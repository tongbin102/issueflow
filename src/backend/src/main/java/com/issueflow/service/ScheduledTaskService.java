package com.issueflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.issueflow.common.BizException;
import com.issueflow.common.Constants;
import com.issueflow.common.PageResult;
import com.issueflow.common.ResultCode;
import com.issueflow.config.DynamicTaskScheduler;
import com.issueflow.dto.req.ScheduledTaskReq;
import com.issueflow.dto.resp.JobOptionVO;
import com.issueflow.dto.resp.ScheduledTaskLogVO;
import com.issueflow.dto.resp.ScheduledTaskVO;
import com.issueflow.entity.ScheduledTask;
import com.issueflow.entity.ScheduledTaskLog;
import com.issueflow.enums.EnableStatusEnum;
import com.issueflow.mapper.ScheduledTaskLogMapper;
import com.issueflow.mapper.ScheduledTaskMapper;
import com.issueflow.util.CronUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 定时任务服务（Phase 7）。
 *
 * <p>职责边界：本类只管<b>元数据的增删改查与校验</b>，真正的调度、执行、
 * 日志落库全部委托 {@link DynamicTaskScheduler}（ARCH §1.3.3 决策 C）。</p>
 *
 * <p>两条硬校验，任何写入路径都不得绕过：</p>
 * <ol>
 *   <li>{@code cron} 必须通过 {@link CronUtils#isValid(String)}（Spring 原生 6 位表达式）；</li>
 *   <li>{@code jobKey} 必须命中 {@code jobRegistry} 白名单 —— <b>禁止任何类名反射</b>（安全红线）。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledTaskService {

    private final ScheduledTaskMapper scheduledTaskMapper;
    private final ScheduledTaskLogMapper scheduledTaskLogMapper;
    private final DynamicTaskScheduler dynamicTaskScheduler;
    private final PermissionService permissionService;

    // ============================ 查询 ============================

    /**
     * 任务分页列表（权限 {@code job:list}）。
     *
     * <p>{@code nextExecTime} 由 cron 实时计算，不落库（ARCH §3.5）。</p>
     *
     * @param pageNum 页码，从 1 开始
     * @param size    每页大小
     * @param keyword 关键词（任务名 / jobKey / 分组），可空
     * @param status  状态过滤 1 运行 / 0 暂停，可空
     * @return 分页结果
     */
    public PageResult<ScheduledTaskVO> pageQuery(int pageNum, int size, String keyword, Integer status) {
        permissionService.requirePermission("job:list");
        int current = pageNum < 1 ? Constants.DEFAULT_PAGE : pageNum;
        int pageSize = size < 1 ? Constants.DEFAULT_SIZE : size;

        Page<ScheduledTask> page = new Page<>(current, pageSize);
        LambdaQueryWrapper<ScheduledTask> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(q -> q.like(ScheduledTask::getTaskName, kw)
                    .or().like(ScheduledTask::getJobKey, kw)
                    .or().like(ScheduledTask::getTaskGroup, kw));
        }
        if (status != null) {
            wrapper.eq(ScheduledTask::getStatus, status);
        }
        wrapper.orderByAsc(ScheduledTask::getTaskGroup).orderByAsc(ScheduledTask::getId);
        scheduledTaskMapper.selectPage(page, wrapper);

        List<ScheduledTaskVO> list = new ArrayList<>(page.getRecords().size());
        for (ScheduledTask row : page.getRecords()) {
            list.add(toVO(row));
        }
        return PageResult.of(list, page.getTotal(), (long) current, (long) pageSize);
    }

    /**
     * 任务列表（不分页，按分组、id 升序）。
     *
     * @return 任务实体列表
     */
    public List<ScheduledTask> list() {
        permissionService.requirePermission("job:list");
        return scheduledTaskMapper.selectList(new LambdaQueryWrapper<ScheduledTask>()
                .orderByAsc(ScheduledTask::getTaskGroup).orderByAsc(ScheduledTask::getId));
    }

    /**
     * 可选执行目标下拉（权限 {@code job:list}）。
     *
     * @return jobRegistry 白名单选项
     */
    public List<JobOptionVO> options() {
        permissionService.requirePermission("job:list");
        return dynamicTaskScheduler.jobOptions();
    }

    /**
     * 按 id 查询任务详情（权限 {@code job:list}）。
     *
     * @param id 任务 id
     * @return 视图对象
     */
    public ScheduledTaskVO detail(Long id) {
        permissionService.requirePermission("job:list");
        return toVO(requireExists(id));
    }

    /**
     * 按 id 查询任务实体（无权限校验，内部使用）。
     *
     * @param id 任务 id
     * @return 任务实体
     */
    public ScheduledTask getById(Long id) {
        return requireExists(id);
    }

    /**
     * 任务执行日志分页（权限 {@code job:list}）。
     *
     * @param taskId  任务 id
     * @param pageNum 页码
     * @param size    每页大小
     * @return 分页结果
     */
    public PageResult<ScheduledTaskLogVO> logPage(Long taskId, int pageNum, int size) {
        permissionService.requirePermission("job:list");
        requireExists(taskId);
        int current = pageNum < 1 ? Constants.DEFAULT_PAGE : pageNum;
        int pageSize = size < 1 ? Constants.DEFAULT_SIZE : size;

        Page<ScheduledTaskLog> page = new Page<>(current, pageSize);
        scheduledTaskLogMapper.selectPage(page, new LambdaQueryWrapper<ScheduledTaskLog>()
                .eq(ScheduledTaskLog::getTaskId, taskId)
                .orderByDesc(ScheduledTaskLog::getStartTime)
                .orderByDesc(ScheduledTaskLog::getId));

        List<ScheduledTaskLogVO> list = new ArrayList<>(page.getRecords().size());
        for (ScheduledTaskLog row : page.getRecords()) {
            ScheduledTaskLogVO vo = new ScheduledTaskLogVO();
            vo.setId(row.getId());
            vo.setStartTime(row.getStartTime());
            vo.setCostMs(row.getCostMs());
            vo.setSuccess(row.getSuccess() != null && row.getSuccess() == 1);
            vo.setMessage(row.getMessage());
            vo.setTriggerType(row.getTriggerType());
            list.add(vo);
        }
        return PageResult.of(list, page.getTotal(), (long) current, (long) pageSize);
    }

    // ============================ 写入 ============================

    /**
     * 新增任务（权限 {@code job:create}）；{@code status=1} 时立即注册到调度池。
     *
     * @param req 任务请求
     * @return 新任务 id
     */
    @Transactional
    public Long create(ScheduledTaskReq req) {
        permissionService.requirePermission("job:create");
        validate(req);
        ScheduledTask entity = new ScheduledTask();
        applyReq(entity, req);
        scheduledTaskMapper.insert(entity);
        dynamicTaskScheduler.refresh(entity.getId());
        return entity.getId();
    }

    /**
     * 编辑任务（权限 {@code job:update}）；保存后按最新 cron / 状态重新注册。
     *
     * @param id  任务 id
     * @param req 任务请求
     */
    @Transactional
    public void update(Long id, ScheduledTaskReq req) {
        permissionService.requirePermission("job:update");
        ScheduledTask entity = requireExists(id);
        validate(req);
        applyReq(entity, req);
        scheduledTaskMapper.updateById(entity);
        dynamicTaskScheduler.refresh(id);
    }

    /**
     * 删除任务（权限 {@code job:delete}）：先取消注册再软删。
     *
     * <p>内置示例任务<b>允许删除</b>（ARCH §3.8），删除后重跑迁移脚本即可恢复。</p>
     *
     * @param id 任务 id
     */
    @Transactional
    public void delete(Long id) {
        permissionService.requirePermission("job:delete");
        requireExists(id);
        dynamicTaskScheduler.cancel(id);
        scheduledTaskMapper.deleteById(id);
    }

    /**
     * 启停切换（权限 {@code job:update}）。
     *
     * @param id     任务 id
     * @param status 1 运行 / 0 暂停
     */
    @Transactional
    public void toggleStatus(Long id, Integer status) {
        permissionService.requirePermission("job:update");
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException(ResultCode.VALID_ERROR, "状态只能是 0（暂停）或 1（运行）");
        }
        ScheduledTask task = requireExists(id);
        task.setStatus(status);
        scheduledTaskMapper.updateById(task);
        dynamicTaskScheduler.refresh(id);
    }

    /**
     * 立即执行一次（权限 {@code job:run}，{@code triggerType=MANUAL}）。
     *
     * <p>由 Redis {@code SETNX} 互斥，重复点击第二次直接返回「正在执行中」。</p>
     *
     * @param id 任务 id
     */
    public void runOnce(Long id) {
        permissionService.requirePermission("job:run");
        requireExists(id);
        dynamicTaskScheduler.runOnce(id);
    }

    // ============================ 私有方法 ============================

    /**
     * 校验请求：cron 合法 + jobKey 命中白名单。
     *
     * @param req 任务请求
     */
    private void validate(ScheduledTaskReq req) {
        if (!CronUtils.isValid(req.getCron())) {
            throw new BizException(ResultCode.VALID_ERROR,
                    "cron 表达式非法（需为 Spring 6 位格式，如 0 0 3 * * ?）：" + req.getCron());
        }
        if (!dynamicTaskScheduler.isValidJobKey(req.getJobKey())) {
            throw new BizException(ResultCode.VALID_ERROR,
                    "执行目标不在白名单内：" + req.getJobKey());
        }
        if (req.getStatus() != null && req.getStatus() != 0 && req.getStatus() != 1) {
            throw new BizException(ResultCode.VALID_ERROR, "状态只能是 0（暂停）或 1（运行）");
        }
    }

    /**
     * 请求体字段写入实体。
     *
     * @param entity 目标实体
     * @param req    请求体
     */
    private void applyReq(ScheduledTask entity, ScheduledTaskReq req) {
        entity.setTaskName(req.getTaskName().trim());
        entity.setTaskGroup(req.getTaskGroup() == null || req.getTaskGroup().isBlank()
                ? "default" : req.getTaskGroup().trim());
        entity.setJobKey(req.getJobKey().trim());
        entity.setCron(req.getCron().trim());
        entity.setParams(req.getParams());
        entity.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        entity.setDescription(req.getDescription());
    }

    /**
     * 实体转 VO（回填 jobName 与实时 nextExecTime）。
     *
     * @param row 任务实体
     * @return 视图对象
     */
    private ScheduledTaskVO toVO(ScheduledTask row) {
        ScheduledTaskVO vo = new ScheduledTaskVO();
        vo.setId(row.getId());
        vo.setTaskName(row.getTaskName());
        vo.setTaskGroup(row.getTaskGroup());
        vo.setJobKey(row.getJobKey());
        vo.setJobName(dynamicTaskScheduler.displayNameOf(row.getJobKey()));
        vo.setCron(row.getCron());
        vo.setParams(row.getParams());
        vo.setStatus(row.getStatus());
        vo.setDescription(row.getDescription());
        vo.setLastExecTime(row.getLastExecTime());
        vo.setLastExecResult(row.getLastExecResult());
        vo.setLastCostMs(row.getLastCostMs());
        // 仅「运行中」的任务才有下次执行时间，暂停态返回 null 避免前端误导
        vo.setNextExecTime(EnableStatusEnum.isEnabled(row.getStatus())
                ? CronUtils.nextExecTime(row.getCron()) : null);
        return vo;
    }

    /**
     * 存在性断言。
     *
     * @param id 任务 id
     * @return 任务实体
     */
    private ScheduledTask requireExists(Long id) {
        ScheduledTask task = id == null ? null : scheduledTaskMapper.selectById(id);
        if (task == null) {
            throw new BizException(ResultCode.NOT_FOUND, "定时任务不存在");
        }
        return task;
    }
}
