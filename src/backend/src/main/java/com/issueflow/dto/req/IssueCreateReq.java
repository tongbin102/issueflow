package com.issueflow.dto.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
     * <p>必须为启用状态的字典项。</p>
     * <p><b>【需求一】</b>由用户显式选择，前端不再预选任何默认类型，故此处提升为强校验；
     * 服务端 {@code IssueService#create} 仍保留 {@code ISSUE_TYPE_NOT_FOUND} 兜底分支。</p>
     */
    @NotBlank(message = "请选择问题类型")
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

    /**
     * 来源编码（dict_item 的 item_code，字典类型 ISSUE_SOURCE）。
     *
     * <p><b>【需求一】该入参已失效</b>：来源在 UI 上固定为「系统录入」且只读，
     * {@code IssueService#create} 会无条件强制覆写为 {@code Constants.DICT_ITEM_SOURCE_SYSTEM}。
     * 字段保留仅为兼容老客户端的请求体结构，传任何值都会被忽略，不做启用性校验。</p>
     *
     * @deprecated 服务端强制固定为 SYSTEM，新调用方无需传递
     */
    @Deprecated
    private String source;

    /**
     * 优先级：0高 1中 2低（见 {@code PriorityEnum}）。
     *
     * <p><b>【需求一 · 默认值红线】</b>必须由用户显式选择，服务端<b>不再</b>兜底为 1（中）。
     * 为空直接 400，避免"没人选过"的问题被统计成中优先级导致报表失真。</p>
     */
    @NotNull(message = "请选择优先级")
    @Min(value = 0, message = "优先级取值非法")
    @Max(value = 2, message = "优先级取值非法")
    private Integer priority;

    /**
     * 自定义字段值（仅 {@code field_config.is_system=0} 的字段）。
     * <p>key = {@code field_config.code}，value = 原始值（TEXT/DICT/REF 为字符串，NUMBER 为数字，
     * DATE/DATETIME 为 ISO 字符串）。由 {@code IssueFieldValueService} 落库，内置字段不进此 Map。</p>
     * <p>不加 {@code @NotNull}：允许请求不携带（局部更新场景）。</p>
     */
    private Map<String, Object> customFields;
}
