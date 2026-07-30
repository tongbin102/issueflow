package com.issueflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.issueflow.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 问题关联实体（仅存前置边；后置由反向推导）。继承 BaseEntity 软删除。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("issue_relation")
public class IssueRelation extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 当前问题 X */
    private Long issueId;

    /** 关联问题 P（rel_type=1 表示 P 是 X 的前置） */
    private Long relatedId;

    /** 关联类型：1=related_id 是 issue_id 的前置任务 */
    private Integer relType;
}
