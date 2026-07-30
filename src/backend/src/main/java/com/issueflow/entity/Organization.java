package com.issueflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.issueflow.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 组织表（树形，parent_id=0 表示根）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("organization")
public class Organization extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 组织名称 */
    private String name;

    /** 父级 id，0 表示根 */
    private Long parentId;

    /** 排序号，升序展示 */
    private Integer sort;
}
