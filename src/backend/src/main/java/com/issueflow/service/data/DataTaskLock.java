package com.issueflow.service.data;

import com.issueflow.common.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 备份 / 恢复全局互斥锁（Phase10）。
 *
 * <p>同一时刻整个集群只允许一个数据管理任务在跑 —— 两个 mysqldump 并发会互相拖垮 IO，
 * 备份与恢复并发更会直接毁掉数据一致性。</p>
 *
 * <p>实现要点：</p>
 * <ul>
 *   <li>{@code SET dm:lock:global {taskId} NX EX ttl} 原子加锁；</li>
 *   <li>释放时**先比对持有者**再删，防止 A 的超时锁被 B 误删；</li>
 *   <li>TTL 兜底进程崩溃 —— 锁最长自动存活 {@code lockTtlSeconds}。</li>
 * </ul>
 *
 * <p>另外维护 {@code dm:readonly} 只读标记：恢复期间由
 * {@link com.issueflow.security.ReadOnlyGuardInterceptor} 读取，拦截一切写请求。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataTaskLock {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 尝试加锁。
     *
     * @param taskId     任务号，作为锁持有者标识
     * @param ttlSeconds 锁最长存活秒数，必须大于 0
     * @return true 加锁成功；false 说明已有任务在跑
     */
    public boolean tryLock(String taskId, int ttlSeconds) {
        if (taskId == null || taskId.trim().isEmpty()) {
            return false;
        }
        int ttl = ttlSeconds > 0 ? ttlSeconds : 3600;
        Boolean ok = redisTemplate.opsForValue()
                .setIfAbsent(Constants.REDIS_DM_GLOBAL_LOCK, taskId, ttl, TimeUnit.SECONDS);
        boolean acquired = Boolean.TRUE.equals(ok);
        if (acquired) {
            log.info("[DataTaskLock] 加锁成功 taskId={} ttl={}s", taskId, ttl);
        } else {
            log.info("[DataTaskLock] 加锁失败，已有任务在执行 taskId={}", taskId);
        }
        return acquired;
    }

    /**
     * 释放锁（仅当持有者是自己时才删）。
     *
     * @param taskId 任务号
     */
    public void unlock(String taskId) {
        if (taskId == null || taskId.trim().isEmpty()) {
            return;
        }
        Object holder = redisTemplate.opsForValue().get(Constants.REDIS_DM_GLOBAL_LOCK);
        if (holder != null && taskId.equals(String.valueOf(holder))) {
            redisTemplate.delete(Constants.REDIS_DM_GLOBAL_LOCK);
            log.info("[DataTaskLock] 释放锁 taskId={}", taskId);
        } else if (holder != null) {
            // 说明本任务的锁已超时并被他人重新抢占，不能删别人的锁
            log.warn("[DataTaskLock] 锁持有者已变更，跳过释放 taskId={}", taskId);
        }
    }

    /**
     * 查询当前锁持有者。
     *
     * @return 持有锁的任务号；无锁返回 null
     */
    public String currentHolder() {
        Object holder = redisTemplate.opsForValue().get(Constants.REDIS_DM_GLOBAL_LOCK);
        return holder == null ? null : String.valueOf(holder);
    }

    /**
     * 是否有任务正在执行。
     *
     * @return true 有
     */
    public boolean isLocked() {
        return currentHolder() != null;
    }

    /**
     * 打开系统只读开关（恢复期间使用）。
     *
     * @param taskId     发起恢复的任务号
     * @param ttlSeconds 只读窗口最长秒数，兜底防止忘记关闭
     */
    public void enableReadOnly(String taskId, int ttlSeconds) {
        int ttl = ttlSeconds > 0 ? ttlSeconds : 1800;
        redisTemplate.opsForValue()
                .set(Constants.REDIS_DM_READONLY, taskId, ttl, TimeUnit.SECONDS);
        log.warn("[DataTaskLock] 系统进入只读模式 taskId={} ttl={}s", taskId, ttl);
    }

    /**
     * 关闭系统只读开关。
     */
    public void disableReadOnly() {
        redisTemplate.delete(Constants.REDIS_DM_READONLY);
        log.info("[DataTaskLock] 系统退出只读模式");
    }

    /**
     * 系统当前是否处于只读期。
     *
     * @return true 只读
     */
    public boolean isReadOnly() {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(Constants.REDIS_DM_READONLY));
        } catch (Exception e) {
            // Redis 不可用时不能把整站写操作全拦死，降级为「非只读」
            log.warn("[DataTaskLock] 只读标记读取失败，降级放行: {}", e.getClass().getSimpleName());
            return false;
        }
    }
}
