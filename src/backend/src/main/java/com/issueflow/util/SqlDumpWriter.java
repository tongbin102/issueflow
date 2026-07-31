package com.issueflow.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 备份 SQL 流式写出器（Phase 7 T8）。
 *
 * <p>生成可直接在<b>结构已存在</b>的目标库上执行的 {@code INSERT} 脚本：
 * 注释头 → {@code SET NAMES utf8mb4;} → 逐表批量 INSERT（每 500 行合并一条语句）。</p>
 *
 * <p><b>转义铁律</b>：值一律走 {@link #escape(String)} 处理反斜杠、单引号、换行、
 * NUL 与 Ctrl-Z —— 少转义任何一个都会让导出的 SQL 在执行时语法错误甚至截断。</p>
 */
public class SqlDumpWriter implements Closeable {

    /** 每条 INSERT 语句合并的最大行数 */
    private static final int BATCH_ROWS = 500;

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Writer writer;

    /** 当前表名 */
    private String currentTable = "";

    /** 当前表列名（决定 VALUES 顺序） */
    private List<String> currentColumns = new ArrayList<>();

    /** 当前批次已缓冲的 VALUES 片段 */
    private final List<String> buffer = new ArrayList<>();

    /** 累计写出字符数（供上限保护判断） */
    private long approximateBytes = 0L;

    /**
     * 构造写出器。
     *
     * @param out 目标输出流（调用方负责最终关闭底层流）
     */
    public SqlDumpWriter(OutputStream out) {
        this.writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
    }

    /**
     * 写注释头元信息。
     *
     * @param meta 元信息键值对
     * @throws IOException 写出失败
     */
    public void writeHeader(Map<String, Object> meta) throws IOException {
        write("-- ============================================================\n");
        write("-- issueFlow 数据备份（SQL 格式）\n");
        for (Map.Entry<String, Object> entry : meta.entrySet()) {
            write("-- " + entry.getKey() + ": " + safeComment(entry.getValue()) + "\n");
        }
        write("-- 说明：本文件仅包含 INSERT 语句，执行前目标库须已具备同版本表结构。\n");
        write("-- ============================================================\n");
        write("SET NAMES utf8mb4;\n");
        write("SET FOREIGN_KEY_CHECKS = 0;\n\n");
    }

    /**
     * 开始一张表。
     *
     * @param table   表名
     * @param columns 列名（决定 INSERT 的列顺序，不可为空）
     * @throws IOException 写出失败
     */
    public void beginTable(String table, List<String> columns) throws IOException {
        flushBatch();
        this.currentTable = table;
        this.currentColumns = columns == null ? new ArrayList<>() : new ArrayList<>(columns);
        write("\n-- ---------- 表 `" + table + "` ----------\n");
    }

    /**
     * 写出一行数据（内部按 500 行批量合并成一条 INSERT）。
     *
     * @param row 列名 → 值
     * @throws IOException 写出失败
     */
    public void writeRow(Map<String, Object> row) throws IOException {
        if (currentColumns.isEmpty() || row == null) {
            return;
        }
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < currentColumns.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(literal(row.get(currentColumns.get(i))));
        }
        sb.append(")");
        buffer.add(sb.toString());
        if (buffer.size() >= BATCH_ROWS) {
            flushBatch();
        }
    }

    /**
     * 结束当前表（冲刷未满批的缓冲）。
     *
     * @throws IOException 写出失败
     */
    public void endTable() throws IOException {
        flushBatch();
        writer.flush();
    }

    /**
     * 结束整个文档。
     *
     * @throws IOException 写出失败
     */
    public void end() throws IOException {
        flushBatch();
        write("\nSET FOREIGN_KEY_CHECKS = 1;\n");
        write("-- 备份结束\n");
        writer.flush();
    }

    /**
     * 累计写出字节数的粗略估算。
     *
     * @return 估算字节数
     */
    public long getApproximateBytes() {
        return approximateBytes;
    }

    @Override
    public void close() throws IOException {
        writer.flush();
        writer.close();
    }

    /**
     * 冲刷缓冲区为一条 INSERT 语句。
     *
     * @throws IOException 写出失败
     */
    private void flushBatch() throws IOException {
        if (buffer.isEmpty() || currentTable.isEmpty() || currentColumns.isEmpty()) {
            buffer.clear();
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO `").append(currentTable).append("` (");
        for (int i = 0; i < currentColumns.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append('`').append(currentColumns.get(i)).append('`');
        }
        sb.append(") VALUES\n");
        for (int i = 0; i < buffer.size(); i++) {
            sb.append(buffer.get(i));
            sb.append(i == buffer.size() - 1 ? ";\n" : ",\n");
        }
        buffer.clear();
        write(sb.toString());
        writer.flush();
    }

    /**
     * 将 Java 值转为 SQL 字面量。
     *
     * @param value 值，可为 null
     * @return SQL 字面量文本
     */
    private String literal(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number || value instanceof BigDecimal || value instanceof BigInteger) {
            return value.toString();
        }
        if (value instanceof Boolean b) {
            return b ? "1" : "0";
        }
        if (value instanceof byte[] bytes) {
            return hexLiteral(bytes);
        }
        if (value instanceof LocalDateTime dt) {
            return "'" + dt.format(DATE_TIME_FORMAT) + "'";
        }
        if (value instanceof LocalDate d) {
            return "'" + d + "'";
        }
        if (value instanceof LocalTime t) {
            return "'" + t + "'";
        }
        if (value instanceof Timestamp ts) {
            return "'" + ts.toLocalDateTime().format(DATE_TIME_FORMAT) + "'";
        }
        if (value instanceof java.sql.Date sd) {
            return "'" + sd.toLocalDate() + "'";
        }
        if (value instanceof java.util.Date ud) {
            return "'" + new Timestamp(ud.getTime()).toLocalDateTime().format(DATE_TIME_FORMAT) + "'";
        }
        return "'" + escape(String.valueOf(value)) + "'";
    }

    /**
     * 二进制转 {@code 0x...} 字面量（file_record 等表理论上无二进制列，此处为防御性兜底）。
     *
     * @param bytes 字节数组
     * @return 十六进制字面量；空数组返回 {@code ''}
     */
    private String hexLiteral(byte[] bytes) {
        if (bytes.length == 0) {
            return "''";
        }
        StringBuilder sb = new StringBuilder("0x");
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    /**
     * MySQL 字符串转义。
     *
     * @param raw 原始字符串
     * @return 转义后的字符串（不含外层引号）
     */
    private String escape(String raw) {
        StringBuilder sb = new StringBuilder(raw.length() + 16);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\'' -> sb.append("\\'");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\0' -> sb.append("\\0");
                case '\u001a' -> sb.append("\\Z");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 注释行安全化：把换行压平，避免元信息中的换行破坏 {@code --} 注释语义。
     *
     * @param value 值
     * @return 单行文本
     */
    private String safeComment(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).replace("\r", " ").replace("\n", " ");
    }

    private void write(String text) throws IOException {
        writer.write(text);
        approximateBytes += text.length();
    }
}
