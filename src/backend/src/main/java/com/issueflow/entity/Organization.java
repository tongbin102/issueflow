package com.issueflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.issueflow.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 组织表（树形，parent_id=0 表示根；Phase 5 扩展 code/leader_id/status/description）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("organization")
public class Organization extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 组织名称 */
    private String name;

    /** 组织编码（必填唯一） */
    private String code;

    /** 负责人 user.id（可空） */
    private Long leaderId;

    /** 状态：1 启用 / 0 禁用 */
    private Integer status;

    /** 组织描述 */
    private String description;

    /** 父级 id，0 表示根 */
    private Long parentId;

    /** 排序号，升序展示 */
    private Integer sort;
}
