package com.issueflow.dto.req;

import jakarta.validation.constraints.NotBlank;
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

    /** 关联项目 id（可空） */
    private Long projectId;
}
