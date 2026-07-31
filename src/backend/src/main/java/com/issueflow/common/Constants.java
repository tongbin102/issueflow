package com.issueflow.common;

import java.util.Set;

/**
 * 全局常量
 */
public final class Constants {

    private Constants() {
    }

    /** 角色码 */
    public static final String ROLE_SUBMITTER = "SUBMITTER";
    public static final String ROLE_DEVELOPER = "DEVELOPER";
    public static final String ROLE_TESTER = "TESTER";
    public static final String ROLE_ADMIN = "ADMIN";

    /** 内置角色码集合（禁止删除 / 禁止改角色码） */
    public static final Set<String> BUILTIN_ROLE_CODES = Set.of("ADMIN", "SUBMITTER", "DEVELOPER", "TESTER");

    /** Redis Key：角色权限码前缀，完整 key = perm:role:{roleId}，value 为逗号分隔权限码字符串 */
    public static final String REDIS_PERM_ROLE_PREFIX = "perm:role:";

    /** 菜单端维度：前台端 */
    public static final int MENU_TYPE_FRONT = 1;
    /** 菜单端维度：后台端 */
    public static final int MENU_TYPE_ADMIN = 2;

    /** Redis Key 前缀 */
    public static final String REDIS_JWT_BLACKLIST_PREFIX = "jwt:blacklist:";

    /** 附件存储根路径(可被 application.yml 的 app.attachment-base-path 覆盖) */
    public static final String ATTACHMENT_BASE_PATH = "/data/attachments";

    /** 附件静态资源访问前缀 */
    public static final String ATTACHMENT_STATIC_URL_PREFIX = "/api/attachments/static/";

    /** 单文件大小上限(字节) 20MB */
    public static final long MAX_ATTACHMENT_SIZE = 20L * 1024 * 1024;

    /** 分页默认 */
    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 10;

    /** JWT 默认有效期(秒) 2 小时 */
    public static final long JWT_EXPIRATION_SECONDS = 7200L;

    /** 系统配置键 */
    public static final String CFG_FLOW_REOPEN_ENABLED = "flow_reopen_enabled";
    public static final String CFG_FLOW_REJECT_ENABLED = "flow_reject_enabled";
    public static final String CFG_THEME_COLOR = "theme_color";
    public static final String CFG_LAYOUT = "layout";
    public static final String CFG_MENU_CONFIG = "menu_config";

    /** 网站设置配置键（Phase6，site.* 七键） */
    public static final String CFG_SITE_NAME = "site.name";
    public static final String CFG_SITE_SHORT_NAME = "site.short_name";
    public static final String CFG_SITE_SUBTITLE = "site.subtitle";
    public static final String CFG_SITE_DEFAULT_THEME = "site.default_theme";
    public static final String CFG_SITE_DEFAULT_LOCALE = "site.default_locale";
    public static final String CFG_SITE_COPYRIGHT = "site.copyright";
    public static final String CFG_SITE_ICP = "site.icp";

    /** 前台主题合法值（Q2：4 套主题） */
    public static final Set<String> SITE_THEMES = Set.of("light", "dark", "blue", "green");

    /** 站点语言合法值（Q4：中英双语） */
    public static final Set<String> SITE_LOCALES = Set.of("zh-CN", "en-US");

    /** 内置兜底问题类型 code（存量问题回填 / 禁止删除语义由引用计数天然保证） */
    public static final String ISSUE_TYPE_CODE_OTHER = "OTHER";
}
