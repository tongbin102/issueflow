package com.issueflow.service;

import com.issueflow.dto.resp.RedisInfoVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Properties;

/**
 * Redis 监控服务（Phase 7 T7）。
 *
 * <p><b>只读铁律</b>（ARCH §7.9）：本类只执行 {@code INFO} 与 {@code DBSIZE}，
 * <b>不得</b>出现 {@code DEL} / {@code FLUSHDB} / {@code CONFIG SET} 等任何写命令 ——
 * 一个监控页没有任何理由拥有改动生产数据的能力。</p>
 *
 * <p><b>失败降级</b>：Redis 不可用时返回 {@code available=false} + 归一化错误文案，
 * HTTP 状态仍是 200。监控页面本身就是用来看「挂了没有」的，
 * 让它在 Redis 挂掉时抛 500 白屏是设计错误。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisMonitorService {

    private final RedisConnectionFactory redisConnectionFactory;
    private final PermissionService permissionService;

    /**
     * 拉取 Redis 运行信息（权限 {@code redis:monitor}）。
     *
     * @return 监控视图对象；异常时 {@code available=false}
     */
    public RedisInfoVO info() {
        permissionService.requirePermission("redis:monitor");
        RedisInfoVO vo = new RedisInfoVO();
        RedisConnection connection = null;
        try {
            connection = redisConnectionFactory.getConnection();
            Properties props = connection.serverCommands().info();
            if (props == null || props.isEmpty()) {
                vo.setAvailable(Boolean.FALSE);
                vo.setErrorMessage("Redis 返回空的 INFO 结果，可能该命令被服务端禁用");
                return vo;
            }
            fillServer(vo, props);
            fillMemory(vo, props);
            fillStats(vo, props);
            fillKeyspace(vo, props);
            try {
                Long dbSize = connection.serverCommands().dbSize();
                vo.setDbSize(dbSize == null ? 0L : dbSize);
            } catch (Exception e) {
                // DBSIZE 可能被单独禁用，不影响其余指标展示
                log.warn("[RedisMonitor] DBSIZE unavailable: {}", e.getMessage());
                vo.setDbSize(0L);
            }
            vo.setAvailable(Boolean.TRUE);
            return vo;
        } catch (Exception e) {
            log.warn("[RedisMonitor] INFO failed: {}", e.getMessage());
            vo.setAvailable(Boolean.FALSE);
            vo.setErrorMessage(normalizeError(e));
            return vo;
        } finally {
            closeQuietly(connection);
        }
    }

    // ============================ 分段解析 ============================

    /**
     * 填充服务器信息段。
     *
     * @param vo    目标 VO
     * @param props INFO 结果
     */
    private void fillServer(RedisInfoVO vo, Properties props) {
        vo.getServer().put("version", value(props, "redis_version"));
        vo.getServer().put("mode", value(props, "redis_mode"));
        vo.getServer().put("os", value(props, "os"));
        vo.getServer().put("arch", value(props, "arch_bits"));
        vo.getServer().put("uptimeDays", value(props, "uptime_in_days"));
        vo.getServer().put("uptimeSeconds", value(props, "uptime_in_seconds"));
        vo.getServer().put("role", value(props, "role"));
        vo.getServer().put("port", value(props, "tcp_port"));
    }

    /**
     * 填充内存信息段。
     *
     * @param vo    目标 VO
     * @param props INFO 结果
     */
    private void fillMemory(RedisInfoVO vo, Properties props) {
        vo.getMemory().put("usedMemory", value(props, "used_memory"));
        vo.getMemory().put("usedMemoryHuman", value(props, "used_memory_human"));
        vo.getMemory().put("usedMemoryRss", value(props, "used_memory_rss"));
        vo.getMemory().put("usedMemoryPeak", value(props, "used_memory_peak"));
        vo.getMemory().put("usedMemoryPeakHuman", value(props, "used_memory_peak_human"));
        vo.getMemory().put("maxMemory", value(props, "maxmemory"));
        vo.getMemory().put("maxMemoryHuman", value(props, "maxmemory_human"));
        vo.getMemory().put("maxMemoryPolicy", value(props, "maxmemory_policy"));
        vo.getMemory().put("fragmentationRatio", value(props, "mem_fragmentation_ratio"));
    }

    /**
     * 填充统计信息段（含命中率计算）。
     *
     * <p>命中率 = hits / (hits + misses)；分母为 0 时输出 {@code -}，
     * 不能输出 0% —— 「没有请求」和「一次都没命中」是完全不同的两件事。</p>
     *
     * @param vo    目标 VO
     * @param props INFO 结果
     */
    private void fillStats(RedisInfoVO vo, Properties props) {
        vo.getStats().put("connectedClients", value(props, "connected_clients"));
        vo.getStats().put("blockedClients", value(props, "blocked_clients"));
        vo.getStats().put("totalConnections", value(props, "total_connections_received"));
        vo.getStats().put("totalCommands", value(props, "total_commands_processed"));
        vo.getStats().put("opsPerSec", value(props, "instantaneous_ops_per_sec"));
        vo.getStats().put("expiredKeys", value(props, "expired_keys"));
        vo.getStats().put("evictedKeys", value(props, "evicted_keys"));

        long hits = longValue(props, "keyspace_hits");
        long misses = longValue(props, "keyspace_misses");
        vo.getStats().put("hits", String.valueOf(hits));
        vo.getStats().put("misses", String.valueOf(misses));
        long total = hits + misses;
        if (total <= 0) {
            vo.getStats().put("hitRate", "-");
        } else {
            BigDecimal rate = BigDecimal.valueOf(hits)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
            vo.getStats().put("hitRate", rate.toPlainString() + "%");
        }
    }

    /**
     * 填充键空间分布段。
     *
     * <p>INFO 中形如 {@code db0=keys=12,expires=3,avg_ttl=0}，需二次拆分。</p>
     *
     * @param vo    目标 VO
     * @param props INFO 结果
     */
    private void fillKeyspace(RedisInfoVO vo, Properties props) {
        for (String name : props.stringPropertyNames()) {
            if (!name.startsWith("db") || name.length() < 3) {
                continue;
            }
            String raw = props.getProperty(name);
            if (raw == null || raw.isBlank()) {
                continue;
            }
            RedisInfoVO.KeyspaceItem item = new RedisInfoVO.KeyspaceItem();
            item.setDb(name);
            for (String segment : raw.split(",")) {
                int eq = segment.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = segment.substring(0, eq).trim();
                String val = segment.substring(eq + 1).trim();
                switch (key) {
                    case "keys" -> item.setKeys(parseLong(val));
                    case "expires" -> item.setExpires(parseLong(val));
                    case "avg_ttl" -> item.setAvgTtl(parseLong(val));
                    default -> {
                        // 其余字段（如 subexpiry）本期不展示
                    }
                }
            }
            vo.getKeyspace().add(item);
        }
        vo.getKeyspace().sort((a, b) -> a.getDb().compareTo(b.getDb()));
    }

    // ============================ 工具 ============================

    /**
     * 把底层异常归一化为面向运维的中文文案。
     *
     * @param e 异常
     * @return 归一化错误文案
     */
    private String normalizeError(Exception e) {
        String raw = e.getMessage() == null ? "" : e.getMessage();
        String lower = raw.toLowerCase();
        if (lower.contains("connection refused") || lower.contains("unable to connect")) {
            return "无法连接 Redis：连接被拒绝，请检查 Redis 是否已启动、地址与端口是否正确";
        }
        if (lower.contains("timed out") || lower.contains("timeout")) {
            return "连接 Redis 超时：请检查网络连通性或适当放宽 spring.data.redis.timeout";
        }
        if (lower.contains("noauth") || lower.contains("wrongpass")
                || lower.contains("invalid password") || lower.contains("authentication")) {
            return "Redis 认证失败：请检查 spring.data.redis.password 配置";
        }
        if (lower.contains("unknown command") || lower.contains("not allowed")
                || lower.contains("has been disabled")) {
            return "Redis 拒绝执行 INFO 命令：该命令可能已被服务端安全策略禁用";
        }
        if (raw.isBlank()) {
            return "Redis 不可用：" + e.getClass().getSimpleName();
        }
        return "Redis 不可用：" + raw;
    }

    private String value(Properties props, String key) {
        String v = props.getProperty(key);
        return v == null ? "-" : v;
    }

    private long longValue(Properties props, String key) {
        return parseLong(props.getProperty(key));
    }

    private long parseLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private void closeQuietly(RedisConnection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (Exception e) {
            log.warn("[RedisMonitor] close connection failed: {}", e.getMessage());
        }
    }
}
