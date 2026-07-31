package com.issueflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.issueflow.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 定时任务（Phase 7 新增）
 * <p>next_exec_time 不落库，由 {@code CronUtils.nextExecTime(cron)} 实时计算，
 * 避免持久化值与调度器实际状态不一致。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("scheduled_task")
public class ScheduledTask extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 任务名称 */
    private String taskName;

    /** 任务分组 */
    private String taskGroup;

    /** 执行目标，必须命中后端 jobRegistry 白名单 */
    private String jobKey;

    /** Spring cron 表达式（6 位） */
    private String cron;

    /** 任务参数 JSON 字符串 */
    private String params;

    /** 1 运行 / 0 暂停 */
    private Integer status;

    /** 描述 */
    private String description;

    /** 上次执行时间 */
    private LocalDateTime lastExecTime;

    /** 上次执行结果 1 成功 / 0 失败 */
    private Integer lastExecResult;

    /** 上次执行耗时（毫秒） */
    private Long lastCostMs;
}
