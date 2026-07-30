package com.issueflow.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 菜单新建/编辑请求
 */
@Data
public class MenuReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 菜单名称 */
    @NotBlank(message = "菜单名称不能为空")
    private String name;

    /** 路由路径 */
    private String path;

    /** 父级 id，0 表示根，默认 0 */
    private Long parentId = 0L;

    /** 排序号，升序展示，默认 0 */
    private Integer sort = 0;

    /** 权限标识 module:resource:action */
    private String permission;

    /** 图标名（Element Plus icon 名） */
    private String icon;

    /** 端维度：1=前台端 / 2=后台端（默认 2） */
    private Integer type = 2;
}
