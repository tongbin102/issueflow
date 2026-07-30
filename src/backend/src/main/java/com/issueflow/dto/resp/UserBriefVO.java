package com.issueflow.dto.resp;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户简览视图对象（负责人/成员下拉与回显用）
 */
@Data
public class UserBriefVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户 id */
    private Long id;

    /** 真实姓名 */
    private String realName;

    /** 登录名 */
    private String username;

    /** 角色名（由 role.name 回填） */
    private String roleName;
}
