package com.issueflow.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 流程流转规则新建/编辑请求
 */
@Data
public class FlowTransitionReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 源节点 id */
    @NotNull(message = "源节点不能为空")
    private Long fromNodeId;

    /** 目标节点 id */
    @NotNull(message = "目标节点不能为空")
    private Long toNodeId;

    /** 动作码（HistoryActionEnum） */
    @NotBlank(message = "动作码不能为空")
    private String actionCode;

    /** 动作中文名 */
    private String actionName;

    /** 允许角色码（逗号分隔） */
    private String allowRoles;

    /** 是否必填原因 0/1 */
    private Integer remarkRequired;

    /** 承接的开关键（可空） */
    private String configKey;

    /** 1启用 0禁用 */
    private Integer enabled;

    /** 排序号 */
    private Integer sort;
}
