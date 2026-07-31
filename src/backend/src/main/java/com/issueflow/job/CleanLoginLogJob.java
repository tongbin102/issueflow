package com.issueflow.job;

import com.issueflow.common.Constants;
import com.issueflow.service.LoginLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 内置任务：清理过期登录日志（对应种子 {@code CLEAN_LOGIN_LOG}）。
 *
 * <p>登录日志成功 / 失败全量记录（PRD Q9 方案 B），增长快且无长期价值，
 * 默认保留 {@link Constants#LOGIN_LOG_KEEP_DAYS} 天，物理删除（非软删）避免表无限膨胀。</p>
 *
 * <p>参数：{@code keepDays} 可覆盖默认保留天数，非法或缺省时回落 90 天。</p>
 */
@Component
@RequiredArgsConstructor
public class CleanLoginLogJob implements ScheduledJob {

    private final LoginLogService loginLogService;

    @Override
    public String jobKey() {
        return "CLEAN_LOGIN_LOG";
    }

    @Override
    public String displayName() {
        return "清理过期登录日志";
    }

    @Override
    public String execute(Map<String, String> params) {
        int keepDays = resolveKeepDays(params);
        LocalDateTime before = LocalDateTime.now().minusDays(keepDays);
        int removed = loginLogService.cleanBefore(before);
        return "清理 " + keepDays + " 天前登录日志 " + removed + " 条";
    }

    /**
     * 解析保留天数参数。
     *
     * @param params 任务参数
     * @return 保留天数，非法输入回落默认值
     */
    private int resolveKeepDays(Map<String, String> params) {
        if (params == null) {
            return Constants.LOGIN_LOG_KEEP_DAYS;
        }
        String raw = params.get("keepDays");
        if (raw == null || raw.isBlank()) {
            return Constants.LOGIN_LOG_KEEP_DAYS;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : Constants.LOGIN_LOG_KEEP_DAYS;
        } catch (NumberFormatException e) {
            return Constants.LOGIN_LOG_KEEP_DAYS;
        }
    }
}
