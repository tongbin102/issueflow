package com.issueflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.issueflow.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 备份记录实体（Phase10 数据管理）。
 *
 * <p>对应表 {@code backup_record}，一次备份任务落一条。</p>
 *
 * <p><b>安全约束</b>：{@code filePath} 只保存**相对备份根目录**的路径，
 * 绝不保存绝对路径 —— 该字段会随列表接口下发到前端，
 * 泄露服务器目录结构属于安全红线。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("backup_record")
public class BackupRecord extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 任务号（UUID 去横线），与 Redis {@code dm:task:{taskId}} 对应，业务唯一 */
    private String taskId;

    /** 备份文件名，形如 {@code issueflow_backup_20260803_101530_FULL.zip} */
    private String fileName;

    /** 相对备份根目录的路径（绝不存绝对路径） */
    private String filePath;

    /** 文件字节数，进行中为 0 */
    private Long fileSize;

    /** SHA-256 摘要（小写 hex），下载 / 恢复前做完整性校验 */
    private String checksum;

    /** 备份类型：见 {@link com.issueflow.enums.BackupTypeEnum} */
    private String backupType;

    /** 来源：见 {@link com.issueflow.enums.BackupSourceEnum} */
    private String source;

    /** 状态：见 {@link com.issueflow.enums.TaskStatusEnum} */
    private String status;

    /** 阶段：见 {@link com.issueflow.enums.TaskPhaseEnum} */
    private String phase;

    /** 进度百分比 0-100 */
    private Integer progress;

    /** 失败原因（已脱敏，绝不含密码 / 全路径 / dump 片段） */
    private String errorMsg;

    /** 备份时的数据库名 */
    private String dbName;

    /** 备份时的应用版本，恢复时做兼容性提示 */
    private String appVersion;

    /** 备份包含的表数量 */
    private Integer tableCount;

    /** 备注（用户填写） */
    private String remark;

    /** 操作人 user.id */
    private Long operatorId;

    /** 操作人姓名快照 */
    private String operatorName;

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 结束时间 */
    private LocalDateTime finishedAt;

    /** 耗时毫秒 */
    private Long durationMs;
}
