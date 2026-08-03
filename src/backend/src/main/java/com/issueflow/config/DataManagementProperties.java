package com.issueflow.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 数据管理（备份 / 恢复）静态配置（Phase10）。
 *
 * <p>前缀 {@code issueflow.data-management}，默认值见 {@code application.yml}。</p>
 *
 * <p><b>与 sys_config 的分工</b>：</p>
 * <ul>
 *   <li>本类持有的是**运维级、需重启生效**的项：可执行文件路径、连接参数、目录。</li>
 *   <li>{@code sys_config} 里的 {@code data.management.*} 是**管理员可在页面改、热生效**的项：
 *       保留份数 / 保留天数 / 上传上限 / 任务超时 / 恢复前是否自动备份。</li>
 * </ul>
 *
 * <p><b>安全红线</b>：本类**不持有数据库密码字段**。密码只从 Spring 数据源配置读取，
 * 并且只以 {@code --defaults-extra-file} 临时文件（0600）的形式传给
 * mysqldump / mysql，**绝不出现在命令行参数里**（命令行可被 {@code ps} 窥探）。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "issueflow.data-management")
public class DataManagementProperties {

    /** 是否启用数据管理模块（关闭后所有 /api/admin/data/** 直接 403） */
    private boolean enabled = true;

    /** 备份文件存储根目录（容器内绝对路径，需挂载持久卷，权限 0700） */
    private String backupDir = "/data/issueflow/backups";

    /** mysqldump 可执行文件路径（镜像内由 Dockerfile 从 mysql:8.0 复制） */
    private String mysqldumpPath = "/usr/bin/mysqldump";

    /** mysql 客户端可执行文件路径（恢复导入时使用） */
    private String mysqlClientPath = "/usr/bin/mysql";

    /** 需要一并打包的配置文件（容器内路径），逗号分隔；不存在的条目静默跳过 */
    private String configFiles = "/app/config/application.yml,/app/config/.env";

    /** 单个任务默认超时秒数；sys_config 中同名项存在时以 sys_config 为准 */
    private int taskTimeoutSeconds = 1800;

    /** 全局互斥锁的最长持有时间（秒），兜住进程崩溃导致的锁泄漏 */
    private int lockTtlSeconds = 3600;

    /** 任务进度在 Redis 中的存活时间（秒），默认 2 小时 */
    private int progressTtlSeconds = 7200;

    /** 备份文件名前缀 */
    private String fileNamePrefix = "issueflow_backup";

    /** mysqldump 排除的表（逗号分隔，仅表名不含库名）；默认不排除 */
    private String excludeTables = "";

    /** 恢复导入时单条 SQL 的最大允许包体（MB），映射为 mysql 客户端 --max-allowed-packet */
    private int maxAllowedPacketMb = 128;
}
