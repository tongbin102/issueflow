package com.issueflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.issueflow.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程节点：status_code 与 IssueStatusEnum(0-4) 一一对应且唯一
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("flow_node")
public class FlowNode extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 节点名称 */
    private String name;

    /** 节点编码 */
    private String code;

    /** 状态码，与 IssueStatusEnum 对齐（0-4） */
    private Integer statusCode;

    /** 节点类型：1开始 2审核 3结束 */
    private Integer nodeType;

    /** 流程图节点颜色 */
    private String color;

    /** 流程图坐标 X */
    private Integer posX;

    /** 流程图坐标 Y */
    private Integer posY;

    /** 排序号，升序 */
    private Integer sort;

    /** 节点描述 */
    private String description;

    /** 1启用 0禁用 */
    private Integer enabled;
}
