package com.issueflow.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 问题类型管理列表视图对象
 */
@Data
public class IssueTypeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 类型名称 */
    private String name;

    /** 类型编码 */
    private String code;

    /** 描述 */
    private String description;

    /** 排序号 */
    private Integer sort;

    /** 是否启用 */
    private Boolean enabled;

    /** 引用该类型的问题数量（未删除） */
    private Long issueCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
