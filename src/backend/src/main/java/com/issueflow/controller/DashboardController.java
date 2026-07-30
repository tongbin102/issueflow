package com.issueflow.controller;

import com.issueflow.common.Result;
import com.issueflow.dto.req.DashboardQueryReq;
import com.issueflow.dto.resp.DashboardVO;
import com.issueflow.service.DashboardService;
import com.issueflow.util.SecurityUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 看板控制器：统计数据 + Excel 导出
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * 看板概览（5 项指标聚合）
     */
    @GetMapping("/overview")
    public Result<DashboardVO> overview(DashboardQueryReq req) {
        return Result.success(dashboardService.overview(req,
                SecurityUtils.getCurrentUserId(), SecurityUtils.getCurrentRoleCode()));
    }

    /**
     * 导出看板数据为 Excel（文件流）
     */
    @GetMapping("/export")
    public void export(DashboardQueryReq req, HttpServletResponse response) throws IOException {
        byte[] data = dashboardService.export(req,
                SecurityUtils.getCurrentUserId(), SecurityUtils.getCurrentRoleCode());
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename=\"dashboard.xlsx\"");
        response.setContentLength(data.length);
        try (OutputStream out = response.getOutputStream()) {
            out.write(data);
            out.flush();
        }
    }
}
