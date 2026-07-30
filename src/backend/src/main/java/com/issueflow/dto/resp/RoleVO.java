package com.issueflow.dto.resp;

import lombok.Data;

import java.io.Serializable;

/**
 * 角色视图对象
 */
@Data
public class RoleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String code;

    private String name;

    private String description;

    /** 已分配权限数 */
    private Integer permissionCount;

    /** 是否内置角色（内置不可删/改码） */
    private Boolean builtin;
}
