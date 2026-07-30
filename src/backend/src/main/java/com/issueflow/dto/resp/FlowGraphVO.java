package com.issueflow.dto.resp;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 流程可视化图数据：节点 + 流转
 */
@Data
public class FlowGraphVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 节点列表 */
    private List<FlowNodeVO> nodes;

    /** 流转规则列表 */
    private List<FlowTransitionVO> transitions;
}
