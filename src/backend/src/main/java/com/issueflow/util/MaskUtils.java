package com.issueflow.util;

/**
 * 敏感信息脱敏工具（Phase 7 个人中心）
 *
 * <p>脱敏值仅用于<b>展示</b>；编辑回填一律走 {@code ProfileVO.emailRaw / phoneRaw} 原值字段，
 * 避免前端把 {@code 138****8000} 当成真实值提交回来覆盖数据。</p>
 */
public final class MaskUtils {

    private MaskUtils() {
    }

    /**
     * 手机号脱敏：{@code 13812348000} → {@code 138****8000}。
     *
     * @param phone 手机号，可为 null
     * @return 脱敏结果；null / 空白原样返回；长度不足 7 位时中间统一打码
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone;
        }
        String value = phone.trim();
        if (value.length() < 7) {
            return value.charAt(0) + "****";
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }

    /**
     * 邮箱脱敏：{@code zhangsan@corp.com} → {@code z***@corp.com}。
     *
     * @param email 邮箱，可为 null
     * @return 脱敏结果；null / 空白 / 无 {@code @} 时原样返回
     */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return email;
        }
        String value = email.trim();
        int at = value.indexOf('@');
        if (at <= 0) {
            return value;
        }
        String name = value.substring(0, at);
        String domain = value.substring(at);
        if (name.length() == 1) {
            return name + "***" + domain;
        }
        return name.charAt(0) + "***" + domain;
    }
}
