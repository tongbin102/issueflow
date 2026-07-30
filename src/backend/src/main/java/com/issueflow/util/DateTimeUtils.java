package com.issueflow.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 日期解析工具：将 yyyy-MM-dd 字符串解析为 LocalDateTime（用于看板/历史/问题筛选范围）
 */
public final class DateTimeUtils {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private DateTimeUtils() {
    }

    /**
     * 解析日期字符串为 LocalDateTime
     *
     * @param dateStr  yyyy-MM-dd
     * @param isStart  true 表示区间起点（00:00:00），false 表示区间终点（23:59:59）
     * @return 解析成功返回 LocalDateTime，空串或格式错误返回 null
     */
    public static LocalDateTime parseDate(String dateStr, boolean isStart) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            LocalDate localDate = LocalDate.parse(dateStr.trim(), DATE_FORMATTER);
            return isStart ? localDate.atStartOfDay() : localDate.atTime(23, 59, 59);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
