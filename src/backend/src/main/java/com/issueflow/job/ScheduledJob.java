package com.issueflow.job;

import java.util.Map;

/**
 * 可调度任务白名单接口（Phase 7 安全红线）。
 *
 * <p><b>禁止任何形式的类名反射执行</b>（ARCH §7.9）。{@code scheduled_task.job_key}
 * 只能命中本接口所有 Spring Bean 实现所组成的 {@code jobRegistry}，
 * 未命中即拒绝保存 —— 这样即便有人向表里手工写入恶意 job_key 也无法触发任意代码。</p>
 *
 * <p>新增内置任务的唯一方式：实现本接口并声明为 Spring Bean，
 * {@code DynamicTaskScheduler} 会在启动时自动收集。</p>
 */
public interface ScheduledJob {

    /**
     * 任务唯一标识，对应 {@code scheduled_task.job_key}。
     *
     * @return 大写下划线风格的 key，如 {@code CLEAN_LOGIN_LOG}
     */
    String jobKey();

    /**
     * 任务展示名（前端「执行目标」下拉展示）。
     *
     * @return 中文展示名
     */
    String displayName();

    /**
     * 执行任务。
     *
     * <p>返回值为「执行结果摘要」，将写入 {@code scheduled_task_log.message}
     * （该列语义为「执行结果 / 异常摘要」，故此处相较 ARCH 的 {@code void} 签名
     * 改为返回 String，以便结果列有真实内容而非固定文案）。</p>
     *
     * @param params 任务参数（由 {@code scheduled_task.params} 的 JSON 解析而来，永不为 null）
     * @return 执行结果摘要，可为 null（调度器会回填默认文案）
     * @throws Exception 执行失败时抛出，由调度器捕获并记为失败日志
     */
    String execute(Map<String, String> params) throws Exception;
}
