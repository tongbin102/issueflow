package com.issueflow.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 个人资料视图对象
 * <p>email / phone 为脱敏值供展示；emailRaw / phoneRaw 为原值供编辑回填。</p>
 */
@Data
public class ProfileVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 登录名（不可改） */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 真实姓名 */
    private String realName;

    /** 头像相对路径 */
    private String avatar;

    /** 脱敏邮箱 */
    private String email;

    /** 脱敏手机号 */
    private String phone;

    /** 原始邮箱（编辑回填） */
    private String emailRaw;

    /** 原始手机号（编辑回填） */
    private String phoneRaw;

    /** 所属组织名称 */
    private String orgName;

    /** 角色名称 */
    private String roleName;

    /** 角色编码 */
    private String roleCode;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /** 上次改密时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime pwdUpdatedAt;
}
