package com.issueflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.issueflow.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 操作历史表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("issue_history")
public class IssueHistory extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 关联问题 id */
    private Long issueId;

    /** 动作：CREATE/CLAIM/SUBMIT_FIX/VERIFY_PASS/VERIFY_REJECT/CLOSE/REOPEN/EDIT */
    private String action;

    /** 源状态 */
    private Integer fromStatus;

    /** 目标状态 */
    private Integer toStatus;

    /** 操作人 id */
    private Long operatorId;

    /** 备注/原因 */
    private String remark;
}
