package com.issueflow.service.data;

import com.issueflow.common.BizException;
import com.issueflow.config.DataManagementProperties;
import com.issueflow.config.DataTaskAsyncConfig;
import com.issueflow.entity.BackupRecord;
import com.issueflow.enums.BackupTypeEnum;
import com.issueflow.enums.TaskPhaseEnum;
import com.issueflow.enums.TaskStatusEnum;
import com.issueflow.mapper.BackupRecordMapper;
import com.issueflow.service.data.strategy.DumpStrategy;
import com.issueflow.service.data.strategy.JdbcExportStrategy;
import com.issueflow.service.data.strategy.MysqldumpStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipOutputStream;

/**
 * 备份任务执行器（Phase10 数据管理）。
 *
 * <p>在 {@code dataTaskExecutor} 线程池中异步跑完整条备份流水线：</p>
 * <pre>
 * LOCK → DUMP_DB → DUMP_CONFIG → PACKAGE → CHECKSUM → PERSIST → DONE
 * </pre>
 *
 * <p><b>失败处理原则</b>：任何环节抛异常都要做三件事 ——
 * ①把半成品 zip 删掉（残留的坏包比没有包更危险，管理员可能拿它去恢复）；
 * ②记录脱敏后的失败原因；③释放全局锁。三件事缺一不可。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BackupTaskExecutor {

    private final BackupRecordMapper backupRecordMapper;
    private final BackupArchiveService archiveService;
    private final ConfigArchiveService configArchiveService;
    private final DataManagementConfigService configService;
    private final DataManagementProperties properties;
    private final RetentionPolicyExecutor retentionPolicyExecutor;
    private final TaskProgressStore progressStore;
    private final DataTaskLock dataTaskLock;
    private final MysqldumpStrategy mysqldumpStrategy;
    private final JdbcExportStrategy jdbcExportStrategy;
    private final Environment environment;

    /** 备份文件时间戳格式 */
    private static final DateTimeFormatter FILE_TS =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /** 按月分目录的格式 */
    private static final DateTimeFormatter MONTH_DIR = DateTimeFormatter.ofPattern("yyyyMM");

    /** manifest 结构版本，将来兼容性判断靠它 */
    private static final int MANIFEST_VERSION = 1;

    /**
     * 异步执行备份任务（对外入口）。
     *
     * <p>方法自身吞掉所有异常 —— 异步线程抛异常没人接，只会打一条无人看的堆栈。
     * 失败信息统一落到 {@code backup_record.error_msg} 与 Redis 进度里。</p>
     *
     * @param recordId 备份记录 id
     */
    @Async(DataTaskAsyncConfig.DATA_TASK_EXECUTOR)
    public void runAsync(Long recordId) {
        BackupRecord record = backupRecordMapper.selectById(recordId);
        if (record == null) {
            log.error("[BackupTaskExecutor] 备份记录不存在，任务终止 recordId={}", recordId);
            return;
        }
        String taskId = record.getTaskId();
        try {
            execute(record);
            progressStore.success(taskId, record.getId(), record.getFileName());
        } catch (Exception e) {
            String reason = describe(e);
            log.error("[BackupTaskExecutor] 备份失败 taskId={} reason={}", taskId, reason, e);
            markFailed(record, reason);
            progressStore.fail(taskId, reason);
        } finally {
            dataTaskLock.unlock(taskId);
        }
    }

    /**
     * 同步执行备份核心流程。
     *
     * <p>供 {@link RestoreTaskExecutor} 在 PRE_BACKUP 阶段复用 ——
     * 恢复前的安全备份必须<b>同步</b>完成，不能异步排队，
     * 否则可能出现「安全备份还没写完，恢复已经把库删了」的灾难。</p>
     *
     * @param record 备份记录（须已落库，含 taskId / backupType）
     * @throws Exception 备份失败
     */
    public void execute(BackupRecord record) throws Exception {
        String taskId = record.getTaskId();
        long startNanos = System.nanoTime();

        BackupTypeEnum type = BackupTypeEnum.of(record.getBackupType());
        if (type == null) {
            type = BackupTypeEnum.FULL;
            record.setBackupType(type.getCode());
        }

        record.setStatus(TaskStatusEnum.RUNNING.getCode());
        record.setPhase(TaskPhaseEnum.LOCK.getCode());
        record.setProgress(TaskPhaseEnum.LOCK.getWeight());
        record.setStartedAt(LocalDateTime.now());
        backupRecordMapper.updateById(record);
        progressStore.advance(taskId, TaskPhaseEnum.LOCK, "准备备份环境");

        Path root = configService.getBackupRoot();
        LocalDateTime now = LocalDateTime.now();
        String monthDir = now.format(MONTH_DIR);
        String fileName = buildFileName(record, type, now);
        // 相对路径入库，绝对路径只在进程内使用（安全红线：绝不下发服务器目录结构）
        String relativePath = monthDir + "/" + fileName;
        Path target = root.resolve(monthDir).resolve(fileName);

        record.setFileName(fileName);
        record.setFilePath(relativePath);
        backupRecordMapper.updateById(record);

        String dumpStrategyName = "";
        boolean packaged = false;
        try {
            Files.createDirectories(target.getParent());
            try (ZipOutputStream zos = archiveService.openZip(target)) {
                if (type.includeDb()) {
                    progressStore.advance(taskId, TaskPhaseEnum.DUMP_DB, "正在导出数据库");
                    record.setPhase(TaskPhaseEnum.DUMP_DB.getCode());
                    backupRecordMapper.updateById(record);

                    DumpStrategy strategy = chooseDumpStrategy();
                    dumpStrategyName = strategy.strategyName();
                    OutputStream entryStream = archiveService.beginEntry(
                            zos, BackupArchiveService.DB_ENTRY);
                    long bytes = strategy.dump(record, entryStream);
                    archiveService.closeEntry(zos);
                    log.info("[BackupTaskExecutor] 数据库导出完成 taskId={} strategy={} bytes={}",
                            taskId, dumpStrategyName, bytes);
                }

                if (type.includeConfig()) {
                    progressStore.advance(taskId, TaskPhaseEnum.DUMP_CONFIG, "正在导出配置（敏感项已脱敏）");
                    record.setPhase(TaskPhaseEnum.DUMP_CONFIG.getCode());
                    backupRecordMapper.updateById(record);

                    Map<String, String> configs = configArchiveService.collectMaskedConfigs();
                    for (Map.Entry<String, String> entry : configs.entrySet()) {
                        archiveService.writeTextEntry(zos,
                                BackupArchiveService.CONFIG_ENTRY_PREFIX + entry.getKey(),
                                entry.getValue());
                    }
                    log.info("[BackupTaskExecutor] 配置导出完成 taskId={} files={}",
                            taskId, configs.size());
                }

                progressStore.advance(taskId, TaskPhaseEnum.PACKAGE, "正在打包归档");
                record.setPhase(TaskPhaseEnum.PACKAGE.getCode());
                backupRecordMapper.updateById(record);
                archiveService.writeManifest(zos, buildManifest(record, type, dumpStrategyName, now));
            }
            packaged = true;

            progressStore.advance(taskId, TaskPhaseEnum.CHECKSUM, "正在校验文件完整性");
            String checksum = archiveService.sha256(target);
            long size = Files.size(target);

            progressStore.advance(taskId, TaskPhaseEnum.PERSIST, "正在登记备份记录");
            record.setChecksum(checksum);
            record.setFileSize(size);
            record.setStatus(TaskStatusEnum.SUCCESS.getCode());
            record.setPhase(TaskPhaseEnum.DONE.getCode());
            record.setProgress(100);
            record.setErrorMsg("");
            record.setFinishedAt(LocalDateTime.now());
            record.setDurationMs((System.nanoTime() - startNanos) / 1_000_000L);
            backupRecordMapper.updateById(record);

            int cleaned = retentionPolicyExecutor.apply();
            if (cleaned > 0) {
                log.info("[BackupTaskExecutor] 保留策略清理了 {} 份过期备份", cleaned);
            }
            log.info("[BackupTaskExecutor] 备份成功 taskId={} file={} size={}B",
                    taskId, fileName, size);
        } catch (Exception e) {
            // 半成品包必须删干净，避免管理员误用损坏的备份去恢复
            if (!packaged || Files.exists(target)) {
                deleteQuietly(target);
            }
            throw e;
        }
    }

    /**
     * 选择数据库导出策略：优先 mysqldump，缺失时回落纯 JDBC。
     *
     * @return 可用的导出策略
     * @throws BizException 两种策略都不可用
     */
    private DumpStrategy chooseDumpStrategy() {
        if (mysqldumpStrategy.isAvailable()) {
            return mysqldumpStrategy;
        }
        log.warn("[BackupTaskExecutor] mysqldump 不可用，回落 JDBC 导出策略"
                + "（不含存储过程 / 触发器，且非全库一致性快照）");
        if (jdbcExportStrategy.isAvailable()) {
            return jdbcExportStrategy;
        }
        throw new BizException(com.issueflow.common.ResultCode.DATA_TOOL_UNAVAILABLE);
    }

    /**
     * 构造备份文件名。
     *
     * @param record 备份记录
     * @param type   备份类型
     * @param now    时间
     * @return 形如 {@code issueflow_backup_20260803_101530_FULL.zip}
     */
    private String buildFileName(BackupRecord record, BackupTypeEnum type, LocalDateTime now) {
        String prefix = properties.getFileNamePrefix();
        if (prefix == null || prefix.trim().isEmpty()) {
            prefix = "issueflow_backup";
        }
        String suffix = record.getTaskId() == null || record.getTaskId().length() < 6
                ? "" : "_" + record.getTaskId().substring(0, 6);
        return prefix + "_" + now.format(FILE_TS) + "_" + type.getCode() + suffix + ".zip";
    }

    /**
     * 构造 manifest 元信息。
     *
     * @param record       备份记录
     * @param type         备份类型
     * @param strategyName 使用的导出策略
     * @param now          时间
     * @return 有序的 manifest 键值
     */
    private Map<String, Object> buildManifest(BackupRecord record, BackupTypeEnum type,
                                              String strategyName, LocalDateTime now) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("manifestVersion", MANIFEST_VERSION);
        manifest.put("product", "issueFlow");
        manifest.put("appVersion", appVersion());
        manifest.put("backupType", type.getCode());
        manifest.put("source", record.getSource() == null ? "" : record.getSource());
        manifest.put("dbName", record.getDbName() == null ? "" : record.getDbName());
        manifest.put("tableCount", record.getTableCount() == null ? 0 : record.getTableCount());
        manifest.put("dumpStrategy", strategyName);
        manifest.put("includeDb", type.includeDb());
        manifest.put("includeConfig", type.includeConfig());
        manifest.put("createdAt", now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        manifest.put("operatorName", record.getOperatorName() == null ? "" : record.getOperatorName());
        manifest.put("remark", record.getRemark() == null ? "" : record.getRemark());
        manifest.put("dbEntry", type.includeDb() ? BackupArchiveService.DB_ENTRY : "");
        manifest.put("configEntryPrefix",
                type.includeConfig() ? BackupArchiveService.CONFIG_ENTRY_PREFIX : "");
        // 配置项在包内已脱敏，恢复时不可用于回写真实密钥，这里显式标注避免误解
        manifest.put("configMasked", Boolean.TRUE);
        record.setAppVersion(appVersion());
        return manifest;
    }

    /**
     * 读取应用版本号。
     *
     * @return 版本号，未配置时返回 {@code unknown}
     */
    private String appVersion() {
        String version = environment.getProperty("issueflow.app.version");
        if (version == null || version.trim().isEmpty()) {
            version = environment.getProperty("spring.application.version", "unknown");
        }
        return version;
    }

    /**
     * 将备份记录标记为失败。
     *
     * @param record 备份记录
     * @param reason 脱敏后的失败原因
     */
    private void markFailed(BackupRecord record, String reason) {
        try {
            record.setStatus(TaskStatusEnum.FAILED.getCode());
            record.setErrorMsg(reason);
            record.setFinishedAt(LocalDateTime.now());
            if (record.getStartedAt() != null) {
                record.setDurationMs(java.time.Duration
                        .between(record.getStartedAt(), record.getFinishedAt()).toMillis());
            }
            backupRecordMapper.updateById(record);
        } catch (Exception e) {
            log.error("[BackupTaskExecutor] 失败状态回写异常 taskId={}", record.getTaskId(), e);
        }
    }

    /**
     * 静默删除文件。
     *
     * @param path 文件路径
     */
    private void deleteQuietly(Path path) {
        try {
            boolean deleted = Files.deleteIfExists(path);
            if (deleted) {
                log.info("[BackupTaskExecutor] 已清理失败产生的半成品备份文件");
            }
        } catch (IOException e) {
            log.warn("[BackupTaskExecutor] 半成品备份文件清理失败: {}", e.getClass().getSimpleName());
        }
    }

    /**
     * 把异常转成可以安全下发给前端的文案。
     *
     * @param e 异常
     * @return 脱敏后的描述
     */
    private String describe(Exception e) {
        if (e instanceof BizException) {
            return e.getMessage() == null ? "备份执行失败" : e.getMessage();
        }
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "备份执行失败：" + e.getClass().getSimpleName();
        }
        String masked = com.issueflow.util.MaskUtils.maskSensitivePath(message);
        return masked.length() <= 200 ? masked : masked.substring(0, 200) + "...";
    }
}
