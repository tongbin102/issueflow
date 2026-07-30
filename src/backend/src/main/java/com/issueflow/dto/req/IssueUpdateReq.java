package com.issueflow.dto.req;

import lombok.Data;

import java.io.Serializable;

/**
 * 编辑问题请求（仅更新非空字段，不处理状态流转）
 */
@Data
public class IssueUpdateReq implements Serializable {

    private static final long serialVersionUID = 1L;

    private String title;

    private String description;

    private Integer severity;

    private String tags;

    private String reproduceSteps;

    private String envOs;

    private String envBrowser;

    private String envAppVersion;

    private String envDevice;

    private Long assigneeId;

    /** 关联项目 id（非空才更新） */
    private Long projectId;
}
