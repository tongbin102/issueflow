package com.issueflow.dto.resp;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 看板聚合视图对象
 *
 * <p>BUG-01 契约：三个聚合列表的「数量」键统一为 {@code count}（历史别名 {@code cnt} 已废弃），
 * 与前端 UserDashboard / UserStats / admin Dashboard / FlowMonitor / TrendChart /
 * DistributionChart 的 {@code d.count} 读取口径严格一致，改动别名须同步前端。</p>
 */
@Data
public class DashboardVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态分布：[{status, count}] */
    private List<Map<String, Object>> statusDistribution;

    /** 每日趋势：[{day, count}] */
    private List<Map<String, Object>> trendByDay;

    /** 平均解决周期（小时，仅 CLOSED） */
    private Double avgResolveCycle;

    /** 解决率（已关闭 / 总数，0~1） */
    private Double resolveRate;

    /** 严重等级占比：[{severity, count}] */
    private List<Map<String, Object>> severityRatio;

    /** 总数 */
    private Long total;

    /** 已关闭数 */
    private Long closedTotal;
}
