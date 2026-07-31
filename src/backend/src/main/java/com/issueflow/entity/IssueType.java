package com.issueflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.issueflow.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 问题类型（Phase 6 新增）
 * <p>与 Issue 为 1:N 弱关联（issue.type_id，无外键）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("issue_type")
public class IssueType extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 类型名称 */
    private String name;

    /** 类型编码（大写），供程序判断与 i18n key 拼接 */
    private String code;

    /** 描述 */
    private String description;

    /** 升序展示 */
    private Integer sort;

    /** 1 启用 / 0 停用 */
    private Integer enabled;
}
