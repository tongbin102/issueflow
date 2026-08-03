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

    // ---------------------------------------------------------------------
    // Phase10 数据管理：错误文案脱敏
    // ---------------------------------------------------------------------

    /** 疑似密码的键值对：password=xxx / -pxxx / pwd: xxx */
    private static final java.util.regex.Pattern PWD_PATTERN = java.util.regex.Pattern.compile(
            "(?i)(password|passwd|pwd|--password)\\s*[=:]\\s*\\S+");

    /** mysql 客户端短选项形式的密码：-pSecret（-p 后紧跟非空白） */
    private static final java.util.regex.Pattern SHORT_PWD_PATTERN = java.util.regex.Pattern.compile(
            "(?<![\\w-])-p\\S+");

    /** Unix 绝对路径（至少两级），如 /data/issueflow/backups/xxx.zip */
    private static final java.util.regex.Pattern UNIX_PATH_PATTERN = java.util.regex.Pattern.compile(
            "(/[\\w.\\-]+){2,}/?");

    /** Windows 绝对路径，如 D:\\data\\backups\\xxx.zip */
    private static final java.util.regex.Pattern WIN_PATH_PATTERN = java.util.regex.Pattern.compile(
            "(?i)[a-z]:\\\\[^\\s\"']*");

    /** 单条脱敏文案的最大长度，避免把整段 stderr 灌进 error_msg */
    private static final int MAX_LENGTH = 500;

    /**
     * 备份 / 恢复错误文案脱敏。
     *
     * <p>做三件事，缺一不可（安全红线）：</p>
     * <ol>
     *   <li>抹掉一切疑似密码的片段 —— mysqldump / mysql 的 stderr 可能回显命令行；</li>
     *   <li>把服务器绝对路径压成文件名 —— 不向前端泄露目录结构；</li>
     *   <li>截断超长文本 —— 防止把 dump 内容片段整段带出。</li>
     * </ol>
     *
     * @param text 原始文案，可为 null
     * @return 脱敏后的文案；null 转空串
     */
    public static String maskSensitivePath(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String result = text;
        result = PWD_PATTERN.matcher(result).replaceAll("$1=***");
        result = SHORT_PWD_PATTERN.matcher(result).replaceAll("-p***");
        // 绝对路径只保留最后一段文件名，前缀统一折叠为 .../
        result = replacePath(UNIX_PATH_PATTERN, result);
        result = replacePath(WIN_PATH_PATTERN, result);
        if (result.length() > MAX_LENGTH) {
            result = result.substring(0, MAX_LENGTH) + "...";
        }
        return result.trim();
    }

    /**
     * 把匹配到的绝对路径折叠为 {@code .../最后一段}。
     *
     * @param pattern 路径正则
     * @param text    原文
     * @return 折叠后的文本
     */
    private static String replacePath(java.util.regex.Pattern pattern, String text) {
        java.util.regex.Matcher matcher = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String matched = matcher.group();
            String tail = matched;
            int slash = Math.max(matched.lastIndexOf('/'), matched.lastIndexOf('\\'));
            if (slash >= 0 && slash < matched.length() - 1) {
                tail = matched.substring(slash + 1);
            } else if (slash >= 0) {
                tail = "";
            }
            matcher.appendReplacement(sb,
                    java.util.regex.Matcher.quoteReplacement(tail.isEmpty() ? ".../" : ".../" + tail));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
