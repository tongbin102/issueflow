package com.issueflow.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.issueflow.common.BizException;
import com.issueflow.common.Constants;
import com.issueflow.common.ResultCode;
import com.issueflow.dto.resp.JobOptionVO;
import com.issueflow.entity.ScheduledTask;
import com.issueflow.entity.ScheduledTaskLog;
import com.issueflow.enums.EnableStatusEnum;
import com.issueflow.job.ScheduledJob;
import com.issueflow.mapper.ScheduledTaskLogMapper;
import com.issueflow.mapper.ScheduledTaskMapper;
import com.issueflow.util.CronUtils;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 动态定时任务调度器（ARCH §1.3.3 决策 C）。
 *
 * <p>核心设计：</p>
 * <ol>
 *   <li><b>自持调度池</b>：内部 new 一个 {@link ThreadPoolTaskScheduler}，不去抢
 *       Spring Boot 自动装配的 {@code taskScheduler} Bean —— 避免与 {@code @Scheduled}
 *       共池互相阻塞，也避免容器内多 TaskScheduler Bean 的注入歧义。</li>
 *   <li><b>启动装载走 {@link ApplicationReadyEvent}</b>，不用 {@code @PostConstruct}：
 *       后者阶段数据源 / Mapper 可能尚未就绪（Phase 5 血泪教训）。整体 try/catch，
 *       表不存在时只 warn 不阻断启动。</li>
 *   <li><b>jobKey 白名单</b>：只认 {@link ScheduledJob} 的 Spring Bean 实现，
 *       <b>禁止任何类名反射</b>（安全红线）。</li>
 *   <li><b>并发互斥</b>：执行前 Redis {@code SETNX job:running:{id}}，防止重复点击
 *       「立即执行」或 cron 与手动触发撞车；Redis 不可用时降级为「允许执行」，
 *       监控可用性不该成为业务可用性的单点。</li>
 * </ol>
 *
 * <p><b>单机约束</b>：本调度器为进程内调度，多实例部署会各自注册导致重复触发，
 * 需升级 Quartz 集群模式（ARCH §8.7）。</p>
 */
@Slf4j
@Component
public class DynamicTaskScheduler {

    /** 每个任务保留的执行日志条数 */
    private static final int LOG_KEEP_PER_TASK = 200;

    /** 异常摘要保留的栈帧行数 */
    private static final int STACK_LINES = 5;

    /** scheduled_task_log.message 列长度上限 */
    private static final int MESSAGE_MAX_LENGTH = 2000;

    private final ScheduledTaskMapper scheduledTaskMapper;
    private final ScheduledTaskLogMapper scheduledTaskLogMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    /** jobKey -> Job 实现，构造期由 Spring 注入的实现列表冻结为白名单 */
    private final Map<String, ScheduledJob> jobRegistry = new LinkedHashMap<>();

    /** taskId -> 已注册的调度句柄 */
    private final Map<Long, ScheduledFuture<?>> registry = new ConcurrentHashMap<>();

    /** 内部调度池 */
    private final ThreadPoolTaskScheduler taskScheduler;

    /**
     * 构造调度器并冻结 job 白名单。
     *
     * @param scheduledTaskMapper    任务 Mapper
     * @param scheduledTaskLogMapper 任务日志 Mapper
     * @param redisTemplate          Redis 模板（互斥锁）
     * @param jobs                   Spring 容器内全部 {@link ScheduledJob} 实现
     */
    public DynamicTaskScheduler(ScheduledTaskMapper scheduledTaskMapper,
                                ScheduledTaskLogMapper scheduledTaskLogMapper,
                                RedisTemplate<String, Object> redisTemplate,
                                List<ScheduledJob> jobs) {
        this.scheduledTaskMapper = scheduledTaskMapper;
        this.scheduledTaskLogMapper = scheduledTaskLogMapper;
        this.redisTemplate = redisTemplate;
        if (jobs != null) {
            for (ScheduledJob job : jobs) {
                if (job.jobKey() == null || job.jobKey().isBlank()) {
                    log.warn("[Scheduler] ignore job with blank key: {}", job.getClass().getName());
                    continue;
                }
                ScheduledJob previous = jobRegistry.put(job.jobKey(), job);
                if (previous != null) {
                    log.warn("[Scheduler] duplicated jobKey={}, {} overrides {}",
                            job.jobKey(), job.getClass().getName(), previous.getClass().getName());
                }
            }
        }
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("if-sched-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.initialize();
        this.taskScheduler = scheduler;
        log.info("[Scheduler] job whitelist loaded: {}", jobRegistry.keySet());
    }

    // ============================ 白名单查询 ============================

    /**
     * 判断 jobKey 是否在白名单内。
     *
     * @param jobKey 执行目标 key
     * @return true 合法
     */
    public boolean isValidJobKey(String jobKey) {
        return jobKey != null && jobRegistry.containsKey(jobKey);
    }

    /**
     * 取 jobKey 对应的展示名。
     *
     * @param jobKey 执行目标 key
     * @return 展示名；未命中时回退 jobKey 本身
     */
    public String displayNameOf(String jobKey) {
        ScheduledJob job = jobKey == null ? null : jobRegistry.get(jobKey);
        return job == null ? jobKey : job.displayName();
    }

    /**
     * 可选执行目标下拉。
     *
     * @return 白名单选项列表
     */
    public List<JobOptionVO> jobOptions() {
        List<JobOptionVO> options = new ArrayList<>(jobRegistry.size());
        for (ScheduledJob job : jobRegistry.values()) {
            options.add(new JobOptionVO(job.jobKey(), job.displayName()));
        }
        return options;
    }

    /**
     * 某任务当前是否已注册到调度池。
     *
     * @param taskId 任务 id
     * @return true 已注册
     */
    public boolean isRegistered(Long taskId) {
        return taskId != null && registry.containsKey(taskId);
    }

    // ============================ 装载与注册 ============================

    /**
     * 启动装载：注册全部 {@code status=1} 的任务。
     *
     * <p>整体 try/catch —— 迁移脚本未执行导致表不存在时只告警，绝不阻断应用启动。</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registerAll() {
        try {
            List<ScheduledTask> tasks = scheduledTaskMapper.selectList(
                    new LambdaQueryWrapper<ScheduledTask>()
                            .eq(ScheduledTask::getStatus, EnableStatusEnum.ENABLED.getCode()));
            int ok = 0;
            for (ScheduledTask task : tasks) {
                if (doRegister(task)) {
                    ok++;
                }
            }
            log.info("[Scheduler] startup registered {}/{} enabled tasks", ok, tasks.size());
        } catch (Exception e) {
            log.warn("[Scheduler] startup registration skipped: {} "
                    + "（若为首次部署，请确认已先执行 Phase7 迁移脚本再重启后端）", e.getMessage());
        }
    }

    /**
     * 刷新单个任务的注册状态（新增 / 编辑 / 启停后调用）。
     *
     * @param taskId 任务 id
     */
    public void refresh(Long taskId) {
        cancel(taskId);
        if (taskId == null) {
            return;
        }
        ScheduledTask task = scheduledTaskMapper.selectById(taskId);
        if (task == null || task.getStatus() == null || task.getStatus() != 1) {
            return;
        }
        doRegister(task);
    }

    /**
     * 取消单个任务的注册（删除 / 暂停后调用）。
     *
     * @param taskId 任务 id
     */
    public void cancel(Long taskId) {
        if (taskId == null) {
            return;
        }
        ScheduledFuture<?> future = registry.remove(taskId);
        if (future != null) {
            future.cancel(false);
            log.info("[Scheduler] task#{} cancelled", taskId);
        }
    }

    /**
     * 立即执行一次（{@code triggerType=MANUAL}）。
     *
     * <p>提交到调度池异步跑，接口立即返回，避免长任务把 HTTP 线程挂死；
     * 互斥锁在<b>提交前</b>抢占，这样重复点击能立刻收到「正在执行中」而不是排队。</p>
     *
     * @param taskId 任务 id
     * @throws BizException 任务不存在 / jobKey 非法 / 已在执行中
     */
    public void runOnce(Long taskId) {
        ScheduledTask task = scheduledTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ResultCode.NOT_FOUND, "定时任务不存在");
        }
        if (!isValidJobKey(task.getJobKey())) {
            throw new BizException(ResultCode.VALID_ERROR,
                    "执行目标不在白名单内：" + task.getJobKey());
        }
        if (!tryLock(task.getId())) {
            throw new BizException(ResultCode.VALID_ERROR, "该任务正在执行中，请稍后再试");
        }
        taskScheduler.execute(() -> {
            try {
                invoke(task.getId(), Constants.TRIGGER_TYPE_MANUAL);
            } finally {
                unlock(task.getId());
            }
        });
    }

    /**
     * 真正把任务挂到调度池。
     *
     * @param task 任务实体
     * @return true 注册成功
     */
    private boolean doRegister(ScheduledTask task) {
        if (task == null || task.getId() == null) {
            return false;
        }
        if (!isValidJobKey(task.getJobKey())) {
            log.warn("[Scheduler] task#{} skipped: jobKey={} not in whitelist",
                    task.getId(), task.getJobKey());
            return false;
        }
        if (!CronUtils.isValid(task.getCron())) {
            log.warn("[Scheduler] task#{} skipped: invalid cron={}", task.getId(), task.getCron());
            return false;
        }
        Long taskId = task.getId();
        try {
            ScheduledFuture<?> future = taskScheduler.schedule(
                    () -> runWithLock(taskId), new CronTrigger(task.getCron().trim()));
            if (future == null) {
                log.warn("[Scheduler] task#{} schedule returned null", taskId);
                return false;
            }
            registry.put(taskId, future);
            log.info("[Scheduler] task#{} registered, cron={}, next={}",
                    taskId, task.getCron(), CronUtils.nextExecTime(task.getCron()));
            return true;
        } catch (Exception e) {
            log.warn("[Scheduler] task#{} register failed: {}", taskId, e.getMessage());
            return false;
        }
    }

    /**
     * cron 触发入口：抢锁 → 执行 → 释放。
     *
     * @param taskId 任务 id
     */
    private void runWithLock(Long taskId) {
        if (!tryLock(taskId)) {
            log.info("[Scheduler] task#{} skipped: previous run still in progress", taskId);
            return;
        }
        try {
            invoke(taskId, Constants.TRIGGER_TYPE_CRON);
        } finally {
            unlock(taskId);
        }
    }

    /**
     * 执行任务本体并落执行日志、回写任务上次执行状态。
     *
     * <p>本方法<b>绝不向外抛异常</b>：调度线程一旦抛出异常，Spring 会取消后续触发，
     * 导致「一次失败 = 任务永久停摆」。</p>
     *
     * @param taskId      任务 id
     * @param triggerType CRON / MANUAL
     */
    private void invoke(Long taskId, String triggerType) {
        ScheduledTask task = null;
        LocalDateTime start = LocalDateTime.now();
        long startMillis = System.currentTimeMillis();
        boolean success = true;
        String message;
        try {
            task = scheduledTaskMapper.selectById(taskId);
            if (task == null) {
                log.warn("[Scheduler] task#{} vanished before execution", taskId);
                return;
            }
            ScheduledJob job = jobRegistry.get(task.getJobKey());
            if (job == null) {
                throw new IllegalStateException("执行目标不在白名单内：" + task.getJobKey());
            }
            String result = job.execute(parseParams(task.getParams()));
            message = (result == null || result.isBlank()) ? "执行成功" : result;
        } catch (Throwable t) {
            success = false;
            message = summarize(t);
            log.warn("[Scheduler] task#{} execute failed: {}", taskId, t.getMessage());
        }
        long cost = System.currentTimeMillis() - startMillis;
        writeLog(taskId, start, cost, success, message, triggerType);
        writeBackTaskState(task, start, cost, success);
    }

    /**
     * 落一条执行日志并滚动裁剪历史。
     */
    private void writeLog(Long taskId, LocalDateTime start, long cost,
                          boolean success, String message, String triggerType) {
        try {
            ScheduledTaskLog entity = new ScheduledTaskLog();
            entity.setTaskId(taskId);
            entity.setStartTime(start);
            entity.setCostMs(cost);
            entity.setSuccess(success ? 1 : 0);
            entity.setMessage(truncate(message));
            entity.setTriggerType(triggerType);
            scheduledTaskLogMapper.insert(entity);
            scheduledTaskLogMapper.trimByTask(taskId, LOG_KEEP_PER_TASK);
        } catch (Exception e) {
            log.warn("[Scheduler] task#{} write log failed: {}", taskId, e.getMessage());
        }
    }

    /**
     * 回写任务的上次执行时间 / 结果 / 耗时。
     */
    private void writeBackTaskState(ScheduledTask task, LocalDateTime start, long cost, boolean success) {
        if (task == null) {
            return;
        }
        try {
            ScheduledTask patch = new ScheduledTask();
            patch.setId(task.getId());
            patch.setLastExecTime(start);
            patch.setLastExecResult(success ? 1 : 0);
            patch.setLastCostMs(cost);
            scheduledTaskMapper.updateById(patch);
        } catch (Exception e) {
            log.warn("[Scheduler] task#{} write back state failed: {}", task.getId(), e.getMessage());
        }
    }

    // ============================ 互斥锁 ============================

    /**
     * 抢占执行锁。
     *
     * @param taskId 任务 id
     * @return true 抢到锁；Redis 不可用时降级返回 true（不阻断业务）
     */
    private boolean tryLock(Long taskId) {
        String key = Constants.REDIS_JOB_RUNNING_PREFIX + taskId;
        try {
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(
                    key, "1", Constants.JOB_RUNNING_LOCK_SECONDS, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            log.warn("[Scheduler] redis lock unavailable, degrade to allow. task#{}, msg={}",
                    taskId, e.getMessage());
            return true;
        }
    }

    /**
     * 释放执行锁（失败仅告警，锁本身有 TTL 兜底）。
     *
     * @param taskId 任务 id
     */
    private void unlock(Long taskId) {
        try {
            redisTemplate.delete(Constants.REDIS_JOB_RUNNING_PREFIX + taskId);
        } catch (Exception e) {
            log.warn("[Scheduler] redis unlock failed, task#{}, msg={}", taskId, e.getMessage());
        }
    }

    // ============================ 工具 ============================

    /**
     * 解析任务参数 JSON（形如 {@code {"keepDays":"30"}}）。
     *
     * <p>刻意手写极简解析而不引 ObjectMapper：参数恒为「扁平字符串键值对」，
     * 解析失败一律回落空 Map，绝不让参数格式问题导致任务无法执行。</p>
     *
     * @param json 参数 JSON 字符串，可为 null
     * @return 参数表，永不为 null
     */
    private Map<String, String> parseParams(String json) {
        Map<String, String> result = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return result;
        }
        String body = json.trim();
        if (body.startsWith("{")) {
            body = body.substring(1);
        }
        if (body.endsWith("}")) {
            body = body.substring(0, body.length() - 1);
        }
        if (body.isBlank()) {
            return result;
        }
        for (String pair : body.split(",")) {
            int colon = pair.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = unquote(pair.substring(0, colon));
            String value = unquote(pair.substring(colon + 1));
            if (!key.isEmpty()) {
                result.put(key, value);
            }
        }
        return result;
    }

    private String unquote(String raw) {
        String value = raw.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return value.trim();
    }

    /**
     * 异常摘要：消息 + 前 N 行栈帧。
     *
     * @param t 异常
     * @return 摘要文本
     */
    private String summarize(Throwable t) {
        StringBuilder sb = new StringBuilder();
        sb.append(t.getClass().getSimpleName()).append(": ")
                .append(t.getMessage() == null ? "(no message)" : t.getMessage());
        StackTraceElement[] stack = t.getStackTrace();
        int lines = Math.min(STACK_LINES, stack.length);
        for (int i = 0; i < lines; i++) {
            sb.append("\n  at ").append(stack[i]);
        }
        return sb.toString();
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MESSAGE_MAX_LENGTH
                ? message : message.substring(0, MESSAGE_MAX_LENGTH);
    }

    /**
     * 优雅停机：取消全部注册并关闭调度池。
     */
    @PreDestroy
    public void shutdown() {
        for (Map.Entry<Long, ScheduledFuture<?>> entry : registry.entrySet()) {
            entry.getValue().cancel(false);
        }
        registry.clear();
        taskScheduler.shutdown();
        log.info("[Scheduler] shutdown completed");
    }

    /**
     * 当前已注册的任务 id 快照（供监控 / 排障）。
     *
     * @return 不可变 id 列表
     */
    public List<Long> registeredTaskIds() {
        return Collections.unmodifiableList(new ArrayList<>(registry.keySet()));
    }
}
