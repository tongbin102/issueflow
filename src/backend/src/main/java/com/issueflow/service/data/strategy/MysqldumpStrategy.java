package com.issueflow.service.data.strategy;

import com.issueflow.common.BizException;
import com.issueflow.common.ResultCode;
import com.issueflow.config.DataManagementProperties;
import com.issueflow.entity.BackupRecord;
import com.issueflow.enums.TaskPhaseEnum;
import com.issueflow.service.data.DbConnectionResolver;
import com.issueflow.service.data.DbConnectionResolver.DbConnectionInfo;
import com.issueflow.service.data.MysqlCliSupport;
import com.issueflow.service.data.TaskProgressStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于 mysqldump 的数据库导出策略（Phase10 数据管理）。
 *
 * <p>首选实现。相比 JDBC 逐表导出，mysqldump 的优势是：</p>
 * <ul>
 *   <li>{@code --single-transaction} 在 InnoDB 上取一致性快照，<b>不锁表</b>，业务无感；</li>
 *   <li>自带存储过程 / 触发器 / 事件的导出，JDBC 手写很难覆盖全；</li>
 *   <li>输出格式与 mysql 客户端天然匹配，恢复零转换。</li>
 * </ul>
 *
 * <p><b>安全红线</b>：密码只经 {@code --defaults-extra-file}（0600 临时文件）传递，
 * 命令行参数里绝不出现密码；stderr 经脱敏后才允许进入日志与错误消息。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MysqldumpStrategy implements DumpStrategy {

    private final DataManagementProperties properties;
    private final DbConnectionResolver connectionResolver;
    private final MysqlCliSupport cliSupport;
    private final TaskProgressStore progressStore;

    /** 读写缓冲区大小 64KB */
    private static final int BUFFER_SIZE = 64 * 1024;

    /** 进度上报间隔（字节），每 8MB 刷一次，避免高频写 Redis */
    private static final long PROGRESS_STEP_BYTES = 8L * 1024 * 1024;

    /** DUMP_DB 阶段的进度起点（沿用 LOCK 阶段锚点） */
    private static final int PROGRESS_START = TaskPhaseEnum.LOCK.getWeight();

    /** DUMP_DB 阶段的进度终点锚点 */
    private static final int PROGRESS_END = TaskPhaseEnum.DUMP_DB.getWeight();

    /** 进度渐进曲线的半衰规模（MB）：写到约 50MB 时进度走完一半区间 */
    private static final double CURVE_SCALE_MB = 50.0D;

    @Override
    public boolean isAvailable() {
        return cliSupport.isExecutableAvailable(properties.getMysqldumpPath());
    }

    @Override
    public String strategyName() {
        return "MYSQLDUMP";
    }

    @Override
    public long dump(BackupRecord record, OutputStream out) throws Exception {
        cliSupport.requireExecutable(properties.getMysqldumpPath());

        DbConnectionInfo info = connectionResolver.resolve();
        record.setDbName(info.getDatabase());

        Path credFile = null;
        Process process = null;
        try {
            credFile = cliSupport.writeCredentialsFile(info);
            List<String> command = buildCommand(credFile, info);
            log.info("[MysqldumpStrategy] 开始导出 db={} taskId={}",
                    info.getDatabase(), record.getTaskId());

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(false);
            process = builder.start();

            // stderr 必须并发读走，否则管道写满会把子进程卡死
            AtomicReference<String> stderrHolder = new AtomicReference<>("");
            Process finalProcess = process;
            Thread stderrReader = new Thread(
                    () -> stderrHolder.set(cliSupport.drainStderr(finalProcess.getErrorStream())),
                    "dm-mysqldump-stderr");
            stderrReader.setDaemon(true);
            stderrReader.start();

            long written = pipe(process.getInputStream(), out, record.getTaskId());

            int timeoutSeconds = properties.getTaskTimeoutSeconds() > 0
                    ? properties.getTaskTimeoutSeconds() : 1800;
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.error("[MysqldumpStrategy] 导出超时 {}s，已强制终止 taskId={}",
                        timeoutSeconds, record.getTaskId());
                throw new BizException(ResultCode.BACKUP_EXECUTE_FAILED, "备份超时，请缩小备份范围或联系运维");
            }

            stderrReader.join(TimeUnit.SECONDS.toMillis(5));
            int exitCode = process.exitValue();
            String stderr = stderrHolder.get();

            if (exitCode != 0) {
                log.error("[MysqldumpStrategy] 导出失败 exitCode={} stderr={}", exitCode, stderr);
                throw new BizException(ResultCode.BACKUP_EXECUTE_FAILED,
                        "数据库导出失败" + (stderr.isEmpty() ? "" : "：" + brief(stderr)));
            }
            if (written <= 0) {
                log.error("[MysqldumpStrategy] 导出结果为空 taskId={}", record.getTaskId());
                throw new BizException(ResultCode.BACKUP_EXECUTE_FAILED, "数据库导出结果为空");
            }

            record.setTableCount(0);
            log.info("[MysqldumpStrategy] 导出完成 taskId={} bytes={}", record.getTaskId(), written);
            return written;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            cliSupport.deleteQuietly(credFile);
        }
    }

    /**
     * 组装 mysqldump 命令行。
     *
     * <p>{@code --defaults-extra-file} 必须是<b>第一个</b>参数，这是 MySQL 客户端的硬性要求。</p>
     *
     * @param credFile 凭据临时文件
     * @param info     连接信息
     * @return 命令片段列表
     */
    private List<String> buildCommand(Path credFile, DbConnectionInfo info) {
        List<String> command = new ArrayList<>(20);
        command.add(properties.getMysqldumpPath());
        command.add("--defaults-extra-file=" + credFile.toAbsolutePath());
        command.add("--single-transaction");
        command.add("--quick");
        command.add("--routines");
        command.add("--triggers");
        command.add("--events");
        command.add("--hex-blob");
        command.add("--default-character-set=utf8mb4");
        // GTID 信息会让备份包只能恢复到同一 GTID 拓扑，跨环境恢复必须关掉
        command.add("--set-gtid-purged=OFF");
        command.add("--add-drop-table");
        command.add("--complete-insert");
        command.add("--max-allowed-packet=" + properties.getMaxAllowedPacketMb() + "M");

        for (String table : excludeTables()) {
            command.add("--ignore-table=" + info.getDatabase() + "." + table);
        }
        command.add(info.getDatabase());
        return command;
    }

    /**
     * 解析需要排除的表名列表。
     *
     * @return 表名列表，未配置时为空列表
     */
    private List<String> excludeTables() {
        List<String> tables = new ArrayList<>(4);
        String raw = properties.getExcludeTables();
        if (raw == null || raw.trim().isEmpty()) {
            return tables;
        }
        for (String item : raw.split(",")) {
            String name = item.trim();
            // 只允许合法标识符，杜绝参数注入
            if (!name.isEmpty() && name.matches("[A-Za-z0-9_]+")) {
                tables.add(name);
            }
        }
        return tables;
    }

    /**
     * 把子进程 stdout 拷贝到目标流，并按字节量渐进上报进度。
     *
     * @param in     子进程标准输出
     * @param out    目标输出流（不关闭）
     * @param taskId 任务号
     * @return 拷贝的字节数
     * @throws IOException 读写失败
     */
    private long pipe(InputStream in, OutputStream out, String taskId) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long total = 0L;
        long nextReport = PROGRESS_STEP_BYTES;
        int read = in.read(buffer);
        while (read != -1) {
            out.write(buffer, 0, read);
            total += read;
            if (total >= nextReport) {
                progressStore.updateProgress(taskId, curveProgress(total),
                        "已导出 " + toMb(total) + " MB");
                nextReport = total + PROGRESS_STEP_BYTES;
            }
            read = in.read(buffer);
        }
        out.flush();
        return total;
    }

    /**
     * 把已写字节数映射为 [PROGRESS_START, PROGRESS_END) 区间内的百分比。
     *
     * <p>总量事先未知，因此用指数衰减曲线渐进逼近终点：写得越多越接近 45%，
     * 但永远不会提前触达，避免进度条卡在 100% 却还在跑的糟糕体验。</p>
     *
     * @param bytes 已写字节数
     * @return 百分比
     */
    private int curveProgress(long bytes) {
        double mb = bytes / (1024.0D * 1024.0D);
        double ratio = 1.0D - Math.exp(-mb / CURVE_SCALE_MB);
        int span = PROGRESS_END - PROGRESS_START - 1;
        return PROGRESS_START + (int) Math.round(span * ratio);
    }

    /**
     * 字节数转 MB（保留一位小数）。
     *
     * @param bytes 字节数
     * @return MB 文本
     */
    private String toMb(long bytes) {
        return String.format("%.1f", bytes / (1024.0D * 1024.0D));
    }

    /**
     * 截断过长的错误文本，避免整段 stderr 灌进前端提示。
     *
     * @param text 脱敏后的错误文本
     * @return 最多 200 字符的摘要
     */
    private String brief(String text) {
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= 200 ? oneLine : oneLine.substring(0, 200) + "...";
    }
}
