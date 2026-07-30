package com.issueflow.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 问题详情视图对象（含附件列表 + 最近操作历史）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class IssueDetailVO extends IssueVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String description;

    private String reproduceSteps;

    private String envOs;

    private String envBrowser;

    private String envDevice;

    /** 附件列表 */
    private List<AttachmentVO> attachments;

    /** 最近操作历史（按时间倒序，最多 20 条） */
    private List<IssueHistoryVO> recentHistory;
}
