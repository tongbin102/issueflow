package com.issueflow.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 系统配置项新增/编辑请求
 * <p>编辑时 {@code configKey} 被服务端忽略（键名不可改）。</p>
 */
@Data
public class ConfigItemReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 配置键 */
    @NotBlank(message = "配置键不能为空")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9._-]{1,63}$",
            message = "配置键须以字母开头，仅含字母、数字、点、下划线、短横线，长度 2-64")
    private String configKey;

    /** 配置值 */
    @Size(max = 2000, message = "配置值不能超过 2000 字")
    private String configValue;

    /** 描述 */
    @Size(max = 200, message = "描述不能超过 200 字")
    private String description;
}
