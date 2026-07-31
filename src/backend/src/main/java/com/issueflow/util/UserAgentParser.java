package com.issueflow.util;

/**
 * 极简 User-Agent 解析（Phase 7 登录日志）
 *
 * <p>刻意<b>不引入三方库</b>（ua-parser / yauaa 体积与维护成本远大于收益）：
 * 登录日志只需展示「浏览器 / 操作系统」两个粗粒度标签，正则覆盖主流 UA 即可，
 * 未命中一律回退 {@code Unknown}，绝不抛异常。</p>
 */
public final class UserAgentParser {

    /** 未识别时的兜底值 */
    public static final String UNKNOWN = "Unknown";

    private UserAgentParser() {
    }

    /**
     * 解析浏览器名称。
     *
     * <p>判定顺序有意从「小众且会伪装成 Chrome 的内核」排到「通用内核」，
     * 例如 Edge 的 UA 同时含 Chrome/Safari，必须先判 Edg。</p>
     *
     * @param userAgent 原始 UA，可为 null
     * @return 浏览器名，未识别返回 {@code Unknown}
     */
    public static String parseBrowser(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return UNKNOWN;
        }
        String ua = userAgent;
        if (contains(ua, "MicroMessenger")) {
            return "WeChat";
        }
        if (contains(ua, "DingTalk")) {
            return "DingTalk";
        }
        if (contains(ua, "QQBrowser")) {
            return "QQBrowser";
        }
        if (contains(ua, "UCBrowser")) {
            return "UC";
        }
        if (contains(ua, "Edg/") || contains(ua, "Edge/") || contains(ua, "EdgA/")) {
            return "Edge";
        }
        if (contains(ua, "OPR/") || contains(ua, "Opera")) {
            return "Opera";
        }
        if (contains(ua, "Firefox/")) {
            return "Firefox";
        }
        if (contains(ua, "Chrome/") || contains(ua, "CriOS/")) {
            return "Chrome";
        }
        if (contains(ua, "Safari/")) {
            return "Safari";
        }
        if (contains(ua, "MSIE") || contains(ua, "Trident/")) {
            return "IE";
        }
        if (contains(ua, "curl/")) {
            return "curl";
        }
        if (contains(ua, "PostmanRuntime")) {
            return "Postman";
        }
        return UNKNOWN;
    }

    /**
     * 解析操作系统名称。
     *
     * @param userAgent 原始 UA，可为 null
     * @return 操作系统名，未识别返回 {@code Unknown}
     */
    public static String parseOs(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return UNKNOWN;
        }
        String ua = userAgent;
        if (contains(ua, "Windows NT 10.0")) {
            return "Windows 10/11";
        }
        if (contains(ua, "Windows NT 6.3")) {
            return "Windows 8.1";
        }
        if (contains(ua, "Windows NT 6.1")) {
            return "Windows 7";
        }
        if (contains(ua, "Windows")) {
            return "Windows";
        }
        if (contains(ua, "Android")) {
            return "Android";
        }
        if (contains(ua, "iPhone") || contains(ua, "iPad") || contains(ua, "iPod")) {
            return "iOS";
        }
        if (contains(ua, "Mac OS X") || contains(ua, "Macintosh")) {
            return "macOS";
        }
        if (contains(ua, "Ubuntu")) {
            return "Ubuntu";
        }
        if (contains(ua, "CentOS")) {
            return "CentOS";
        }
        if (contains(ua, "Linux")) {
            return "Linux";
        }
        return UNKNOWN;
    }

    /**
     * 组装「浏览器 / 操作系统」展示串。
     *
     * @param userAgent 原始 UA，可为 null
     * @return 形如 {@code Chrome / Windows 10/11}
     */
    public static String parseDevice(String userAgent) {
        return parseBrowser(userAgent) + " / " + parseOs(userAgent);
    }

    /**
     * 截断超长 UA，适配 login_log.user_agent VARCHAR(512)。
     *
     * @param userAgent 原始 UA，可为 null
     * @return 截断后的 UA，null 入参返回 null
     */
    public static String truncate(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() <= 512 ? userAgent : userAgent.substring(0, 512);
    }

    private static boolean contains(String source, String keyword) {
        return source.toLowerCase().contains(keyword.toLowerCase());
    }
}
