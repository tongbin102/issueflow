package com.issueflow.util;

import com.alibaba.excel.EasyExcel;
import com.issueflow.dto.resp.DashboardVO;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 看板数据 Excel 导出工具（EasyExcel，内存字节数组）
 */
public final class ExcelExportUtil {

    private ExcelExportUtil() {
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
                data.add(List.of("状态分布", String.valueOf(m.get("status")), String.valueOf(m.get("cnt"))));
            }
        }
        if (vo.getTrendByDay() != null) {
            for (Map<String, Object> m : vo.getTrendByDay()) {
                data.add(List.of("每日趋势", String.valueOf(m.get("day")), String.valueOf(m.get("cnt"))));
            }
        }
        if (vo.getSeverityRatio() != null) {
            for (Map<String, Object> m : vo.getSeverityRatio()) {
                data.add(List.of("严重占比", String.valueOf(m.get("severity")), String.valueOf(m.get("cnt"))));
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
