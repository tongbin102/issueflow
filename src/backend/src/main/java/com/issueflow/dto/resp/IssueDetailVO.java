package com.issueflow.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 问题详情视图对象（含附件列表 + 最近操作历史）
 *
 * <p>注：{@code moduleId} / {@code modulePath} 由父类 {@link IssueVO} 提供，
 * 详情接口同样输出这两个字段（不在此重复声明，避免字段遮蔽导致序列化歧义）。</p>
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

    /**
     * 自定义字段值（仅 {@code field_config.is_system=0} 的字段，按 {@code field_config.type} 从对应列取出真实值）。
     * <p>key = {@code field_config.code}，value = 真实值（NUMBER→数字，DATE/DATETIME→ISO 字符串，TEXT/DICT/REF→字符串）。</p>
     */
    private Map<String, Object> customFields;
}
