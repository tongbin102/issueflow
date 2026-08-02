package com.issueflow.dto.req;

import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.Data;

import java.io.Serializable;

/**
 * 编辑问题请求（仅更新非空字段，不处理状态流转）
 */
@Data
public class IssueUpdateReq implements Serializable {

    private static final long serialVersionUID = 1L;

    private String title;

    /**
     * 问题类型编码（{@code dict_code='ISSUE_TYPE'} 的 {@code dict_item.item_code}），Phase9 起的主入参。
     * <p>非空才更新；目标字典项必须处于启用状态（等值提交即未变更时放行）。</p>
     */
    private String typeCode;

    private String description;

    private Integer severity;

    private String tags;

    private String reproduceSteps;

    private String envOs;

    private String envBrowser;

    private String envAppVersion;

    private String envDevice;

    private Long assigneeId;

    /**
     * 所属项目 id（Phase8 W2 #6 起必填）。
     * <p>校验层保证非空后，Service 内「非空才更新」的分支等价于始终更新，语义不变。</p>
     */
    @NotNull(message = "所属项目不能为空")
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

    /**
     * 自定义字段值（仅 {@code field_config.is_system=0} 的字段）。
     * <p>key = {@code field_config.code}，value = 原始值。新增/修改走 upsert，空值软删旧值。</p>
     * <p>不加 {@code @NotNull}：允许请求不携带（如仅改标题的局部更新，此时不触发必填校验）。</p>
     */
    private Map<String, Object> customFields;
}
