package com.issueflow.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.issueflow.common.BizException;
import com.issueflow.common.PageResult;
import com.issueflow.common.ResultCode;
import com.issueflow.config.DataManagementProperties;
import com.issueflow.dto.data.BackupDetailVO;
import com.issueflow.dto.data.BackupListVO;
import com.issueflow.dto.data.CreateBackupReq;
import com.issueflow.dto.data.DataManagementConfigDTO;
import com.issueflow.dto.data.RestoreReq;
import com.issueflow.dto.data.TaskProgressDTO;
import com.issueflow.dto.data.UploadRestoreReq;
import com.issueflow.entity.BackupRecord;
import com.issueflow.entity.RestoreRecord;
import com.issueflow.entity.User;
import com.issueflow.enums.BackupSourceEnum;
import com.issueflow.enums.BackupTypeEnum;
import com.issueflow.enums.TaskPhaseEnum;
import com.issueflow.enums.TaskStatusEnum;
import com.issueflow.mapper.BackupRecordMapper;
import com.issueflow.mapper.RestoreRecordMapper;
import com.issueflow.mapper.UserMapper;
import com.issueflow.service.DataManagementService;
import com.issueflow.service.data.BackupArchiveService;
import com.issueflow.service.data.BackupTaskExecutor;
import com.issueflow.service.data.DataManagementConfigService;
import com.issueflow.service.data.DataTaskLock;
import com.issueflow.service.data.RestoreTaskExecutor;
import com.issueflow.service.data.RetentionPolicyExecutor;
import com.issueflow.service.data.TaskProgressStore;
import com.issueflow.util.SecurityUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 数据管理服务实现（Phase10）。
 *
 * <p>职责边界：本类只做<b>受理与编排</b> —— 参数校验、权限相关的状态检查、
 * 抢锁、落记录、丢给线程池。真正的耗时活儿全在
 * {@link BackupTaskExecutor} / {@link RestoreTaskExecutor} 里异步跑。</p>
 *
 * <p><b>抢锁顺序很关键</b>：必须「先抢锁，再落记录，最后提交异步任务」。
 * 反过来会出现记录已建但锁没抢到，列表里躺着一条永远 PENDING 的僵尸任务。
 * 任何一步失败都要把锁还回去。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataManagementServiceImpl implements DataManagementService {

    private final BackupRecordMapper backupRecordMapper;
    private final RestoreRecordMapper restoreRecordMapper;
    private final UserMapper userMapper;
    private final DataManagementProperties properties;
    private final DataManagementConfigService configService;
    private final BackupArchiveService archiveService;
    private final BackupTaskExecutor backupTaskExecutor;
    private final RestoreTaskExecutor restoreTaskExecutor;
    private final RetentionPolicyExecutor retentionPolicyExecutor;
    private final TaskProgressStore progressStore;
    private final DataTaskLock dataTaskLock;

    /** 上传包的存放子目录 */
    private static final String UPLOAD_SUB_DIR = "upload";

    /** 按月分目录格式 */
    private static final DateTimeFormatter MONTH_DIR = DateTimeFormatter.ofPattern("yyyyMM");

    /** 上传文件名时间戳 */
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /** 允许的上传扩展名 */
    private static final String ALLOWED_EXTENSION = ".zip";

    /** 下载缓冲区 */
    private static final int DOWNLOAD_BUFFER = 64 * 1024;

    /** 恢复来源：本地备份 */
    private static final String RESTORE_SOURCE_LOCAL = "LOCAL";

    /** 恢复来源：上传文件 */
    private static final String RESTORE_SOURCE_UPLOAD = "UPLOAD";

    // ==================================================================
    // 备份
    // ==================================================================

    @Override
    public TaskProgressDTO createBackup(CreateBackupReq req) {
        requireEnabled();
        CreateBackupReq safeReq = req == null ? new CreateBackupReq() : req;

        String taskId = newTaskId();
        acquireLockOrThrow(taskId);

        BackupRecord record = null;
        try {
            BackupTypeEnum type = BackupTypeEnum.of(safeReq.resolveType());
            if (type == null) {
                type = BackupTypeEnum.FULL;
            }
            record = new BackupRecord();
            record.setTaskId(taskId);
            record.setBackupType(type.getCode());
            record.setSource(BackupSourceEnum.MANUAL.getCode());
            record.setStatus(TaskStatusEnum.PENDING.getCode());
            record.setPhase(TaskPhaseEnum.INIT.getCode());
            record.setProgress(TaskPhaseEnum.INIT.getWeight());
            record.setFileSize(0L);
            record.setTableCount(0);
            record.setErrorMsg("");
            record.setRemark(safeReq.safeName());
            fillOperator(record);
            backupRecordMapper.insert(record);

            TaskProgressDTO progress = progressStore.init(
                    taskId, TaskProgressStore.TYPE_BACKUP, record.getId());
            backupTaskExecutor.runAsync(record.getId());
            log.info("[DataManagement] 备份任务已受理 taskId={} type={} operator={}",
                    taskId, type.getCode(), record.getOperatorName());
            return progress;
        } catch (RuntimeException e) {
            // 受理阶段失败必须还锁，否则整个模块会被一把幽灵锁挡死一小时
            dataTaskLock.unlock(taskId);
            progressStore.remove(taskId);
            if (record != null && record.getId() != null) {
                markAcceptFailed(record, e);
            }
            throw e;
        }
    }

    @Override
    public PageResult<BackupListVO> listBackups(long page, long size, String backupType,
                                                String source, String status, String keyword) {
        requireEnabled();
        long safePage = page <= 0 ? 1L : page;
        long safeSize = size <= 0 ? 10L : Math.min(size, 100L);

        var wrapper = Wrappers.<BackupRecord>lambdaQuery();
        if (isNotBlank(backupType)) {
            wrapper.eq(BackupRecord::getBackupType, backupType.trim().toUpperCase(Locale.ROOT));
        }
        if (isNotBlank(source)) {
            wrapper.eq(BackupRecord::getSource, source.trim().toUpperCase(Locale.ROOT));
        }
        if (isNotBlank(status)) {
            wrapper.eq(BackupRecord::getStatus, status.trim().toUpperCase(Locale.ROOT));
        }
        if (isNotBlank(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(BackupRecord::getFileName, kw)
                    .or().like(BackupRecord::getRemark, kw));
        }
        wrapper.orderByDesc(BackupRecord::getId);

        IPage<BackupRecord> result = backupRecordMapper.selectPage(
                new Page<>(safePage, safeSize), wrapper);

        List<BackupListVO> list = new ArrayList<>(result.getRecords().size());
        for (BackupRecord record : result.getRecords()) {
            list.add(BackupListVO.from(record));
        }
        return PageResult.of(list, result.getTotal(), safePage, safeSize);
    }

    @Override
    public BackupDetailVO getBackupDetail(Long id) {
        requireEnabled();
        return BackupDetailVO.from(requireBackup(id));
    }

    @Override
    public void download(Long id, HttpServletResponse response) {
        requireEnabled();
        BackupRecord record = requireBackup(id);
        requireSucceeded(record);

        Path file = resolveBackupFile(record);
        long size;
        try {
            size = Files.size(file);
        } catch (IOException e) {
            log.error("[DataManagement] 备份文件不可读 id={}", id);
            throw new BizException(ResultCode.BACKUP_FILE_MISSING);
        }

        response.reset();
        response.setContentType("application/zip");
        response.setContentLengthLong(size);
        String encoded = URLEncoder.encode(record.getFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);
        // 备份包含全量业务数据，绝不允许任何中间层缓存
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");

        try (InputStream in = Files.newInputStream(file);
             OutputStream out = response.getOutputStream()) {
            byte[] buffer = new byte[DOWNLOAD_BUFFER];
            int read = in.read(buffer);
            while (read != -1) {
                out.write(buffer, 0, read);
                read = in.read(buffer);
            }
            out.flush();
            log.info("[DataManagement] 备份下载完成 id={} operator={}", id, currentOperatorName());
        } catch (IOException e) {
            // 客户端中断下载是常态（用户点了取消），不必当作系统错误刷 error 日志
            log.warn("[DataManagement] 备份下载中断 id={} err={}", id, e.getClass().getSimpleName());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBackup(Long id) {
        requireEnabled();
        BackupRecord record = requireBackup(id);

        TaskStatusEnum status = TaskStatusEnum.of(record.getStatus());
        if (status != null && !status.isTerminal()) {
            log.warn("[DataManagement] 拒绝删除进行中的备份 id={} status={}", id, record.getStatus());
            throw new BizException(ResultCode.BACKUP_NOT_READY);
        }

        boolean removed = retentionPolicyExecutor.removeOne(record);
        if (!removed) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "备份删除失败，请稍后重试");
        }
        log.info("[DataManagement] 备份已删除 id={} file={} operator={}",
                id, record.getFileName(), currentOperatorName());
    }

    // ==================================================================
    // 恢复
    // ==================================================================

    @Override
    public TaskProgressDTO restore(Long id, RestoreReq req) {
        requireEnabled();
        RestoreReq safeReq = req == null ? new RestoreReq() : req;

        BackupRecord backup = requireBackup(id);
        requireSucceeded(backup);
        Path file = resolveBackupFile(backup);
        // 受理阶段就把包结构校一遍：让「包坏了」在点确认的瞬间报错，
        // 而不是等进度条跑到 15% 才失败，用户体验天差地别
        archiveService.validateAndReadManifest(file);

        BackupTypeEnum type = BackupTypeEnum.of(backup.getBackupType());
        if (type != null && !type.includeDb()) {
            throw new BizException(ResultCode.BACKUP_PACKAGE_INVALID,
                    "该备份仅包含配置文件，不支持数据恢复");
        }

        boolean preBackup = resolvePreBackup(safeReq.getPreBackup());
        String taskId = newTaskId();
        acquireLockOrThrow(taskId);

        RestoreRecord record = null;
        try {
            record = new RestoreRecord();
            record.setTaskId(taskId);
            record.setBackupId(backup.getId());
            record.setBackupFileName(backup.getFileName());
            record.setRestoreSource(RESTORE_SOURCE_LOCAL);
            record.setStatus(TaskStatusEnum.PENDING.getCode());
            record.setPhase(TaskPhaseEnum.INIT.getCode());
            record.setProgress(TaskPhaseEnum.INIT.getWeight());
            record.setAffectedTables(0);
            record.setErrorMsg("");
            fillOperator(record);
            restoreRecordMapper.insert(record);

            TaskProgressDTO progress = progressStore.init(
                    taskId, TaskProgressStore.TYPE_RESTORE, record.getId());
            restoreTaskExecutor.runAsync(record.getId(), preBackup);
            log.warn("[DataManagement] 恢复任务已受理 taskId={} backupId={} preBackup={} operator={}",
                    taskId, backup.getId(), preBackup, record.getOperatorName());
            return progress;
        } catch (RuntimeException e) {
            dataTaskLock.unlock(taskId);
            progressStore.remove(taskId);
            throw e;
        }
    }

    @Override
    public TaskProgressDTO uploadAndRestore(MultipartFile file, UploadRestoreReq req) {
        requireEnabled();
        UploadRestoreReq safeReq = req == null ? new UploadRestoreReq() : req;
        validateUpload(file);

        Path stored = persistUpload(file, safeReq);
        BackupRecord record;
        try {
            Map<String, Object> manifest = archiveService.validateAndReadManifest(stored);
            record = registerUploaded(stored, file, safeReq, manifest);
        } catch (RuntimeException e) {
            // 校验没过的包不留在磁盘上，免得堆积一地垃圾文件
            deleteQuietly(stored);
            throw e;
        }

        if (!Boolean.TRUE.equals(safeReq.getRestoreNow())) {
            log.info("[DataManagement] 上传包仅登记不恢复 backupId={} operator={}",
                    record.getId(), currentOperatorName());
            TaskProgressDTO done = new TaskProgressDTO();
            done.setTaskId(record.getTaskId());
            done.setTaskType(TaskProgressStore.TYPE_BACKUP);
            done.setStatus(TaskStatusEnum.SUCCESS.getCode());
            done.setPhase(TaskPhaseEnum.DONE.getCode());
            done.setPhaseDesc(TaskPhaseEnum.DONE.getDesc());
            done.setProgress(100);
            done.setMessage("备份包已上传并登记");
            done.setRecordId(record.getId());
            done.setFileName(record.getFileName());
            long now = System.currentTimeMillis();
            done.setStartedAt(now);
            done.setUpdatedAt(now);
            done.setFinished(Boolean.TRUE);
            return done;
        }

        RestoreReq restoreReq = new RestoreReq();
        restoreReq.setPreBackup(safeReq.getPreBackup());
        restoreReq.setRemark(safeReq.safeRemark());
        TaskProgressDTO progress = restore(record.getId(), restoreReq);

        // 标注恢复来源为 UPLOAD，便于审计区分「本地备份恢复」与「外部包恢复」
        RestoreRecord restoreRecord = restoreRecordMapper.selectById(progress.getRecordId());
        if (restoreRecord != null) {
            restoreRecord.setRestoreSource(RESTORE_SOURCE_UPLOAD);
            restoreRecordMapper.updateById(restoreRecord);
        }
        return progress;
    }

    // ==================================================================
    // 配置与进度
    // ==================================================================

    @Override
    public DataManagementConfigDTO getConfig() {
        requireEnabled();
        return configService.getConfig();
    }

    @Override
    public DataManagementConfigDTO updateConfig(DataManagementConfigDTO dto) {
        requireEnabled();
        DataManagementConfigDTO updated = configService.updateConfig(dto);
        log.info("[DataManagement] 配置已更新 operator={}", currentOperatorName());
        return updated;
    }

    @Override
    public TaskProgressDTO getTaskProgress(String taskId) {
        requireEnabled();
        TaskProgressDTO progress = progressStore.get(taskId);
        if (progress == null) {
            throw new BizException(ResultCode.DATA_TASK_NOT_FOUND);
        }
        return progress;
    }

    // ==================================================================
    // 内部辅助
    // ==================================================================

    /**
     * 模块开关校验。
     *
     * @throws BizException 模块被关闭
     */
    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new BizException(ResultCode.DATA_MANAGEMENT_DISABLED);
        }
    }

    /**
     * 抢占全局互斥锁，失败直接抛业务异常。
     *
     * @param taskId 任务号
     */
    private void acquireLockOrThrow(String taskId) {
        int ttl = configService.getTaskTimeoutSeconds(properties.getLockTtlSeconds());
        if (!dataTaskLock.tryLock(taskId, Math.max(ttl, properties.getLockTtlSeconds()))) {
            throw new BizException(ResultCode.DATA_TASK_RUNNING);
        }
    }

    /**
     * 读取备份记录，不存在直接抛异常。
     *
     * @param id 备份记录 id
     * @return 备份记录
     */
    private BackupRecord requireBackup(Long id) {
        if (id == null || id <= 0) {
            throw new BizException(ResultCode.BACKUP_NOT_FOUND);
        }
        BackupRecord record = backupRecordMapper.selectById(id);
        if (record == null) {
            throw new BizException(ResultCode.BACKUP_NOT_FOUND);
        }
        return record;
    }

    /**
     * 要求备份处于 SUCCESS 状态。
     *
     * @param record 备份记录
     */
    private void requireSucceeded(BackupRecord record) {
        if (!TaskStatusEnum.SUCCESS.getCode().equals(record.getStatus())) {
            throw new BizException(ResultCode.BACKUP_NOT_READY);
        }
    }

    /**
     * 由相对路径还原备份文件绝对路径，并做越界防护。
     *
     * @param record 备份记录
     * @return 备份文件绝对路径
     */
    private Path resolveBackupFile(BackupRecord record) {
        String relative = record.getFilePath();
        if (relative == null || relative.trim().isEmpty()) {
            throw new BizException(ResultCode.BACKUP_FILE_MISSING);
        }
        Path root = configService.getBackupRoot();
        Path file = root.resolve(relative).normalize();
        // 路径穿越防护：库里的相对路径若被篡改为 ../../etc/passwd 必须挡住
        if (!file.startsWith(root)) {
            log.error("[DataManagement] 检测到越界的备份路径，已拒绝访问 id={}", record.getId());
            throw new BizException(ResultCode.BACKUP_FILE_MISSING);
        }
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            log.warn("[DataManagement] 备份文件已丢失 id={}", record.getId());
            throw new BizException(ResultCode.BACKUP_FILE_MISSING);
        }
        return file;
    }

    /**
     * 决定本次恢复是否做预备份。
     *
     * <p>系统配置开启强制预备份时，用户传 false 也无效 ——
     * 这是防手滑的最后一道保险，管理员在页面上再自信也不能绕过。</p>
     *
     * @param requested 用户请求值，可为 null
     * @return 最终生效值
     */
    private boolean resolvePreBackup(Boolean requested) {
        if (configService.isPreBackupEnabled()) {
            if (Boolean.FALSE.equals(requested)) {
                log.warn("[DataManagement] 系统强制恢复前备份，已忽略用户的跳过请求");
            }
            return true;
        }
        return !Boolean.FALSE.equals(requested);
    }

    /**
     * 上传文件基础校验。
     *
     * @param file 上传文件
     */
    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.VALID_ERROR, "请选择要上传的备份文件");
        }
        long maxBytes = configService.getUploadMaxBytes();
        if (file.getSize() > maxBytes) {
            log.warn("[DataManagement] 上传文件超限 size={}B limit={}B", file.getSize(), maxBytes);
            throw new BizException(ResultCode.BACKUP_UPLOAD_TOO_LARGE);
        }
        String original = file.getOriginalFilename();
        if (original == null || !original.toLowerCase(Locale.ROOT).endsWith(ALLOWED_EXTENSION)) {
            throw new BizException(ResultCode.BACKUP_PACKAGE_INVALID,
                    "仅支持上传 .zip 格式的备份文件");
        }
    }

    /**
     * 把上传文件落盘到备份根目录下的 upload 子目录。
     *
     * @param file    上传文件
     * @param req     上传参数
     * @return 落盘后的绝对路径
     */
    private Path persistUpload(MultipartFile file, UploadRestoreReq req) {
        LocalDateTime now = LocalDateTime.now();
        String fileName = "issueflow_upload_" + now.format(FILE_TS) + "_"
                + UUID.randomUUID().toString().substring(0, 8) + ALLOWED_EXTENSION;
        Path root = configService.getBackupRoot();
        Path dir = root.resolve(UPLOAD_SUB_DIR).resolve(now.format(MONTH_DIR));
        Path target = dir.resolve(fileName).normalize();
        if (!target.startsWith(root)) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "上传目录解析异常");
        }
        try {
            Files.createDirectories(dir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("[DataManagement] 上传文件落盘失败 name={}", req.safeName(), e);
            throw new BizException(ResultCode.SYSTEM_ERROR, "备份文件保存失败，请稍后重试");
        }
        return target;
    }

    /**
     * 为上传的备份包登记一条 UPLOAD 来源的备份记录。
     *
     * @param stored   已落盘路径
     * @param file     上传文件
     * @param req      上传参数
     * @param manifest 包内 manifest
     * @return 登记后的备份记录
     */
    private BackupRecord registerUploaded(Path stored, MultipartFile file,
                                          UploadRestoreReq req, Map<String, Object> manifest) {
        Path root = configService.getBackupRoot();
        String relative = root.relativize(stored).toString().replace('\\', '/');

        String checksum;
        long size;
        try {
            checksum = archiveService.sha256(stored);
            size = Files.size(stored);
        } catch (IOException e) {
            log.error("[DataManagement] 上传包校验和计算失败", e);
            throw new BizException(ResultCode.BACKUP_PACKAGE_INVALID, "备份文件校验失败，请重新上传");
        }

        BackupRecord record = new BackupRecord();
        record.setTaskId(newTaskId());
        record.setFileName(stored.getFileName().toString());
        record.setFilePath(relative);
        record.setFileSize(size);
        record.setChecksum(checksum);
        record.setBackupType(manifestString(manifest, "backupType", BackupTypeEnum.FULL.getCode()));
        record.setSource(BackupSourceEnum.UPLOAD.getCode());
        record.setStatus(TaskStatusEnum.SUCCESS.getCode());
        record.setPhase(TaskPhaseEnum.DONE.getCode());
        record.setProgress(100);
        record.setErrorMsg("");
        record.setDbName(manifestString(manifest, "dbName", ""));
        record.setAppVersion(manifestString(manifest, "appVersion", ""));
        record.setTableCount(manifestInt(manifest, "tableCount"));
        String displayName = req.safeName().isEmpty()
                ? String.valueOf(file.getOriginalFilename()) : req.safeName();
        record.setRemark("上传导入：" + displayName);
        record.setStartedAt(LocalDateTime.now());
        record.setFinishedAt(LocalDateTime.now());
        record.setDurationMs(0L);
        fillOperator(record);
        backupRecordMapper.insert(record);

        log.info("[DataManagement] 上传备份包已登记 id={} size={}B operator={}",
                record.getId(), size, record.getOperatorName());
        return record;
    }

    /**
     * 从 manifest 取字符串。
     *
     * @param manifest     manifest
     * @param key          键
     * @param defaultValue 默认值
     * @return 字符串值
     */
    private String manifestString(Map<String, Object> manifest, String key, String defaultValue) {
        Object value = manifest == null ? null : manifest.get(key);
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return defaultValue;
        }
        return String.valueOf(value).trim();
    }

    /**
     * 从 manifest 取整数。
     *
     * @param manifest manifest
     * @param key      键
     * @return 整数值，解析失败返回 0
     */
    private int manifestInt(Map<String, Object> manifest, String key) {
        Object value = manifest == null ? null : manifest.get(key);
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 填充操作人信息（备份记录）。
     *
     * @param record 备份记录
     */
    private void fillOperator(BackupRecord record) {
        record.setOperatorId(SecurityUtils.getCurrentUserId());
        record.setOperatorName(currentOperatorName());
    }

    /**
     * 填充操作人信息（恢复记录）。
     *
     * @param record 恢复记录
     */
    private void fillOperator(RestoreRecord record) {
        record.setOperatorId(SecurityUtils.getCurrentUserId());
        record.setOperatorName(currentOperatorName());
    }

    /**
     * 读取当前登录用户的展示名。
     *
     * @return 姓名 / 用户名；取不到返回 {@code system}
     */
    private String currentOperatorName() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return "system";
        }
        try {
            User user = userMapper.selectById(userId);
            if (user == null) {
                return "system";
            }
            if (user.getRealName() != null && !user.getRealName().trim().isEmpty()) {
                return user.getRealName();
            }
            return user.getUsername() == null ? "system" : user.getUsername();
        } catch (Exception e) {
            log.debug("[DataManagement] 操作人姓名读取失败: {}", e.getClass().getSimpleName());
            return "system";
        }
    }

    /**
     * 受理阶段失败时把记录标记为失败，避免留下永远 PENDING 的僵尸记录。
     *
     * @param record 备份记录
     * @param e      异常
     */
    private void markAcceptFailed(BackupRecord record, RuntimeException e) {
        try {
            record.setStatus(TaskStatusEnum.FAILED.getCode());
            record.setErrorMsg(e.getMessage() == null ? "任务受理失败" : e.getMessage());
            record.setFinishedAt(LocalDateTime.now());
            backupRecordMapper.updateById(record);
        } catch (Exception ignored) {
            log.warn("[DataManagement] 受理失败状态回写异常 taskId={}", record.getTaskId());
        }
    }

    /**
     * 静默删除文件。
     *
     * @param path 路径
     */
    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("[DataManagement] 临时上传文件清理失败: {}", e.getClass().getSimpleName());
        }
    }

    /**
     * 生成任务号。
     *
     * @return UUID 去横线
     */
    private String newTaskId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 非空白判断。
     *
     * @param text 文本
     * @return true 非空白
     */
    private boolean isNotBlank(String text) {
        return text != null && !text.trim().isEmpty();
    }
}
