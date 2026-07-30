package com.issueflow.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户新增/编辑请求
 */
@Data
public class UserReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 登录名 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 密码（新增必填；编辑时为空则保持原密码） */
    private String password;

    /** 真实姓名 */
    private String realName;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String phone;

    /** 角色 id（关联 role.id） */
    @NotNull(message = "角色不能为空")
    private Long roleId;

    /** 状态：1 启用 / 0 禁用（默认 1） */
    private Integer status = 1;
}
