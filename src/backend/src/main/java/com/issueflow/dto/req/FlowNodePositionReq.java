package com.issueflow.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 流程节点坐标批量更新请求（画布拖拽后一次性持久化）
 */
@Data
public class FlowNodePositionReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 待更新的节点坐标列表 */
    @Valid
    @NotEmpty(message = "坐标列表不能为空")
    private List<PositionItem> positions;

    /**
     * 单个节点坐标项
     */
    @Data
    public static class PositionItem implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 节点 id */
        @NotNull(message = "节点 id 不能为空")
        private Long id;

        /** 坐标 X */
        private Integer posX;

        /** 坐标 Y */
        private Integer posY;
    }
}
