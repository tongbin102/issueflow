package com.issueflow.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 通用启停入参（字典项 / 定时任务共用，避免重复建 DTO）
 */
@Data
public class StatusToggleReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** true 启用 / false 停用 */
    @NotNull(message = "状态不能为空")
    private Boolean enabled;
}
