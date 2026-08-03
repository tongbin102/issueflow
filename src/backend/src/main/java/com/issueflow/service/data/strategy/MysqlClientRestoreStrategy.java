package com.issueflow.service.data.strategy;

import com.issueflow.common.BizException;
import com.issueflow.common.ResultCode;
import com.issueflow.config.DataManagementProperties;
import com.issueflow.entity.RestoreRecord;
import com.issueflow.service.data.DbConnectionResolver;
import com.issueflow.service.data.DbConnectionResolver.DbConnectionInfo;
import com.issueflow.service.data.MysqlCliSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于 mysql 客户端的恢复策略（Phase10 数据管理）。
 *
 * <p>把解包出来的 SQL 文件通过 {@code mysql} 客户端重放回当前库。</p>
 *
 * <p><b>为什么必须做连接池换血</b>：备份 SQL 里含
 * {@code DROP TABLE} / {@code CREATE TABLE}，导入后表的 metadata version 会变。
 * HikariCP 池中的旧连接仍缓存着旧的预编译语句句柄与会话临时表，
 * 继续复用会报 {@code Table definition has changed}，甚至读到不一致的中间态。
 * 因此导入成功后立刻 {@code softEvictConnections()}：
 * 空闲连接立即销毁，在用连接归还时销毁，业务无感知地换上全新连接。</p>
 *
 * <p><b>调用前置条件</b>：系统必须已由上层置为只读期，否则并发写会与导入互相踩踏。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MysqlClientRestoreStrategy implements RestoreStrategy {

    private final DataManagementProperties properties;
    private final DbConnectionResolver connectionResolver;
    private final MysqlCliSupport cliSupport;
    private final DataSource dataSource;

    @Override
    public boolean isAvailable() {
        return cliSupport.isExecutableAvailable(properties.getMysqlClientPath());
    }

    @Override
    public String strategyName() {
        return "MYSQL_CLIENT";
    }

    @Override
    public void restore(Path sqlFile, RestoreRecord record) throws Exception {
        cliSupport.requireExecutable(properties.getMysqlClientPath());

        if (sqlFile == null || !Files.exists(sqlFile) || !Files.isRegularFile(sqlFile)) {
            log.error("[MysqlClientRestoreStrategy] SQL 文件不存在 taskId={}", record.getTaskId());
            throw new BizException(ResultCode.BACKUP_PACKAGE_INVALID, "备份包内未找到数据库文件");
        }
        if (Files.size(sqlFile) <= 0L) {
            throw new BizException(ResultCode.BACKUP_PACKAGE_INVALID, "备份包内数据库文件为空");
        }

        DbConnectionInfo info = connectionResolver.resolve();
        Path credFile = null;
        Process process = null;
        try {
            credFile = cliSupport.writeCredentialsFile(info);
            List<String> command = buildCommand(credFile, info);
            log.warn("[MysqlClientRestoreStrategy] 开始导入 db={} taskId={} —— 破坏性操作",
                    info.getDatabase(), record.getTaskId());

            ProcessBuilder builder = new ProcessBuilder(command);
            // SQL 文件直接作为子进程 stdin，避免整文件读进 JVM 堆
            builder.redirectInput(sqlFile.toFile());
            builder.redirectErrorStream(false);
            process = builder.start();

            AtomicReference<String> stderrHolder = new AtomicReference<>("");
            Process finalProcess = process;
            Thread stderrReader = new Thread(
                    () -> stderrHolder.set(cliSupport.drainStderr(finalProcess.getErrorStream())),
                    "dm-mysql-restore-stderr");
            stderrReader.setDaemon(true);
            stderrReader.start();

            // stdout 也要读走，mysql 客户端在非交互模式下输出很少但不能不读
            drainQuietly(process);

            int timeoutSeconds = properties.getTaskTimeoutSeconds() > 0
                    ? properties.getTaskTimeoutSeconds() : 1800;
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.error("[MysqlClientRestoreStrategy] 导入超时 {}s taskId={}",
                        timeoutSeconds, record.getTaskId());
                throw new BizException(ResultCode.RESTORE_EXECUTE_FAILED, "数据恢复超时");
            }

            stderrReader.join(TimeUnit.SECONDS.toMillis(5));
            int exitCode = process.exitValue();
            String stderr = stderrHolder.get();
            if (exitCode != 0) {
                log.error("[MysqlClientRestoreStrategy] 导入失败 exitCode={} stderr={}", exitCode, stderr);
                throw new BizException(ResultCode.RESTORE_EXECUTE_FAILED,
                        "数据恢复失败" + (stderr.isEmpty() ? "" : "：" + brief(stderr)));
            }

            log.info("[MysqlClientRestoreStrategy] 导入完成 taskId={}", record.getTaskId());
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            cliSupport.deleteQuietly(credFile);
            // 无论成败都换血：失败时库可能已被改动，旧连接同样不可信
            evictConnections();
            record.setAffectedTables(countTablesQuietly());
        }
    }

    /**
     * 组装 mysql 客户端命令行。
     *
     * @param credFile 凭据临时文件
     * @param info     连接信息
     * @return 命令片段列表
     */
    private List<String> buildCommand(Path credFile, DbConnectionInfo info) {
        List<String> command = new ArrayList<>(10);
        command.add(properties.getMysqlClientPath());
        command.add("--defaults-extra-file=" + credFile.toAbsolutePath());
        command.add("--default-character-set=utf8mb4");
        command.add("--max-allowed-packet=" + properties.getMaxAllowedPacketMb() + "M");
        // 任何一条语句失败即整体中止，绝不允许「导了一半」这种薛定谔状态
        command.add("--batch");
        command.add("--force=FALSE");
        command.add(info.getDatabase());
        return command;
    }

    /**
     * 读走子进程 stdout，防止管道写满导致死锁。
     *
     * @param process 子进程
     */
    private void drainQuietly(Process process) {
        Thread thread = new Thread(() -> {
            try (java.io.InputStream in = process.getInputStream()) {
                byte[] buffer = new byte[8192];
                while (in.read(buffer) != -1) {
                    // 丢弃即可，mysql --batch 的 stdout 无需保留
                }
            } catch (IOException e) {
                log.debug("[MysqlClientRestoreStrategy] stdout 读取中断: {}",
                        e.getClass().getSimpleName());
            }
        }, "dm-mysql-restore-stdout");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * 触发 HikariCP 连接池软换血。
     *
     * <p>用反射调用是为了不在编译期硬绑定 HikariCP —— 连接池被替换时代码仍可编译，
     * 只是退化为无操作并留下一条 warn。</p>
     */
    private void evictConnections() {
        try {
            Method method = dataSource.getClass().getMethod("softEvictConnections");
            method.invoke(dataSource);
            log.info("[MysqlClientRestoreStrategy] 连接池已软换血，旧连接将被逐出");
        } catch (NoSuchMethodException e) {
            log.warn("[MysqlClientRestoreStrategy] 当前连接池不支持 softEvictConnections，"
                    + "恢复后建议重启应用以避免陈旧连接");
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.warn("[MysqlClientRestoreStrategy] 连接池换血失败: {}", e.getClass().getSimpleName());
        }
    }

    /**
     * 统计恢复后的表数量（失败不影响主流程）。
     *
     * @return 表数量；统计失败返回 0
     */
    private int countTablesQuietly() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.TABLES "
                             + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'")) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            log.debug("[MysqlClientRestoreStrategy] 表数量统计失败: {}", e.getClass().getSimpleName());
        }
        return 0;
    }

    /**
     * 截断过长的错误文本。
     *
     * @param text 脱敏后的错误文本
     * @return 最多 200 字符的摘要
     */
    private String brief(String text) {
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= 200 ? oneLine : oneLine.substring(0, 200) + "...";
    }
}
