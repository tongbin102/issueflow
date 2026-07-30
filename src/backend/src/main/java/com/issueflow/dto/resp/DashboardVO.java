package com.issueflow.dto.resp;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 看板聚合视图对象
 */
@Data
public class DashboardVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态分布：[{status, cnt}] */
    private List<Map<String, Object>> statusDistribution;

    /** 每日趋势：[{day, cnt}] */
    private List<Map<String, Object>> trendByDay;

    /** 平均解决周期（小时，仅 CLOSED） */
    private Double avgResolveCycle;

    /** 解决率（已关闭 / 总数，0~1） */
    private Double resolveRate;

    /** 严重等级占比：[{severity, cnt}] */
    private List<Map<String, Object>> severityRatio;

    /** 总数 */
    private Long total;

    /** 已关闭数 */
    private Long closedTotal;
}
