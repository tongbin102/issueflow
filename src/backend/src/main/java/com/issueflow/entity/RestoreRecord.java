package com.issueflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.issueflow.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 恢复记录实体（Phase10 数据管理）。
 *
 * <p>对应表 {@code restore_record}，一次恢复任务落一条。</p>
 *
 * <p>{@code preBackupId} 指向恢复前自动生成的「安全备份」，
 * 恢复出问题时可据此回退。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("restore_record")
public class RestoreRecord extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 任务号（UUID 去横线），与 Redis {@code dm:task:{taskId}} 对应，业务唯一 */
    private String taskId;

    /** 所恢复的 {@code backup_record.id}；上传恢复时为新建的 UPLOAD 记录 id */
    private Long backupId;

    /** 所恢复的备份文件名快照 */
    private String backupFileName;

    /** 恢复来源：{@code LOCAL} 本地备份 / {@code UPLOAD} 上传文件 */
    private String restoreSource;

    /** 状态：见 {@link com.issueflow.enums.TaskStatusEnum} */
    private String status;

    /** 阶段：见 {@link com.issueflow.enums.TaskPhaseEnum} */
    private String phase;

    /** 进度百分比 0-100 */
    private Integer progress;

    /** 失败原因（已脱敏） */
    private String errorMsg;

    /** 恢复前自动安全备份的 {@code backup_record.id} */
    private Long preBackupId;

    /** 恢复涉及的表数量 */
    private Integer affectedTables;

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
