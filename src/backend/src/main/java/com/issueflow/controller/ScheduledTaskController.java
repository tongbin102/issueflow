package com.issueflow.controller;

import com.issueflow.common.PageResult;
import com.issueflow.common.Result;
import com.issueflow.dto.req.ScheduledTaskReq;
import com.issueflow.dto.req.StatusToggleReq;
import com.issueflow.dto.resp.JobOptionVO;
import com.issueflow.dto.resp.ScheduledTaskLogVO;
import com.issueflow.dto.resp.ScheduledTaskVO;
import com.issueflow.service.ScheduledTaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 定时任务控制器（Phase 7 T7）。
 *
 * <p>路由前缀 {@code /api/admin/jobs}（ARCH §3.8）；权限校验统一在 Service 首行。</p>
 *
 * <p><b>安全红线</b>：{@code jobKey} 必须命中后端 {@code jobRegistry} 白名单，
 * 保存时由 {@code ScheduledTaskService.validate} 硬拦截 —— 接口<b>不接受任何类名</b>，
 * 也不存在反射执行路径。</p>
 */
@RestController
@RequestMapping("/api/admin/jobs")
@RequiredArgsConstructor
public class ScheduledTaskController {

    private final ScheduledTaskService scheduledTaskService;

    /**
     * 任务分页列表（{@code job:list}）；{@code nextExecTime} 由 cron 实时计算。
     *
     * @param page    页码，默认 1
     * @param size    每页大小，默认 10
     * @param keyword 关键词（任务名 / jobKey / 分组），可空
     * @param status  状态过滤 1 运行 / 0 暂停，可空
     * @return 分页结果
     */
    @GetMapping
    public Result<PageResult<ScheduledTaskVO>> page(@RequestParam(defaultValue = "1") Integer page,
                                                    @RequestParam(defaultValue = "10") Integer size,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) Integer status) {
        return Result.success(scheduledTaskService.pageQuery(page, size, keyword, status));
    }

    /**
     * 可选执行目标下拉（{@code job:list}）：来自 jobRegistry 白名单。
     *
     * @return 选项列表
     */
    @GetMapping("/options")
    public Result<List<JobOptionVO>> options() {
        return Result.success(scheduledTaskService.options());
    }

    /**
     * 新增任务（{@code job:create}）：cron 校验 + jobKey 白名单校验；status=1 时立即注册。
     *
     * @param req 任务请求
     * @return 新任务 id
     */
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ScheduledTaskReq req) {
        return Result.success(scheduledTaskService.create(req));
    }

    /**
     * 编辑任务（{@code job:update}）：保存后按最新 cron / 状态重新注册。
     *
     * @param id  任务 id
     * @param req 任务请求
     * @return 空结果
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ScheduledTaskReq req) {
        scheduledTaskService.update(id, req);
        return Result.success();
    }

    /**
     * 删除任务（{@code job:delete}）：先取消注册再软删；内置示例任务允许删除。
     *
     * @param id 任务 id
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        scheduledTaskService.delete(id);
        return Result.success();
    }

    /**
     * 启停切换（{@code job:update}）：{@code enabled=true} 恢复运行，{@code false} 暂停。
     *
     * @param id  任务 id
     * @param req 启停入参（通用 StatusToggleReq）
     * @return 空结果
     */
    @PutMapping("/{id}/status")
    public Result<Void> toggleStatus(@PathVariable Long id, @Valid @RequestBody StatusToggleReq req) {
        scheduledTaskService.toggleStatus(id, Boolean.TRUE.equals(req.getEnabled()) ? 1 : 0);
        return Result.success();
    }

    /**
     * 立即执行一次（{@code job:run}，{@code triggerType=MANUAL}）。
     *
     * <p>Redis SETNX 互斥防重入：重复点击第二次返回「该任务正在执行中，请稍后再试」。</p>
     *
     * @param id 任务 id
     * @return 空结果
     */
    @PostMapping("/{id}/run")
    public Result<Void> runOnce(@PathVariable Long id) {
        scheduledTaskService.runOnce(id);
        return Result.success();
    }

    /**
     * 任务执行日志分页（{@code job:list}）。
     *
     * @param id   任务 id
     * @param page 页码，默认 1
     * @param size 每页大小，默认 10
     * @return 分页结果
     */
    @GetMapping("/{id}/logs")
    public Result<PageResult<ScheduledTaskLogVO>> logs(@PathVariable Long id,
                                                       @RequestParam(defaultValue = "1") Integer page,
                                                       @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(scheduledTaskService.logPage(id, page, size));
    }
}
