package com.issueflow.dto.resp;

import lombok.Data;

import java.io.Serializable;

/**
 * 菜单视图对象
 */
@Data
public class MenuVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    private String path;

    private Long parentId;

    private Integer sort;

    private String permission;

    private String icon;

    /** 端维度：1=前台端 / 2=后台端 */
    private Integer type;
}
