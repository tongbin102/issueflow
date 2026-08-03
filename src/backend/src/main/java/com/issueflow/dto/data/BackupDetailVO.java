package com.issueflow.dto.data;

import com.issueflow.entity.BackupRecord;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 备份详情视图（Phase10 数据管理）。
 *
 * <p>用于恢复前的二次确认弹窗：把「要拿哪份包覆盖现网」讲清楚。</p>
 *
 * <p><b>安全约束</b>：同样不含 {@code filePath}。{@code checksum} 只回传前 12 位，
 * 供人工核对，既能辨识又不泄露完整完整性凭据。</p>
 */
@Data
public class BackupDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 备份记录 id */
    private Long id = null;

    /** 任务号 */
    private String taskId = "";

    /** 展示名称（备注，回落文件名） */
    private String name = "";

    /** 备份文件名 */
    private String fileName = "";

    /** 文件字节数 */
    private Long size = 0L;

    /** 校验和前缀（前 12 位，人工核对用） */
    private String checksumPrefix = "";

    /** 备份类型 */
    private String backupType = "";

    /** 来源 */
    private String source = "";

    /** 状态 */
    private String status = "";

    /** 阶段 */
    private String phase = "";

    /** 进度百分比 */
    private Integer progress = 0;

    /** 失败原因（已脱敏） */
    private String errorMsg = "";

    /** 备份时的数据库名 */
    private String dbName = "";

    /** 备份时的应用版本，恢复时做兼容性提示 */
    private String appVersion = "";

    /** 备份包含的表数量 */
    private Integer tableCount = 0;

    /** 操作人姓名快照 */
    private String operatorName = "";

    /** 创建时间 */
    private LocalDateTime createTime = null;

    /** 开始时间 */
    private LocalDateTime startedAt = null;

    /** 结束时间 */
    private LocalDateTime finishedAt = null;

    /** 耗时毫秒 */
    private Long durationMs = 0L;

    /**
     * 由实体转换为详情视图。
     *
     * @param record 备份记录实体，可为 null
     * @return 详情视图；入参为 null 时返回 null
     */
    public static BackupDetailVO from(BackupRecord record) {
        if (record == null) {
            return null;
        }
        BackupDetailVO vo = new BackupDetailVO();
        vo.setId(record.getId());
        vo.setTaskId(nullToEmpty(record.getTaskId()));
        String remark = record.getRemark();
        vo.setName(remark == null || remark.trim().isEmpty()
                ? nullToEmpty(record.getFileName())
                : remark.trim());
        vo.setFileName(nullToEmpty(record.getFileName()));
        vo.setSize(record.getFileSize() == null ? 0L : record.getFileSize());
        vo.setChecksumPrefix(shorten(record.getChecksum()));
        vo.setBackupType(nullToEmpty(record.getBackupType()));
        vo.setSource(nullToEmpty(record.getSource()));
        vo.setStatus(nullToEmpty(record.getStatus()));
        vo.setPhase(nullToEmpty(record.getPhase()));
        vo.setProgress(record.getProgress() == null ? 0 : record.getProgress());
        vo.setErrorMsg(nullToEmpty(record.getErrorMsg()));
        vo.setDbName(nullToEmpty(record.getDbName()));
        vo.setAppVersion(nullToEmpty(record.getAppVersion()));
        vo.setTableCount(record.getTableCount() == null ? 0 : record.getTableCount());
        vo.setOperatorName(nullToEmpty(record.getOperatorName()));
        vo.setCreateTime(record.getCreatedAt());
        vo.setStartedAt(record.getStartedAt());
        vo.setFinishedAt(record.getFinishedAt());
        vo.setDurationMs(record.getDurationMs() == null ? 0L : record.getDurationMs());
        return vo;
    }

    /**
     * 截断校验和到前 12 位。
     *
     * @param checksum 完整校验和，可为 null
     * @return 前缀；入参为空返回空串
     */
    private static String shorten(String checksum) {
        if (checksum == null || checksum.isEmpty()) {
            return "";
        }
        return checksum.length() <= 12 ? checksum : checksum.substring(0, 12);
    }

    /**
     * null 安全的字符串。
     *
     * @param value 原始值
     * @return 非 null 字符串
     */
    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
