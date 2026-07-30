package com.issueflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.issueflow.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程流转规则：from/to 唯一；config_key 承接旧流程开关
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("flow_transition")
public class FlowTransition extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 源节点 id */
    private Long fromNodeId;

    /** 目标节点 id */
    private Long toNodeId;

    /** 动作码（HistoryActionEnum） */
    private String actionCode;

    /** 动作中文名 */
    private String actionName;

    /** 允许角色码（逗号分隔） */
    private String allowRoles;

    /** 是否必填原因 0/1 */
    private Integer remarkRequired;

    /** 承接的流程开关键（flow_reject_enabled / flow_reopen_enabled），可空 */
    private String configKey;

    /** 1启用 0禁用 */
    private Integer enabled;

    /** 排序号 */
    private Integer sort;
}
