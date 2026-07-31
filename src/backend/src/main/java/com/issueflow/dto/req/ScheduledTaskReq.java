package com.issueflow.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 定时任务新增/编辑请求
 * <p>{@code jobKey} 必须命中后端 jobRegistry 白名单，{@code cron} 由 CronUtils 校验。</p>
 */
@Data
public class ScheduledTaskReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 任务名称 */
    @NotBlank(message = "任务名称不能为空")
    @Size(max = 100, message = "任务名称不能超过 100 字")
    private String taskName;

    /** 任务分组 */
    @Size(max = 50, message = "任务分组不能超过 50 字")
    private String taskGroup = "default";

    /** 执行目标 */
    @NotBlank(message = "执行目标不能为空")
    @Size(max = 100, message = "执行目标不能超过 100 字")
    private String jobKey;

    /** cron 表达式 */
    @NotBlank(message = "cron 表达式不能为空")
    @Size(max = 100, message = "cron 表达式不能超过 100 字")
    private String cron;

    /** 参数 JSON 字符串 */
    @Size(max = 500, message = "任务参数不能超过 500 字")
    private String params;

    /** 1 运行 / 0 暂停，默认运行 */
    private Integer status = 1;

    /** 描述 */
    @Size(max = 200, message = "描述不能超过 200 字")
    private String description;
}
