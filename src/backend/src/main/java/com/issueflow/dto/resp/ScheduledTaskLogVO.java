package com.issueflow.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 定时任务执行日志视图对象
 */
@Data
public class ScheduledTaskLogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /** 耗时（毫秒） */
    private Long costMs;

    /** 是否成功 */
    private Boolean success;

    /** 执行结果说明 */
    private String message;

    /** 触发方式 CRON / MANUAL */
    private String triggerType;
}
