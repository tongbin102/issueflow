package com.issueflow.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 状态流转请求
 */
@Data
public class StatusChangeReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标状态 code（见 IssueStatusEnum） */
    @NotNull(message = "目标状态不能为空")
    private Integer toStatus;

    /** 流转备注 / 回退原因 */
    private String remark;
}
