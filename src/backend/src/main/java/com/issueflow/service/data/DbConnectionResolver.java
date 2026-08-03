package com.issueflow.service.data;

import com.issueflow.common.BizException;
import com.issueflow.common.ResultCode;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.lang.reflect.Method;

/**
 * 数据库连接信息解析器（Phase10 数据管理）。
 *
 * <p>mysqldump / mysql 客户端需要拆开的 host / port / database / user / password，
 * 而 Spring 只给一个 JDBC URL。本类负责把 URL 拆解为命令行可用的片段。</p>
 *
 * <p><b>解析优先级</b>：</p>
 * <ol>
 *   <li>从 {@link DataSource} 反射取 {@code getJdbcUrl / getUsername / getPassword}
 *       （HikariDataSource 提供这三个方法），这是运行时真实生效的值；</li>
 *   <li>回落到 {@link Environment} 的 {@code spring.datasource.*}。</li>
 * </ol>
 *
 * <p><b>安全红线</b>：{@link DbConnectionInfo#getPassword()} 只允许写入
 * {@code --defaults-extra-file} 临时文件（0600），
 * <b>绝不允许</b>拼进命令行参数、日志、异常消息或备份包。
 * 本类的 {@code toString} 已被覆写为不含密码。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DbConnectionResolver {

    private final DataSource dataSource;
    private final Environment environment;

    /** MySQL 默认端口 */
    private static final int DEFAULT_PORT = 3306;

    /** JDBC URL 前缀 */
    private static final String JDBC_MYSQL_PREFIX = "jdbc:mysql://";

    /**
     * 解析当前应用实际使用的数据库连接信息。
     *
     * @return 连接信息，字段均非 null（密码可能为空串）
     * @throws BizException 无法解析出库名时抛出
     */
    public DbConnectionInfo resolve() {
        String url = firstNonBlank(reflectString("getJdbcUrl"),
                environment.getProperty("spring.datasource.url", ""));
        String username = firstNonBlank(reflectString("getUsername"),
                environment.getProperty("spring.datasource.username", ""));
        String password = firstNonBlank(reflectString("getPassword"),
                environment.getProperty("spring.datasource.password", ""));

        if (url.isEmpty()) {
            log.error("[DbConnectionResolver] 无法获取 JDBC URL，数据管理功能不可用");
            throw new BizException(ResultCode.DATA_TOOL_UNAVAILABLE);
        }

        DbConnectionInfo info = parseUrl(url);
        info.setUsername(username);
        info.setPassword(password);

        if (info.getDatabase().isEmpty()) {
            log.error("[DbConnectionResolver] JDBC URL 中未解析到库名");
            throw new BizException(ResultCode.DATA_TOOL_UNAVAILABLE);
        }
        return info;
    }

    /**
     * 拆解 JDBC URL。
     *
     * <p>形如 {@code jdbc:mysql://host:3306/dbname?useSSL=false&...}，
     * 也兼容多主机（{@code host1:3306,host2:3306}）——取第一个主机。</p>
     *
     * @param url JDBC URL，不可为空
     * @return 仅填充了 host / port / database / jdbcUrl 的连接信息
     */
    private DbConnectionInfo parseUrl(String url) {
        DbConnectionInfo info = new DbConnectionInfo();
        info.setJdbcUrl(url);

        String body = url;
        int prefixIdx = body.indexOf(JDBC_MYSQL_PREFIX);
        if (prefixIdx >= 0) {
            body = body.substring(prefixIdx + JDBC_MYSQL_PREFIX.length());
        } else {
            // 非标准前缀（如 jdbc:mysql:loadbalance://），退化为找 "//"
            int slashIdx = body.indexOf("//");
            if (slashIdx >= 0) {
                body = body.substring(slashIdx + 2);
            }
        }

        // 去掉查询串
        int queryIdx = body.indexOf('?');
        if (queryIdx >= 0) {
            body = body.substring(0, queryIdx);
        }

        // 拆 host 段与库名
        int pathIdx = body.indexOf('/');
        String hostPart = pathIdx >= 0 ? body.substring(0, pathIdx) : body;
        String dbPart = pathIdx >= 0 ? body.substring(pathIdx + 1) : "";
        info.setDatabase(dbPart.trim());

        // 多主机取第一个
        int commaIdx = hostPart.indexOf(',');
        if (commaIdx >= 0) {
            hostPart = hostPart.substring(0, commaIdx);
        }

        int colonIdx = hostPart.lastIndexOf(':');
        if (colonIdx > 0) {
            info.setHost(hostPart.substring(0, colonIdx).trim());
            info.setPort(parsePort(hostPart.substring(colonIdx + 1)));
        } else {
            info.setHost(hostPart.trim());
            info.setPort(DEFAULT_PORT);
        }
        if (info.getHost().isEmpty()) {
            info.setHost("127.0.0.1");
        }
        return info;
    }

    /**
     * 端口字符串转 int。
     *
     * @param text 端口文本
     * @return 端口号，非法时回落 3306
     */
    private int parsePort(String text) {
        try {
            int port = Integer.parseInt(text.trim());
            return port > 0 && port <= 65535 ? port : DEFAULT_PORT;
        } catch (NumberFormatException e) {
            return DEFAULT_PORT;
        }
    }

    /**
     * 反射调用 DataSource 上的无参 getter。
     *
     * <p>不直接依赖 HikariDataSource 类型，避免连接池实现被替换后编译不过。</p>
     *
     * @param methodName 方法名
     * @return 返回值字符串；方法不存在或调用失败返回空串
     */
    private String reflectString(String methodName) {
        try {
            Method method = dataSource.getClass().getMethod(methodName);
            Object value = method.invoke(dataSource);
            return value == null ? "" : String.valueOf(value);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "";
        }
    }

    /**
     * 取第一个非空白值。
     *
     * @param first  首选值
     * @param second 备选值
     * @return 非空白的值；都空返回空串
     */
    private String firstNonBlank(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first.trim();
        }
        return second == null ? "" : second.trim();
    }

    /**
     * 数据库连接信息（命令行友好形态）。
     */
    @Data
    public static class DbConnectionInfo {

        /** 主机名或 IP */
        private String host = "127.0.0.1";

        /** 端口 */
        private int port = DEFAULT_PORT;

        /** 库名 */
        private String database = "";

        /** 用户名 */
        private String username = "";

        /** 密码（仅可写入 0600 临时文件） */
        private String password = "";

        /** 原始 JDBC URL（含参数） */
        private String jdbcUrl = "";

        /**
         * 安全的字符串表示：<b>永不包含密码</b>。
         *
         * @return 脱敏后的描述
         */
        @Override
        public String toString() {
            return "DbConnectionInfo{host=" + host + ", port=" + port
                    + ", database=" + database + ", username=" + username
                    + ", password=***}";
        }
    }
}
