package com.issueflow.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.Data;

import java.io.Serializable;

/**
 * 新建问题请求
 * <p>提交者固定为当前登录用户；附件通过 multipart 的 files 部分上传。</p>
 */
@Data
public class IssueCreateReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 标题 */
    @NotBlank(message = "标题不能为空")
    private String title;

    /**
     * 问题类型编码（{@code dict_code='ISSUE_TYPE'} 的 {@code dict_item.item_code}），Phase9 起的主入参。
     * <p>必须为启用状态的字典项；为空时由 {@code IssueService#createIssue} 抛 {@code ISSUE_TYPE_NOT_FOUND}。</p>
     */
    private String typeCode;

    /** 详细描述 */
    private String description;

    /** 严重等级：0致命 1严重 2一般 3轻微（默认 2 一般） */
    private Integer severity = 2;

    /** 标签（逗号分隔名称） */
    private String tags;

    /** 复现步骤 */
    private String reproduceSteps;

    /** 操作系统 */
    private String envOs;

    /** 浏览器 */
    private String envBrowser;

    /** 应用版本 */
    private String envAppVersion;

    /** 设备型号 */
    private String envDevice;

    /** 处理人/认领人 id（可空，由开发人员认领） */
    private Long assigneeId;

    /** 所属项目 id（Phase8 W2 #6 起必填） */
    @NotNull(message = "所属项目不能为空")
    private Long projectId;

    /** 所属模块 id（可空；非空时须属于 projectId 对应项目） */
    private Long moduleId;

    /** 来源编码（dict_item 的 item_code，字典类型 ISSUE_SOURCE；为空时服务端兜底为 SYSTEM） */
    private String source;

    /** 优先级：0高 1中 2低（为空时服务端兜底为 1=中，见 PriorityEnum） */
    private Integer priority;

    /**
     * 自定义字段值（仅 {@code field_config.is_system=0} 的字段）。
     * <p>key = {@code field_config.code}，value = 原始值（TEXT/DICT/REF 为字符串，NUMBER 为数字，
     * DATE/DATETIME 为 ISO 字符串）。由 {@code IssueFieldValueService} 落库，内置字段不进此 Map。</p>
     * <p>不加 {@code @NotNull}：允许请求不携带（局部更新场景）。</p>
     */
    private Map<String, Object> customFields;
}
