package com.issueflow.dto.resp;

import lombok.Data;

import java.io.Serializable;

/**
 * 权限目录视图对象
 */
@Data
public class PermissionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String code;

    private String name;

    private String module;

    private String action;

    /** 端维度：1=前台端 2=后台端 */
    private Integer type;

    private Integer sort;
}
