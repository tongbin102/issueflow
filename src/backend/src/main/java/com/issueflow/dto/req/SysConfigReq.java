package com.issueflow.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 系统配置写入请求（按 configKey 更新 configValue）
 */
@Data
public class SysConfigReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 配置键 */
    @NotBlank(message = "配置键不能为空")
    private String configKey;

    /** 配置值（JSON 文本） */
    private String configValue;
}
