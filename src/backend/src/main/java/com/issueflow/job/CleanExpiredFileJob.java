package com.issueflow.job;

import com.issueflow.service.FileRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 内置任务：清理过期临时文件（对应种子 {@code CLEAN_EXPIRED_FILE}）。
 *
 * <p>清理对象为「已软删且超过 N 天」的 {@code file_record} 行 —— 先删物理文件，
 * 再物理删除记录行。软删保留期的意义是给误删留出人工恢复窗口，
 * 因此<b>不会</b>触碰 {@code deleted=0} 的在用文件。</p>
 *
 * <p>参数：{@code keepDays} 可覆盖默认保留天数（默认 30），{@code limit} 单次最多处理条数（默认 1000）。</p>
 */
@Component
@RequiredArgsConstructor
public class CleanExpiredFileJob implements ScheduledJob {

    /** 软删文件的默认保留天数 */
    private static final int DEFAULT_KEEP_DAYS = 30;

    /** 单次执行最多处理的记录数，防止一次跑爆 IO */
    private static final int DEFAULT_LIMIT = 1000;

    private final FileRecordService fileRecordService;

    @Override
    public String jobKey() {
        return "CLEAN_EXPIRED_FILE";
    }

    @Override
    public String displayName() {
        return "清理过期临时文件";
    }

    @Override
    public String execute(Map<String, String> params) {
        int keepDays = intParam(params, "keepDays", DEFAULT_KEEP_DAYS);
        int limit = intParam(params, "limit", DEFAULT_LIMIT);
        LocalDateTime before = LocalDateTime.now().minusDays(keepDays);
        int purged = fileRecordService.purgeSoftDeletedBefore(before, limit);
        return "清理 " + keepDays + " 天前已删除文件 " + purged + " 个（含物理文件）";
    }

    /**
     * 读取整型参数。
     *
     * @param params       参数表
     * @param key          键
     * @param defaultValue 缺省或非法时的默认值
     * @return 参数值
     */
    private int intParam(Map<String, String> params, String key, int defaultValue) {
        if (params == null) {
            return defaultValue;
        }
        String raw = params.get(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
