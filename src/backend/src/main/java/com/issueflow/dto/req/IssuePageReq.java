package com.issueflow.dto.req;

import lombok.Data;

import java.io.Serializable;

/**
 * 问题分页 + 多条件筛选请求
 */
@Data
public class IssuePageReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页，默认 1 */
    private Integer page = 1;

    /** 每页大小，默认 10 */
    private Integer size = 10;

    /** 状态筛选 */
    private Integer status;

    /** 严重等级筛选 */
    private Integer severity;

    /** 标签名称筛选（模糊匹配逗号分隔串） */
    private String tag;

    /** 应用版本筛选（精确匹配 env_app_version） */
    private String version;

    /** 处理人 id 筛选 */
    private Long assigneeId;

    /** 提交者 id 筛选 */
    private Long reporterId;

    /** 关联项目 id 筛选 */
    private Long projectId;

    /** 关键词（标题/描述模糊匹配） */
    private String keyword;

    /** 起始日期 yyyy-MM-dd（按 created_at） */
    private String startDate;

    /** 结束日期 yyyy-MM-dd（按 created_at） */
    private String endDate;
}
