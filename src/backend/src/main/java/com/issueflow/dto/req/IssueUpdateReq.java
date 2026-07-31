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

    /** 问题类型 id（非空才更新；更新时校验类型必须启用） */
    private Long typeId;

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

    /**
     * 所属模块 id。
     * <p><b>语义特殊</b>：采用「存在即覆盖」——前端编辑时始终携带该字段，
     * 传 null 表示清空模块归属。与 {@link #projectId} 的「非空才更新」不同，切勿套用同一模板。</p>
     */
    private Long moduleId;

    /** 来源编码（dict_item 的 item_code，字典类型 ISSUE_SOURCE；非空才更新） */
    private String source;

    /** 优先级：0高 1中 2低（非空才更新） */
    private Integer priority;
}
