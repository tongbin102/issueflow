package com.issueflow.dto.data;

import com.issueflow.entity.BackupRecord;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 备份列表行视图（Phase10 数据管理）。
 *
 * <p>对应 {@code GET /api/admin/data-management/backups}。</p>
 *
 * <p><b>安全约束</b>：本 VO <b>刻意不含 {@code filePath} 与 {@code checksum}</b> ——
 * 前者会泄露服务器目录结构，后者对列表展示无用且属于内部完整性凭据。
 * 需要时走 {@link BackupDetailVO}（同样只给相对信息）。</p>
 */
@Data
public class BackupListVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 备份记录 id */
    private Long id = null;

    /** 任务号，前端据此可继续轮询进度 */
    private String taskId = "";

    /** 展示名称：用户填的备注，为空时回落为文件名 */
    private String name = "";

    /** 备份文件名 */
    private String fileName = "";

    /** 创建时间 */
    private LocalDateTime createTime = null;

    /** 文件字节数，进行中为 0 */
    private Long size = 0L;

    /** 文件后缀（zip / sql），前端据此显示图标 */
    private String fileType = "";

    /** 备份类型：FULL / DB_ONLY / CONFIG_ONLY */
    private String backupType = "";

    /** 来源：MANUAL / AUTO / UPLOAD / PRE_RESTORE */
    private String source = "";

    /** 状态：PENDING / RUNNING / SUCCESS / FAILED / CANCELED */
    private String status = "";

    /** 进度百分比 0-100 */
    private Integer progress = 0;

    /** 失败原因（已脱敏），成功时为空串 */
    private String errorMsg = "";

    /** 操作人姓名快照 */
    private String operatorName = "";

    /** 耗时毫秒 */
    private Long durationMs = 0L;

    /**
     * 由实体转换为列表视图。
     *
     * @param record 备份记录实体，可为 null
     * @return 列表视图；入参为 null 时返回 null
     */
    public static BackupListVO from(BackupRecord record) {
        if (record == null) {
            return null;
        }
        BackupListVO vo = new BackupListVO();
        vo.setId(record.getId());
        vo.setTaskId(nullToEmpty(record.getTaskId()));
        String remark = record.getRemark();
        vo.setName(remark == null || remark.trim().isEmpty()
                ? nullToEmpty(record.getFileName())
                : remark.trim());
        vo.setFileName(nullToEmpty(record.getFileName()));
        vo.setCreateTime(record.getCreatedAt());
        vo.setSize(record.getFileSize() == null ? 0L : record.getFileSize());
        vo.setFileType(extractSuffix(record.getFileName()));
        vo.setBackupType(nullToEmpty(record.getBackupType()));
        vo.setSource(nullToEmpty(record.getSource()));
        vo.setStatus(nullToEmpty(record.getStatus()));
        vo.setProgress(record.getProgress() == null ? 0 : record.getProgress());
        vo.setErrorMsg(nullToEmpty(record.getErrorMsg()));
        vo.setOperatorName(nullToEmpty(record.getOperatorName()));
        vo.setDurationMs(record.getDurationMs() == null ? 0L : record.getDurationMs());
        return vo;
    }

    /**
     * 取文件名后缀（小写，不含点）。
     *
     * @param fileName 文件名，可为 null
     * @return 后缀；无后缀返回空串
     */
    private static String extractSuffix(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase();
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
