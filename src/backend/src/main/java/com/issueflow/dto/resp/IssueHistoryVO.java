package com.issueflow.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作历史视图对象（含操作人姓名与状态描述）
 */
@Data
public class IssueHistoryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long issueId;

    /** 动作 code（CREATE/CLAIM/...） */
    private String action;

    /** 动作描述 */
    private String actionDesc;

    private Integer fromStatus;

    private String fromStatusDesc;

    private Integer toStatus;

    private String toStatusDesc;

    private Long operatorId;

    /** 操作人姓名（LEFT JOIN user 取 real_name / username） */
    private String operatorName;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
