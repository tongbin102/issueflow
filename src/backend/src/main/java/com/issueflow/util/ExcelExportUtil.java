package com.issueflow.util;

import com.alibaba.excel.EasyExcel;
import com.issueflow.dto.resp.DashboardVO;
import com.issueflow.dto.resp.IssueVO;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Excel 导出工具（EasyExcel，内存字节数组）：看板统计 + 问题列表
 */
public final class ExcelExportUtil {

    private ExcelExportUtil() {
    }

    /** 空值占位符，避免 EasyExcel 写 null 造成列错位 */
    private static final String EMPTY = "";

    /**
     * 将问题列表导出为 Excel 字节数组（Phase7-R6⑦：含「来源」「优先级」两列，值为中文名而非编码）。
     *
     * @param rows 问题视图列表，可为空
     * @return xlsx 文件字节数组
     */
    public static byte[] exportIssues(List<IssueVO> rows) {
        List<List<String>> head = new ArrayList<>();
        head.add(List.of("编号"));
        head.add(List.of("标题"));
        head.add(List.of("类型"));
        head.add(List.of("来源"));
        head.add(List.of("优先级"));
        head.add(List.of("严重等级"));
        head.add(List.of("状态"));
        head.add(List.of("项目"));
        head.add(List.of("模块"));
        head.add(List.of("提交者"));
        head.add(List.of("指派人"));
        head.add(List.of("应用版本"));
        head.add(List.of("标签"));
        head.add(List.of("创建时间"));
        head.add(List.of("关闭时间"));

        List<List<Object>> data = new ArrayList<>();
        if (rows != null) {
            for (IssueVO vo : rows) {
                List<Object> row = new ArrayList<>();
                row.add(nvl(vo.getIssueNo()));
                row.add(nvl(vo.getTitle()));
                row.add(nvl(vo.getTypeName()));
                // 来源：优先取回填后的名称，未命中时退回编码，绝不输出 null
                row.add(pick(vo.getSourceDesc(), vo.getSource()));
                row.add(nvl(vo.getPriorityDesc()));
                row.add(nvl(vo.getSeverityDesc()));
                row.add(nvl(vo.getStatusDesc()));
                row.add(nvl(vo.getProjectName()));
                row.add(nvl(vo.getModulePath()));
                row.add(nvl(vo.getReporterName()));
                row.add(nvl(vo.getAssigneeName()));
                row.add(nvl(vo.getEnvAppVersion()));
                row.add(nvl(vo.getTags()));
                row.add(vo.getCreatedAt() == null ? EMPTY : vo.getCreatedAt().toString());
                row.add(vo.getClosedAt() == null ? EMPTY : vo.getClosedAt().toString());
                data.add(row);
            }
        }

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        EasyExcel.write(os).head(head).sheet("问题列表").doWrite(data);
        return os.toByteArray();
    }

    /**
     * null / 空白安全转换。
     *
     * @param value 原值
     * @return 非 null 字符串
     */
    private static String nvl(String value) {
        return value == null ? EMPTY : value;
    }

    /**
     * 取首个非空值。
     *
     * @param first    首选值
     * @param fallback 兜底值
     * @return 非 null 字符串
     */
    private static String pick(String first, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return fallback == null ? EMPTY : fallback;
    }

    /**
     * 将看板关键指标导出为 Excel 字节数组
     *
     * @param vo 看板聚合数据
     * @return xlsx 文件字节数组
     */
    public static byte[] export(DashboardVO vo) {
        List<List<String>> head = new ArrayList<>();
        head.add(List.of("类别"));
        head.add(List.of("名称/状态"));
        head.add(List.of("数量/值"));

        List<List<Object>> data = new ArrayList<>();

        if (vo.getStatusDistribution() != null) {
            for (Map<String, Object> m : vo.getStatusDistribution()) {
                data.add(List.of("状态分布", String.valueOf(m.get("status")), String.valueOf(m.get("count"))));
            }
        }
        if (vo.getTrendByDay() != null) {
            for (Map<String, Object> m : vo.getTrendByDay()) {
                data.add(List.of("每日趋势", String.valueOf(m.get("day")), String.valueOf(m.get("count"))));
            }
        }
        if (vo.getSeverityRatio() != null) {
            for (Map<String, Object> m : vo.getSeverityRatio()) {
                data.add(List.of("严重占比", String.valueOf(m.get("severity")), String.valueOf(m.get("count"))));
            }
        }
        data.add(List.of("平均解决周期(小时)", "-", String.valueOf(vo.getAvgResolveCycle())));
        data.add(List.of("解决率", "-", String.valueOf(vo.getResolveRate())));
        data.add(List.of("问题总数", "-", String.valueOf(vo.getTotal())));
        data.add(List.of("已关闭数", "-", String.valueOf(vo.getClosedTotal())));

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        EasyExcel.write(os).head(head).sheet("看板统计").doWrite(data);
        return os.toByteArray();
    }
}
