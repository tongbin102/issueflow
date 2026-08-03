package com.issueflow.service.data.strategy;

import com.issueflow.common.BizException;
import com.issueflow.common.ResultCode;
import com.issueflow.entity.BackupRecord;
import com.issueflow.enums.TaskPhaseEnum;
import com.issueflow.service.data.TaskProgressStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 纯 JDBC 数据库导出策略（Phase10 数据管理）。
 *
 * <p><b>定位：回落方案</b>。当运行镜像里没有 mysqldump 时（例如精简版基础镜像、
 * 本地开发环境），仍要保证「备份」这个功能可用，而不是直接给管理员一句
 * 「工具不可用」了事。</p>
 *
 * <p>实现方式：{@code SHOW TABLES} 拿表清单 → 每张表 {@code SHOW CREATE TABLE}
 * 输出建表语句 → 流式 {@code SELECT *} 逐行拼 {@code INSERT}。</p>
 *
 * <p><b>已知局限</b>（写进 manifest，恢复时提示管理员）：</p>
 * <ul>
 *   <li>不导出存储过程 / 触发器 / 事件；</li>
 *   <li>逐表读取，非全库一致性快照 —— 因此仅在 mysqldump 缺失时启用。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JdbcExportStrategy implements DumpStrategy {

    private final DataSource dataSource;
    private final TaskProgressStore progressStore;

    /** 流式读取时的 fetchSize，配合 MySQL 驱动的游标模式避免整表进内存 */
    private static final int FETCH_SIZE = Integer.MIN_VALUE;

    /** 每多少行刷一次输出缓冲 */
    private static final int FLUSH_ROWS = 2000;

    /** DUMP_DB 阶段进度起点 */
    private static final int PROGRESS_START = TaskPhaseEnum.LOCK.getWeight();

    /** DUMP_DB 阶段进度终点 */
    private static final int PROGRESS_END = TaskPhaseEnum.DUMP_DB.getWeight();

    /** 时间戳格式 */
    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public boolean isAvailable() {
        try (Connection connection = dataSource.getConnection()) {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            log.warn("[JdbcExportStrategy] 数据库连接不可用: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    @Override
    public String strategyName() {
        return "JDBC_EXPORT";
    }

    @Override
    public long dump(BackupRecord record, OutputStream out) throws Exception {
        CountingWriter writer = new CountingWriter(
                new OutputStreamWriter(out, StandardCharsets.UTF_8));
        try (Connection connection = dataSource.getConnection()) {
            String dbName = connection.getCatalog();
            record.setDbName(dbName == null ? "" : dbName);

            List<String> tables = listTables(connection);
            if (tables.isEmpty()) {
                throw new BizException(ResultCode.BACKUP_EXECUTE_FAILED, "未读取到任何数据表");
            }
            record.setTableCount(tables.size());

            writeHeader(writer, record.getDbName());

            int index = 0;
            for (String table : tables) {
                index++;
                writeTable(connection, writer, table);
                progressStore.updateProgress(record.getTaskId(),
                        stepProgress(index, tables.size()),
                        "已导出 " + index + "/" + tables.size() + " 张表");
            }

            writeFooter(writer);
            writer.flush();
            log.info("[JdbcExportStrategy] 导出完成 taskId={} tables={} bytes={}",
                    record.getTaskId(), tables.size(), writer.getCount());
            return writer.getCount();
        }
    }

    /**
     * 读取当前库的所有基础表（跳过视图）。
     *
     * @param connection 数据库连接
     * @return 表名列表
     * @throws SQLException 查询失败
     */
    private List<String> listTables(Connection connection) throws SQLException {
        List<String> tables = new ArrayList<>(64);
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SHOW FULL TABLES WHERE Table_type = 'BASE TABLE'")) {
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        }
        return tables;
    }

    /**
     * 输出 SQL 文件头：会话变量设置，保证恢复过程与 mysqldump 产物行为一致。
     *
     * @param writer 输出
     * @param dbName 库名
     * @throws Exception 写失败
     */
    private void writeHeader(Writer writer, String dbName) throws Exception {
        writer.write("-- issueFlow backup (JDBC_EXPORT strategy)\n");
        writer.write("-- database: " + dbName + "\n");
        writer.write("-- generated at: " + LocalDateTime.now().format(TS_FORMAT) + "\n");
        writer.write("-- NOTE: routines/triggers/events are NOT included in this strategy.\n\n");
        writer.write("SET NAMES utf8mb4;\n");
        writer.write("SET FOREIGN_KEY_CHECKS = 0;\n");
        writer.write("SET UNIQUE_CHECKS = 0;\n");
        writer.write("SET SQL_MODE = 'NO_AUTO_VALUE_ON_ZERO';\n");
        writer.write("SET AUTOCOMMIT = 0;\n\n");
    }

    /**
     * 输出 SQL 文件尾：恢复会话变量并提交。
     *
     * @param writer 输出
     * @throws Exception 写失败
     */
    private void writeFooter(Writer writer) throws Exception {
        writer.write("\nCOMMIT;\n");
        writer.write("SET FOREIGN_KEY_CHECKS = 1;\n");
        writer.write("SET UNIQUE_CHECKS = 1;\n");
        writer.write("SET AUTOCOMMIT = 1;\n");
    }

    /**
     * 导出单张表（结构 + 数据）。
     *
     * @param connection 数据库连接
     * @param writer     输出
     * @param table      表名
     * @throws Exception 导出失败
     */
    private void writeTable(Connection connection, Writer writer, String table) throws Exception {
        String quoted = quoteIdentifier(table);
        writer.write("\n-- ----------------------------\n");
        writer.write("-- Table structure for " + table + "\n");
        writer.write("-- ----------------------------\n");
        writer.write("DROP TABLE IF EXISTS " + quoted + ";\n");

        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SHOW CREATE TABLE " + quoted)) {
            if (rs.next()) {
                writer.write(rs.getString(2));
                writer.write(";\n\n");
            }
        }

        writer.write("-- Records of " + table + "\n");
        try (Statement statement = connection.createStatement()) {
            statement.setFetchSize(FETCH_SIZE);
            try (ResultSet rs = statement.executeQuery("SELECT * FROM " + quoted)) {
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();
                String columnList = buildColumnList(meta, columnCount);

                long rowIndex = 0L;
                while (rs.next()) {
                    writer.write("INSERT INTO " + quoted + " (" + columnList + ") VALUES (");
                    for (int i = 1; i <= columnCount; i++) {
                        if (i > 1) {
                            writer.write(", ");
                        }
                        writer.write(formatValue(rs, meta, i));
                    }
                    writer.write(");\n");
                    rowIndex++;
                    if (rowIndex % FLUSH_ROWS == 0) {
                        writer.flush();
                    }
                }
            }
        }
        writer.flush();
    }

    /**
     * 拼接列名清单。
     *
     * @param meta        结果集元数据
     * @param columnCount 列数
     * @return 形如 {@code `id`, `name`} 的列清单
     * @throws SQLException 元数据读取失败
     */
    private String buildColumnList(ResultSetMetaData meta, int columnCount) throws SQLException {
        StringBuilder sb = new StringBuilder(128);
        for (int i = 1; i <= columnCount; i++) {
            if (i > 1) {
                sb.append(", ");
            }
            sb.append(quoteIdentifier(meta.getColumnName(i)));
        }
        return sb.toString();
    }

    /**
     * 把结果集单元格转成 SQL 字面量。
     *
     * @param rs     结果集
     * @param meta   元数据
     * @param column 列序号（从 1 开始）
     * @return SQL 字面量文本
     * @throws SQLException 读取失败
     */
    private String formatValue(ResultSet rs, ResultSetMetaData meta, int column) throws SQLException {
        int type = meta.getColumnType(column);
        if (type == Types.BINARY || type == Types.VARBINARY || type == Types.LONGVARBINARY
                || type == Types.BLOB) {
            byte[] bytes = rs.getBytes(column);
            if (rs.wasNull() || bytes == null) {
                return "NULL";
            }
            return toHexLiteral(bytes);
        }

        String value = rs.getString(column);
        if (rs.wasNull() || value == null) {
            return "NULL";
        }
        if (isNumeric(type)) {
            // 数值列直接裸写，但仍校验形态，异常数据退回字符串以免生成非法 SQL
            return value.matches("-?\\d+(\\.\\d+)?([eE][-+]?\\d+)?") ? value : quoteString(value);
        }
        if (type == Types.BIT || type == Types.BOOLEAN) {
            boolean flag = rs.getBoolean(column);
            return flag ? "1" : "0";
        }
        return quoteString(value);
    }

    /**
     * 判断是否数值类型。
     *
     * @param type JDBC 类型码
     * @return true 数值
     */
    private boolean isNumeric(int type) {
        return type == Types.TINYINT || type == Types.SMALLINT || type == Types.INTEGER
                || type == Types.BIGINT || type == Types.FLOAT || type == Types.REAL
                || type == Types.DOUBLE || type == Types.NUMERIC || type == Types.DECIMAL;
    }

    /**
     * 二进制转 MySQL 十六进制字面量。
     *
     * @param bytes 字节数组
     * @return 形如 {@code 0x41424344}
     */
    private String toHexLiteral(byte[] bytes) {
        if (bytes.length == 0) {
            return "''";
        }
        StringBuilder sb = new StringBuilder(bytes.length * 2 + 2);
        sb.append("0x");
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    /**
     * 字符串字面量转义（防止生成非法或可注入的 SQL）。
     *
     * @param raw 原始文本
     * @return 单引号包裹并转义后的字面量
     */
    private String quoteString(String raw) {
        StringBuilder sb = new StringBuilder(raw.length() + 8);
        sb.append('\'');
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '\'':
                    sb.append("\\'");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\0':
                    sb.append("\\0");
                    break;
                case 0x1a:
                    sb.append("\\Z");
                    break;
                default:
                    sb.append(c);
                    break;
            }
        }
        sb.append('\'');
        return sb.toString();
    }

    /**
     * 标识符加反引号并转义内部反引号。
     *
     * @param identifier 表名或列名
     * @return 形如 {@code `table`}
     */
    private String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    /**
     * 按已完成表数换算进度。
     *
     * @param done  已完成表数
     * @param total 总表数
     * @return 百分比
     */
    private int stepProgress(int done, int total) {
        if (total <= 0) {
            return PROGRESS_START;
        }
        int span = PROGRESS_END - PROGRESS_START;
        return PROGRESS_START + (int) Math.round(span * (done / (double) total));
    }

    /**
     * 统计写出字节数的 Writer 包装（UTF-8 下按实际编码长度计）。
     */
    private static final class CountingWriter extends Writer {

        private final Writer delegate;
        private long count = 0L;

        private CountingWriter(Writer delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(char[] cbuf, int off, int len) throws java.io.IOException {
            delegate.write(cbuf, off, len);
            count += new String(cbuf, off, len).getBytes(StandardCharsets.UTF_8).length;
        }

        @Override
        public void write(String str) throws java.io.IOException {
            delegate.write(str);
            count += str.getBytes(StandardCharsets.UTF_8).length;
        }

        @Override
        public void flush() throws java.io.IOException {
            delegate.flush();
        }

        @Override
        public void close() throws java.io.IOException {
            // 刻意只 flush 不 close：底层是 zip 条目流，由归档服务统一收尾
            delegate.flush();
        }

        private long getCount() {
            return count;
        }
    }
}
