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

    /** 问题类型 id 筛选（Q6：筛选下拉含停用项，停用项由前端追加「(已停用)」标记） */
    private Long typeId;

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

    /** 来源编码筛选（dict_item 的 item_code，字典类型 ISSUE_SOURCE） */
    private String source;

    /** 优先级筛选：0高 1中 2低 */
    private Integer priority;

    /** 关键词（标题/描述模糊匹配） */
    private String keyword;

    /** 起始日期 yyyy-MM-dd（按 created_at） */
    private String startDate;

    /** 结束日期 yyyy-MM-dd（按 created_at） */
    private String endDate;

    /**
     * 数据范围口径（BUG-03）：{@code mine} 仅看自己提交的 / {@code all} 看全站，默认 {@code all}。
     *
     * <p>历史缺陷：前端「我的问题」一直在发 {@code scope=mine}，但本 DTO 无该字段，
     * Spring 参数绑定静默丢弃，导致管理员进入「我的问题」实际看到全站数据。</p>
     *
     * <p>生效规则（见 {@code IssueService#pageQuery}）：</p>
     * <ul>
     *   <li>SUBMITTER 角色恒被收窄为仅看自己，与本字段无关（安全底线）；</li>
     *   <li>非 ADMIN 用户传 {@code mine} → 追加 reporter_id = 当前用户；</li>
     *   <li>ADMIN 传 {@code mine} → 视为看全站，不加过滤（保留管理员全局排障能力）。</li>
     * </ul>
     */
    private String scope = "all";
}
