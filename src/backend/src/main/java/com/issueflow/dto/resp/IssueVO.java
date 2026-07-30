package com.issueflow.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 问题列表视图对象
 */
@Data
public class IssueVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 问题编号 IS-YYYYMMDD-0001 */
    private String issueNo;

    private String title;

    /** 严重等级 code */
    private Integer severity;

    /** 严重等级描述 */
    private String severityDesc;

    /** 状态 code */
    private Integer status;

    /** 状态描述 */
    private String statusDesc;

    /** 标签（逗号分隔） */
    private String tags;

    /** 应用版本 */
    private String envAppVersion;

    private Long reporterId;

    private String reporterName;

    private Long assigneeId;

    private String assigneeName;

    /** 关联项目 id */
    private Long projectId;

    /** 关联项目名称（由 ProjectService.nameMap 回显） */
    private String projectName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime closedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
