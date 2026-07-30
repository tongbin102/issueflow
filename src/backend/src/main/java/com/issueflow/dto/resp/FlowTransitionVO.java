package com.issueflow.dto.resp;

import lombok.Data;

import java.io.Serializable;

/**
 * 流程流转规则视图对象（含源/目标节点状态码与名称，便于前端绘图）
 */
@Data
public class FlowTransitionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long fromNodeId;

    private Long toNodeId;

    private Integer fromStatusCode;

    private Integer toStatusCode;

    private String fromName;

    private String toName;

    private String actionCode;

    private String actionName;

    private String allowRoles;

    private Integer remarkRequired;

    private String configKey;

    private Integer enabled;

    private Integer sort;
}
