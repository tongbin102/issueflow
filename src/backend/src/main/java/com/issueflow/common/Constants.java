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

    /**
     * 新增用户默认密码（Phase8 W1 #2）。
     * <p>敏感项：仅管理端 GET /api/admin/site/config 下发，公开的 /api/site/config 不返回。</p>
     */
    public static final String CFG_SITE_DEFAULT_PASSWORD = "site.default_password";

    /** 新增用户默认密码兜底值（DB 缺键时使用） */
    public static final String DEFAULT_USER_PASSWORD = "123456";

    /** 前台主题合法值（Q2：4 套主题） */
    public static final Set<String> SITE_THEMES = Set.of("light", "dark", "blue", "green");

    /** 站点语言合法值（Q4：中英双语） */
    public static final Set<String> SITE_LOCALES = Set.of("zh-CN", "en-US");

    /** 内置兜底问题类型 code（存量问题回填 / 禁止删除语义由引用计数天然保证） */
    public static final String ISSUE_TYPE_CODE_OTHER = "OTHER";

    // ======================= Phase 7 =======================

    /** 文件配置键（sys_config 的 file.* 4 键） */
    public static final String CFG_FILE_STORAGE_ROOT = "file.storage_root";
    public static final String CFG_FILE_MAX_SIZE_MB = "file.max_size_mb";
    public static final String CFG_FILE_ALLOWED_EXTS = "file.allowed_exts";
    public static final String CFG_FILE_STORAGE_TYPE = "file.storage_type";

    /** 文件配置默认值（sys_config 缺键时兜底） */
    public static final String DEFAULT_FILE_STORAGE_ROOT = "/data/attachments";
    public static final int DEFAULT_FILE_MAX_SIZE_MB = 10;
    public static final String DEFAULT_FILE_ALLOWED_EXTS =
            "jpg,jpeg,png,gif,pdf,zip,rar,doc,docx,xls,xlsx,txt,log";
    public static final String DEFAULT_FILE_STORAGE_TYPE = "LOCAL";

    /** 文件配置本地缓存有效期（毫秒，30s） */
    public static final long FILE_CONFIG_CACHE_MILLIS = 30_000L;

    /** 可在线预览的扩展名 */
    public static final Set<String> PREVIEWABLE_EXTS = Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp");

    /** 头像允许的扩展名 */
    public static final Set<String> AVATAR_EXTS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    /** 头像单文件上限（字节，2MB） */
    public static final long MAX_AVATAR_SIZE = 2L * 1024 * 1024;

    /** 文件业务类型 */
    public static final String BIZ_TYPE_ISSUE = "ISSUE";
    public static final String BIZ_TYPE_AVATAR = "AVATAR";
    public static final String BIZ_TYPE_MANUAL = "MANUAL";

    /** 存储类型 */
    public static final String STORAGE_TYPE_LOCAL = "LOCAL";

    /** 字典类型编码（业务真实使用） */
    public static final String DICT_TYPE_ISSUE_SOURCE = "ISSUE_SOURCE";

    /** 默认来源项编码（创建问题未指定来源时兜底） */
    public static final String DICT_ITEM_SOURCE_SYSTEM = "SYSTEM";

    /** 来源项编码：手工录入 */
    public static final String DICT_ITEM_SOURCE_MANUAL = "MANUAL";

    /** Redis Key：字典项缓存前缀，完整 key = dict:items:{typeCode} */
    public static final String REDIS_DICT_PREFIX = "dict:items:";

    /** 字典缓存 TTL（秒，1 小时） */
    public static final long DICT_CACHE_TTL_SECONDS = 3600L;

    /** Redis Key：定时任务执行互斥锁前缀，完整 key = job:running:{taskId} */
    public static final String REDIS_JOB_RUNNING_PREFIX = "job:running:";

    /** 定时任务互斥锁 TTL（秒，10 分钟） */
    public static final long JOB_RUNNING_LOCK_SECONDS = 600L;

    /** 登录日志保留天数 */
    public static final int LOGIN_LOG_KEEP_DAYS = 90;

    /** 任务触发方式 */
    public static final String TRIGGER_TYPE_CRON = "CRON";
    public static final String TRIGGER_TYPE_MANUAL = "MANUAL";

    /** 备份：单表游标分页批大小 */
    public static final int BACKUP_PAGE_SIZE = 2000;

    /** 备份：总行数超过该阈值时在预估结果中给出耗时警告 */
    public static final long BACKUP_MAX_ROWS = 500_000L;

    /** 备份：生成文件超过该字节数时中断并提示（512MB） */
    public static final long BACKUP_MAX_BYTES = 512L * 1024 * 1024;

    /** 备份：不导出的日志类表 */
    public static final Set<String> BACKUP_EXCLUDED_TABLES = Set.of("scheduled_task_log", "login_log");

    /** 配置管理：系统内置配置键前缀（不可删除，仅可改值） */
    public static final Set<String> BUILTIN_CONFIG_PREFIXES =
            Set.of("site.", "file.", "flow_", "theme_", "layout", "menu_config");
}
