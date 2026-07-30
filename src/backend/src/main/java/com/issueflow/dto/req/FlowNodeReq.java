package com.issueflow.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 流程节点新建/编辑请求
 */
@Data
public class FlowNodeReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 节点名称 */
    @NotBlank(message = "节点名称不能为空")
    private String name;

    /** 节点编码 */
    private String code;

    /** 状态码（0-4），与 IssueStatusEnum 对应且唯一 */
    @NotNull(message = "状态码不能为空")
    private Integer statusCode;

    /** 节点类型 1开始 2审核 3结束 */
    private Integer nodeType;

    /** 颜色 */
    private String color;

    /** 坐标 X */
    private Integer posX;

    /** 坐标 Y */
    private Integer posY;

    /** 排序号 */
    private Integer sort;

    /** 描述 */
    private String description;

    /** 1启用 0禁用 */
    private Integer enabled;
}
