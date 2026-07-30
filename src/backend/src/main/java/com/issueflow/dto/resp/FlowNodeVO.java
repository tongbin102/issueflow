package com.issueflow.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 流程节点视图对象
 */
@Data
public class FlowNodeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    private String code;

    private Integer statusCode;

    private Integer nodeType;

    private String color;

    private Integer posX;

    private Integer posY;

    private Integer sort;

    private String description;

    private Integer enabled;

    /** 状态中文描述（来自 IssueStatusEnum） */
    private String statusDesc;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
