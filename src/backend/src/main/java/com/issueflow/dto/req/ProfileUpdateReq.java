package com.issueflow.dto.req;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 个人资料编辑请求
 * <p><b>不含 userId</b>：一律操作当前登录用户，结构性杜绝越权。</p>
 */
@Data
public class ProfileUpdateReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 昵称（为空时前端展示 realName） */
    @Size(max = 50, message = "昵称不能超过 50 字")
    private String nickname;

    /** 真实姓名 */
    @Size(max = 50, message = "姓名不能超过 50 字")
    private String realName;

    /** 邮箱 */
    @Size(max = 100, message = "邮箱不能超过 100 字")
    @Email(message = "邮箱格式不正确")
    private String email;

    /** 手机号（11 位大陆号码，可为空） */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
}
