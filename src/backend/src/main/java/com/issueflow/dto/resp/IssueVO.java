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

    /** 问题类型 id */
    private Long typeId;

    /** 问题类型名称（由 IssueTypeService.nameMap 批量回填；停用类型仍正常回显） */
    private String typeName;

    /** 问题类型 code（前端 i18n 枚举文案映射用，如 BUG/FEATURE/OTHER） */
    private String typeCode;

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

    /** 所属模块 id（可空） */
    private Long moduleId;

    /** 所属模块全路径「父 &gt; 子 &gt; 孙」（由 ModuleService.pathMap 批量回填；null 时前端显「—」） */
    private String modulePath;

    /** 来源编码（dict_item 的 item_code，字典类型 ISSUE_SOURCE） */
    private String source;

    /** 来源名称（由 DictService 批量回填） */
    private String sourceDesc;

    /** 优先级 code：0高 1中 2低 */
    private Integer priority;

    /** 优先级描述（由 PriorityEnum 计算） */
    private String priorityDesc;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime closedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
