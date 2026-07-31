package com.issueflow.util;

import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;

/**
 * cron 表达式工具（Phase 7 定时任务）
 *
 * <p>统一使用 Spring 原生 {@link CronExpression}（6 位：秒 分 时 日 月 周），
 * 与 {@code ThreadPoolTaskScheduler} 的 {@code CronTrigger} 完全同源，
 * 避免「保存时校验通过、注册时解析失败」的口径分裂。</p>
 *
 * <p>next_exec_time <b>不落库</b>，由本工具实时计算（ARCH §3.5），
 * 防止持久化值与调度器真实状态不一致。</p>
 */
public final class CronUtils {

    private CronUtils() {
    }

    /**
     * 校验 cron 表达式是否合法。
     *
     * @param cron cron 表达式，可为 null
     * @return true 合法；null / 空白 / 位数错误 / 语法非法均返回 false
     */
    public static boolean isValid(String cron) {
        if (cron == null || cron.isBlank()) {
            return false;
        }
        try {
            return CronExpression.isValidExpression(cron.trim());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 计算下一次执行时间。
     *
     * @param cron cron 表达式
     * @return 下次执行时间；表达式非法或永不触发时返回 null
     */
    public static LocalDateTime nextExecTime(String cron) {
        return nextExecTime(cron, LocalDateTime.now());
    }

    /**
     * 以指定基准时间计算下一次执行时间。
     *
     * @param cron cron 表达式
     * @param from 基准时间，为 null 时取当前时间
     * @return 下次执行时间；表达式非法或永不触发时返回 null
     */
    public static LocalDateTime nextExecTime(String cron, LocalDateTime from) {
        if (!isValid(cron)) {
            return null;
        }
        LocalDateTime base = from == null ? LocalDateTime.now() : from;
        try {
            return CronExpression.parse(cron.trim()).next(base);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析为 {@link CronExpression}，供调度器直接注册使用。
     *
     * @param cron cron 表达式
     * @return 解析结果；非法时返回 null
     */
    public static CronExpression parse(String cron) {
        if (!isValid(cron)) {
            return null;
        }
        try {
            return CronExpression.parse(cron.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
