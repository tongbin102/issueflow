package com.issueflow.service;

import com.issueflow.common.Constants;
import com.issueflow.dto.req.DashboardQueryReq;
import com.issueflow.dto.resp.DashboardVO;
import com.issueflow.mapper.IssueMapper;
import com.issueflow.util.DateTimeUtils;
import com.issueflow.util.ExcelExportUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 看板统计服务：聚合 5 项指标 + Excel 导出
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final IssueMapper issueMapper;

    /**
     * 组装看板数据（ADMIN 统计全站，其余角色仅统计自己提交的问题）
     */
    public DashboardVO overview(DashboardQueryReq req, Long currentUser, String roleCode) {
        LocalDateTime start = DateTimeUtils.parseDate(req.getStart(), true);
        LocalDateTime end = DateTimeUtils.parseDate(req.getEnd(), false);
        // BUG-03 口径对齐（真·我的）：ADMIN 看全站，其余角色（SUBMITTER/DEVELOPER/TESTER）只看自己提交的，与列表页一致
        Long reporterId = Constants.ROLE_ADMIN.equals(roleCode) ? null : currentUser;
        String version = (req.getVersion() != null && !req.getVersion().isBlank()) ? req.getVersion() : null;

        DashboardVO vo = new DashboardVO();
        vo.setStatusDistribution(issueMapper.statusDistribution(reporterId, version, start, end));
        vo.setTrendByDay(issueMapper.trendByDay(reporterId, version, start, end));
        vo.setSeverityRatio(issueMapper.severityRatio(reporterId, version, start, end));

        BigDecimal avg = issueMapper.avgResolveCycle(reporterId, version, start, end);
        vo.setAvgResolveCycle(avg == null ? 0.0 : avg.doubleValue());

        Long total = issueMapper.countTotal(reporterId, version, start, end);
        Long closed = issueMapper.countClosed(reporterId, version, start, end);
        vo.setTotal(total == null ? 0L : total);
        vo.setClosedTotal(closed == null ? 0L : closed);
        double rate = (total != null && total > 0 && closed != null)
                ? closed.doubleValue() / total.doubleValue() : 0.0;
        vo.setResolveRate(rate);

        return vo;
    }

    /**
     * 导出看板数据为 Excel 字节数组
     */
    public byte[] export(DashboardQueryReq req, Long currentUser, String roleCode) {
        DashboardVO vo = overview(req, currentUser, roleCode);
        return ExcelExportUtil.export(vo);
    }
}
