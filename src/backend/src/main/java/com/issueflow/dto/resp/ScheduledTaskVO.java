package com.issueflow.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 定时任务视图对象
 */
@Data
public class ScheduledTaskVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 任务名称 */
    private String taskName;

    /** 任务分组 */
    private String taskGroup;

    /** 执行目标 key */
    private String jobKey;

    /** 执行目标展示名（来自 jobRegistry，缺失时回退 jobKey） */
    private String jobName;

    /** cron 表达式 */
    private String cron;

    /** 参数 JSON */
    private String params;

    /** 1 运行 / 0 暂停 */
    private Integer status;

    /** 描述 */
    private String description;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastExecTime;

    /** 上次执行结果 1 成功 / 0 失败 */
    private Integer lastExecResult;

    /** 上次耗时（毫秒） */
    private Long lastCostMs;

    /** 下次执行时间（实时按 cron 计算，不落库） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime nextExecTime;
}
