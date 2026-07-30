package com.issueflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.issueflow.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目模块表（树形，邻接表：parent_id 自引用，0 = 根）
 *
 * <p>注意：本类与 {@code java.lang.Module} 同名，使用处必须显式 import 本类。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("module")
public class Module extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属项目 id（project.id，逻辑删除下不加外键） */
    private Long projectId;

    /** 父模块 id，0 = 根 */
    private Long parentId;

    /** 模块名称（同父级下唯一，应用层校验） */
    private String name;

    /** 模块描述 */
    private String description;

    /** 同级排序号，升序，从 1 连续重排 */
    private Integer sort;
}
