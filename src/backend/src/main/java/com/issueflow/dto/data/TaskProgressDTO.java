package com.issueflow.dto.data;

import lombok.Data;

import java.io.Serializable;

/**
 * 备份 / 恢复任务进度（Phase10）。
 *
 * <p>存放于 Redis {@code dm:task:{taskId}}（TTL 2h），
 * 由 {@code GET /api/admin/data/tasks/{taskId}} 直接下发给前端轮询。</p>
 *
 * <p><b>安全约束</b>：{@code message} / {@code errorMsg} 必须是**脱敏后**的文案，
 * 绝不允许携带数据库密码、服务器绝对路径、mysqldump 原始 stderr 片段。</p>
 */
@Data
public class TaskProgressDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 任务号（UUID 去横线） */
    private String taskId = "";

    /** 任务种类：{@code BACKUP} / {@code RESTORE} */
    private String taskType = "";

    /** 状态：见 {@link com.issueflow.enums.TaskStatusEnum} */
    private String status = "";

    /** 阶段：见 {@link com.issueflow.enums.TaskPhaseEnum} */
    private String phase = "";

    /** 阶段中文描述，前端进度条副标题直接展示 */
    private String phaseDesc = "";

    /** 进度百分比 0-100 */
    private Integer progress = 0;

    /** 附加提示（已脱敏） */
    private String message = "";

    /** 失败原因（已脱敏），成功时为空串 */
    private String errorMsg = "";

    /** 关联的业务记录 id：备份任务为 backup_record.id，恢复任务为 restore_record.id */
    private Long recordId = null;

    /** 备份成功后的文件名，便于前端直接给出「下载」入口 */
    private String fileName = "";

    /** 开始时间戳（毫秒） */
    private Long startedAt = 0L;

    /** 更新时间戳（毫秒） */
    private Long updatedAt = 0L;

    /** 是否终态（前端据此停止轮询） */
    private Boolean finished = Boolean.FALSE;
}
