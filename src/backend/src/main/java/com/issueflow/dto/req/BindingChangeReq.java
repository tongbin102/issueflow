package com.issueflow.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 手机号 / 邮箱绑定变更请求（需当前密码二次确认）
 */
@Data
public class BindingChangeReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 变更类型：PHONE / EMAIL */
    @NotBlank(message = "绑定类型不能为空")
    @Pattern(regexp = "^(PHONE|EMAIL)$", message = "绑定类型只能是 PHONE 或 EMAIL")
    private String type;

    /** 新值 */
    @NotBlank(message = "绑定值不能为空")
    @Size(max = 100, message = "绑定值不能超过 100 字")
    private String value;

    /** 当前密码（二次确认） */
    @NotBlank(message = "当前密码不能为空")
    private String currentPassword;
}
