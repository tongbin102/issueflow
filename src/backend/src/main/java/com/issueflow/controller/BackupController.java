package com.issueflow.controller;

import com.issueflow.common.Result;
import com.issueflow.dto.req.BackupReq;
import com.issueflow.dto.resp.BackupEstimateVO;
import com.issueflow.service.BackupService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据备份控制器（Phase 7 T8）。
 *
 * <p>路由前缀 {@code /api/admin/backup}（ARCH §3.8），权限码 {@code system:backup:export}。</p>
 *
 * <p><b>响应契约特殊</b>（ARCH §7.1）：导出成功返回
 * {@code application/octet-stream} 二进制流；<b>失败返回 HTTP 200 +
 * {@code application/json} 错误体</b>（由全局异常处理器包装）。
 * 前端 {@code responseType:'blob'} 后<b>必须先判断 Content-Type</b>，
 * 若为 JSON 需 {@code blob.text()} 解析出 message 展示，否则会下载到一个坏文件。</p>
 *
 * @deprecated Phase10 需求三起废弃。本控制器的能力已被
 *             {@link DataManagementController}（前缀 {@code /api/admin/data}）完整覆盖并增强
 *             —— 新模块提供备份记录持久化、列表分页、下载、删除、恢复、上传恢复、
 *             保留策略与任务进度轮询，权限也从粗粒度的 {@code system:backup:export}
 *             细化为 7 个 {@code system:data:*} 权限码。
 *             <p>前端唯一调用方 {@code src/api/backup.js} 已同批次下线，本控制器现已无调用方。
 *             之所以暂不删除：{@code /api/admin/backup/**} 属对外 HTTP 契约，
 *             可能仍有运维脚本或历史集成在直连，需走一个废弃公告周期后再移除。
 *             <b>请勿在此新增端点。</b></p>
 */
@Deprecated
@RestController
@RequestMapping("/api/admin/backup")
@RequiredArgsConstructor
public class BackupController {

    private final BackupService backupService;

    /**
     * 导出前预估（{@code system:backup:export}）。
     *
     * @param scope 范围 ALL 全量 / CORE 核心配置，默认 CORE
     * @return 预估视图：表数、总条数、逐表条数、建议文件名、超限告警
     */
    @GetMapping("/estimate")
    public Result<BackupEstimateVO> estimate(@RequestParam(defaultValue = "CORE") String scope) {
        return Result.success(backupService.estimate(scope));
    }

    /**
     * 执行导出（{@code system:backup:export}）。
     *
     * <p>先写系统临时文件，全部成功后才回传，保证不产生半截损坏文件。</p>
     *
     * @param req      导出请求（scope + format）
     * @param response HTTP 响应（成功时直接写二进制流）
     */
    @PostMapping("/export")
    public void export(@Valid @RequestBody BackupReq req, HttpServletResponse response) {
        backupService.export(req, response);
    }
}
