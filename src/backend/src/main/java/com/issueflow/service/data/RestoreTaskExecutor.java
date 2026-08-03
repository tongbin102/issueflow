package com.issueflow.service.data;

import com.issueflow.common.BizException;
import com.issueflow.common.Constants;
import com.issueflow.common.ResultCode;
import com.issueflow.config.DataTaskAsyncConfig;
import com.issueflow.entity.BackupRecord;
import com.issueflow.entity.RestoreRecord;
import com.issueflow.enums.BackupSourceEnum;
import com.issueflow.enums.BackupTypeEnum;
import com.issueflow.enums.TaskPhaseEnum;
import com.issueflow.enums.TaskStatusEnum;
import com.issueflow.mapper.BackupRecordMapper;
import com.issueflow.mapper.RestoreRecordMapper;
import com.issueflow.service.data.strategy.MysqlClientRestoreStrategy;
import com.issueflow.util.MaskUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 恢复任务执行器（Phase10 数据管理）。
 *
 * <p>流水线：</p>
 * <pre>
 * LOCK → VALIDATE → PRE_BACKUP → UNPACK → IMPORT_DB → REFRESH_CACHE → DONE
 * </pre>
 *
 * <p><b>这是全系统风险最高的一段代码</b>，四条硬风险约束不可省略：</p>
 * <ol>
 *   <li><b>先校验后动手</b>：checksum 与包结构校验必须在 PRE_BACKUP 之前完成。
 *       拿一个损坏的包去恢复，等于用坏数据覆盖好数据。</li>
 *   <li><b>只读窗口</b>：校验通过即刻置 {@code dm:readonly}，
 *       让 {@code ReadOnlyGuardInterceptor} 挡住所有业务写请求，
 *       否则导入过程中的并发写会写进一个即将被 DROP 的表。</li>
 *   <li><b>恢复前安全备份同步做完</b>：不能异步，必须阻塞到安全备份落盘成功，
 *       否则「安全备份没写完，库先被删了」就彻底没救。</li>
 *   <li><b>无论成败都解除只读并换血</b>：只读标记漏关会让整站变成只读的活死人状态，
 *       比恢复失败本身更严重。因此解除动作放在 finally。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestoreTaskExecutor {

    private final RestoreRecordMapper restoreRecordMapper;
    private final BackupRecordMapper backupRecordMapper;
    private final BackupArchiveService archiveService;
    private final BackupTaskExecutor backupTaskExecutor;
    private final DataManagementConfigService configService;
    private final MysqlClientRestoreStrategy restoreStrategy;
    private final TaskProgressStore progressStore;
    private final DataTaskLock dataTaskLock;
    private final RedisTemplate<String, Object> redisTemplate;

    /** 解包临时目录前缀 */
    private static final String UNPACK_DIR_PREFIX = "dm-restore-";

    /** 恢复期间需要清空的业务缓存前缀（dm:* 全部排除，那是任务自身的状态） */
    private static final String[] BUSINESS_CACHE_PREFIXES = {
            Constants.REDIS_PERM_ROLE_PREFIX,
            Constants.REDIS_DICT_PREFIX,
            Constants.REDIS_FIELD_SCHEMA_PREFIX
    };

    /**
     * 异步执行恢复任务（对外入口）。
     *
     * @param recordId 恢复记录 id
     * @param preBackup 是否在恢复前生成安全备份
     */
    @Async(DataTaskAsyncConfig.DATA_TASK_EXECUTOR)
    public void runAsync(Long recordId, boolean preBackup) {
        RestoreRecord record = restoreRecordMapper.selectById(recordId);
        if (record == null) {
            log.error("[RestoreTaskExecutor] 恢复记录不存在，任务终止 recordId={}", recordId);
            return;
        }
        String taskId = record.getTaskId();
        try {
            execute(record, preBackup);
            progressStore.success(taskId, record.getId(), record.getBackupFileName());
        } catch (Exception e) {
            String reason = describe(e);
            log.error("[RestoreTaskExecutor] 恢复失败 taskId={} reason={}", taskId, reason, e);
            markFailed(record, reason);
            progressStore.fail(taskId, reason);
        } finally {
            // 双保险：解除只读 + 释放锁，漏掉任意一个都会让系统卡死
            dataTaskLock.disableReadOnly();
            dataTaskLock.unlock(taskId);
        }
    }

    /**
     * 执行恢复核心流程。
     *
     * @param record    恢复记录
     * @param preBackup 是否恢复前自动安全备份
     * @throws Exception 恢复失败
     */
    private void execute(RestoreRecord record, boolean preBackup) throws Exception {
        String taskId = record.getTaskId();
        long startNanos = System.nanoTime();

        record.setStatus(TaskStatusEnum.RUNNING.getCode());
        record.setPhase(TaskPhaseEnum.LOCK.getCode());
        record.setProgress(TaskPhaseEnum.LOCK.getWeight());
        record.setStartedAt(LocalDateTime.now());
        restoreRecordMapper.updateById(record);
        progressStore.advance(taskId, TaskPhaseEnum.LOCK, "准备恢复环境");

        BackupRecord backup = backupRecordMapper.selectById(record.getBackupId());
        if (backup == null) {
            throw new BizException(ResultCode.BACKUP_NOT_FOUND);
        }
        Path backupFile = configService.getBackupRoot().resolve(backup.getFilePath()).normalize();
        if (!Files.exists(backupFile) || !Files.isRegularFile(backupFile)) {
            log.error("[RestoreTaskExecutor] 备份文件缺失 backupId={}", backup.getId());
            throw new BizException(ResultCode.BACKUP_FILE_MISSING);
        }

        // ---------- 1. VALIDATE：先校验，绝不先动库 ----------
        advance(record, TaskPhaseEnum.VALIDATE, "正在校验备份包完整性");
        Map<String, Object> manifest = archiveService.validateAndReadManifest(backupFile);
        verifyChecksum(backup, backupFile);
        BackupTypeEnum backupType = resolveBackupType(backup, manifest);
        if (!backupType.includeDb()) {
            log.error("[RestoreTaskExecutor] 备份包不含数据库，无法恢复 backupId={}", backup.getId());
            throw new BizException(ResultCode.BACKUP_PACKAGE_INVALID,
                    "该备份仅包含配置文件，不支持数据恢复");
        }

        // ---------- 2. 进入只读窗口 ----------
        int readOnlyTtl = configService.getTaskTimeoutSeconds(1800);
        dataTaskLock.enableReadOnly(taskId, readOnlyTtl);

        Path workDir = null;
        try {
            // ---------- 3. PRE_BACKUP：同步做完安全备份 ----------
            if (preBackup) {
                advance(record, TaskPhaseEnum.PRE_BACKUP, "正在生成恢复前安全备份");
                Long preBackupId = createSafetyBackup(record);
                record.setPreBackupId(preBackupId);
                restoreRecordMapper.updateById(record);
                log.warn("[RestoreTaskExecutor] 安全备份已生成 preBackupId={} taskId={}",
                        preBackupId, taskId);
            } else {
                log.warn("[RestoreTaskExecutor] 管理员显式跳过恢复前安全备份，taskId={}", taskId);
            }

            // ---------- 4. UNPACK ----------
            advance(record, TaskPhaseEnum.UNPACK, "正在解包备份文件");
            workDir = Files.createTempDirectory(UNPACK_DIR_PREFIX);
            Map<String, Path> extracted = archiveService.unpack(backupFile, workDir);
            Path sqlFile = extracted.get(BackupArchiveService.DB_ENTRY);
            if (sqlFile == null) {
                throw new BizException(ResultCode.BACKUP_PACKAGE_INVALID, "备份包内未找到数据库文件");
            }

            // ---------- 5. IMPORT_DB ----------
            advance(record, TaskPhaseEnum.IMPORT_DB, "正在导入数据库，请勿关闭页面");
            restoreStrategy.restore(sqlFile, record);

            // ---------- 6. REFRESH_CACHE ----------
            advance(record, TaskPhaseEnum.REFRESH_CACHE, "正在刷新缓存");
            clearBusinessCache();

            record.setStatus(TaskStatusEnum.SUCCESS.getCode());
            record.setPhase(TaskPhaseEnum.DONE.getCode());
            record.setProgress(100);
            record.setErrorMsg("");
            record.setFinishedAt(LocalDateTime.now());
            record.setDurationMs((System.nanoTime() - startNanos) / 1_000_000L);
            restoreRecordMapper.updateById(record);
            log.warn("[RestoreTaskExecutor] 恢复成功 taskId={} backupId={} affectedTables={}",
                    taskId, backup.getId(), record.getAffectedTables());
        } finally {
            deleteDirQuietly(workDir);
        }
    }

    /**
     * 校验备份文件 SHA-256。
     *
     * <p>历史记录里 checksum 为空（例如极早期版本写入的数据）时跳过校验并记 warn，
     * 但绝不因为「算不出来」就放行 —— 算得出但不匹配一律拒绝。</p>
     *
     * @param backup     备份记录
     * @param backupFile 备份文件
     * @throws IOException 读取失败
     */
    private void verifyChecksum(BackupRecord backup, Path backupFile) throws IOException {
        String expected = backup.getChecksum();
        if (expected == null || expected.trim().isEmpty()) {
            log.warn("[RestoreTaskExecutor] 备份记录无校验和，跳过完整性比对 backupId={}", backup.getId());
            return;
        }
        String actual = archiveService.sha256(backupFile);
        if (!expected.equalsIgnoreCase(actual)) {
            log.error("[RestoreTaskExecutor] 校验和不匹配 backupId={} expected={} actual={}",
                    backup.getId(), expected, actual);
            throw new BizException(ResultCode.BACKUP_CHECKSUM_MISMATCH);
        }
    }

    /**
     * 解析备份类型：优先用记录字段，缺失时回落 manifest。
     *
     * @param backup   备份记录
     * @param manifest manifest 内容
     * @return 备份类型，均无法解析时按 FULL 处理
     */
    private BackupTypeEnum resolveBackupType(BackupRecord backup, Map<String, Object> manifest) {
        BackupTypeEnum type = BackupTypeEnum.of(backup.getBackupType());
        if (type != null) {
            return type;
        }
        Object fromManifest = manifest.get("backupType");
        type = BackupTypeEnum.of(fromManifest == null ? null : String.valueOf(fromManifest));
        return type == null ? BackupTypeEnum.FULL : type;
    }

    /**
     * 生成恢复前安全备份（同步阻塞直至落盘成功）。
     *
     * <p>安全备份的 {@code taskId} 复用恢复任务的 taskId ——
     * 这样 dump 过程中的细粒度进度会写进同一个 Redis 进度对象，
     * 前端进度条不会出现「卡住不动」的假死感。两张表各自唯一，不冲突。</p>
     *
     * @param record 恢复记录
     * @return 安全备份的 backup_record.id
     * @throws Exception 安全备份失败（此时必须中止恢复）
     */
    private Long createSafetyBackup(RestoreRecord record) throws Exception {
        BackupRecord safety = new BackupRecord();
        safety.setTaskId(record.getTaskId());
        safety.setBackupType(BackupTypeEnum.DB_ONLY.getCode());
        safety.setSource(BackupSourceEnum.PRE_RESTORE.getCode());
        safety.setStatus(TaskStatusEnum.PENDING.getCode());
        safety.setPhase(TaskPhaseEnum.INIT.getCode());
        safety.setProgress(0);
        safety.setFileSize(0L);
        safety.setErrorMsg("");
        safety.setRemark("恢复前自动安全备份（恢复任务 " + record.getTaskId() + "）");
        safety.setOperatorId(record.getOperatorId());
        safety.setOperatorName(record.getOperatorName());
        backupRecordMapper.insert(safety);

        try {
            backupTaskExecutor.execute(safety);
        } catch (Exception e) {
            log.error("[RestoreTaskExecutor] 恢复前安全备份失败，已中止恢复 taskId={}",
                    record.getTaskId(), e);
            throw new BizException(ResultCode.RESTORE_EXECUTE_FAILED,
                    "恢复前安全备份失败，为保护现有数据已中止恢复");
        }
        return safety.getId();
    }

    /**
     * 清空业务缓存（保留 dm:* 任务状态）。
     *
     * <p>库已经被整体替换，Redis 里的角色权限 / 字典 / 字段 schema 缓存全部失效，
     * 不清会出现「页面显示的还是旧数据」这类灵异现象。</p>
     */
    private void clearBusinessCache() {
        int total = 0;
        for (String prefix : BUSINESS_CACHE_PREFIXES) {
            try {
                Set<String> keys = redisTemplate.keys(prefix + "*");
                if (keys == null || keys.isEmpty()) {
                    continue;
                }
                // 双保险：绝不误删数据管理自身的任务状态
                Set<String> safeKeys = new HashSet<>(keys.size());
                for (String key : keys) {
                    if (!key.startsWith("dm:")) {
                        safeKeys.add(key);
                    }
                }
                if (!safeKeys.isEmpty()) {
                    Long removed = redisTemplate.delete(safeKeys);
                    total += removed == null ? 0 : removed.intValue();
                }
            } catch (Exception e) {
                log.warn("[RestoreTaskExecutor] 缓存清理失败 prefix={} err={}",
                        prefix, e.getClass().getSimpleName());
            }
        }
        log.info("[RestoreTaskExecutor] 业务缓存已清理，共 {} 个 key", total);
    }

    /**
     * 推进阶段并同步落库。
     *
     * @param record  恢复记录
     * @param phase   目标阶段
     * @param message 提示文案
     */
    private void advance(RestoreRecord record, TaskPhaseEnum phase, String message) {
        record.setPhase(phase.getCode());
        record.setProgress(phase.getWeight());
        restoreRecordMapper.updateById(record);
        progressStore.advance(record.getTaskId(), phase, message);
    }

    /**
     * 将恢复记录标记为失败。
     *
     * @param record 恢复记录
     * @param reason 脱敏后的失败原因
     */
    private void markFailed(RestoreRecord record, String reason) {
        try {
            record.setStatus(TaskStatusEnum.FAILED.getCode());
            record.setErrorMsg(reason);
            record.setFinishedAt(LocalDateTime.now());
            if (record.getStartedAt() != null) {
                record.setDurationMs(Duration
                        .between(record.getStartedAt(), record.getFinishedAt()).toMillis());
            }
            restoreRecordMapper.updateById(record);
        } catch (Exception e) {
            log.error("[RestoreTaskExecutor] 失败状态回写异常 taskId={}", record.getTaskId(), e);
        }
    }

    /**
     * 递归删除临时解包目录。
     *
     * @param dir 目录路径，可为 null
     */
    private void deleteDirQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    path.toFile().deleteOnExit();
                }
            });
        } catch (IOException e) {
            log.warn("[RestoreTaskExecutor] 临时解包目录清理失败: {}", e.getClass().getSimpleName());
        }
    }

    /**
     * 生成新的任务号。
     *
     * @return UUID 去横线
     */
    public static String newTaskId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 把异常转成可安全下发前端的文案。
     *
     * @param e 异常
     * @return 脱敏后的描述
     */
    private String describe(Exception e) {
        if (e instanceof BizException) {
            return e.getMessage() == null ? "数据恢复失败" : e.getMessage();
        }
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "数据恢复失败：" + e.getClass().getSimpleName();
        }
        String masked = MaskUtils.maskSensitivePath(message);
        return masked.length() <= 200 ? masked : masked.substring(0, 200) + "...";
    }
}
