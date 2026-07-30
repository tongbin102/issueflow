package com.issueflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.issueflow.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 菜单表（树形，parent_id=0 表示根）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("menu")
public class Menu extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 菜单名称 */
    private String name;

    /** 路由路径 */
    private String path;

    /** 父级 id，0 表示根 */
    private Long parentId;

    /** 排序号，升序展示 */
    private Integer sort;

    /** 权限标识 module:resource:action */
    private String permission;

    /** 图标名（Element Plus icon 名） */
    private String icon;
}
