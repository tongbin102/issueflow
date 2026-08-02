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

    private static final DateTimeFormatter DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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

    /**
     * 将 LocalDateTime 格式化为 {@code yyyy-MM-dd} 字符串（无时分秒）。
     *
     * <p>用于 DATE 类型自定义字段的出参，对齐前端
     * {@code DynamicField.vue} 中 DATE 控件的 {@code valueFormat='YYYY-MM-DD'} 契约。
     *
     * @param dt 待格式化的日期时间，可为 null
     * @return 格式化后的日期字符串；{@code dt} 为 null 时返回 null
     */
    public static String formatDate(LocalDateTime dt) {
        if (dt == null) {
            return null;
        }
        return dt.format(DATE_FORMATTER);
    }

    /**
     * 将 LocalDateTime 格式化为 {@code yyyy-MM-dd HH:mm:ss} 字符串。
     *
     * <p>用于 DATETIME 类型自定义字段的出参，对齐前端
     * {@code DynamicField.vue} 中 DATETIME 控件的
     * {@code valueFormat='YYYY-MM-DD HH:mm:ss'} 契约。
     *
     * @param dt 待格式化的日期时间，可为 null
     * @return 格式化后的日期时间字符串；{@code dt} 为 null 时返回 null
     */
    public static String formatDateTime(LocalDateTime dt) {
        if (dt == null) {
            return null;
        }
        return dt.format(DATETIME_FORMATTER);
    }
}
