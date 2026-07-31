package com.issueflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.issueflow.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 定时任务执行日志（Phase 7 新增）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("scheduled_task_log")
public class ScheduledTaskLog extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 关联任务 scheduled_task.id */
    private Long taskId;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 耗时（毫秒） */
    private Long costMs;

    /** 1 成功 / 0 失败 */
    private Integer success;

    /** 执行结果说明或异常摘要 */
    private String message;

    /** 触发方式 CRON / MANUAL */
    private String triggerType;
}
