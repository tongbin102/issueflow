package com.issueflow.util;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 备份 JSON 流式写出器（Phase 7 T8）。
 *
 * <p>使用 Jackson 底层 {@link JsonGenerator} 而非 {@code ObjectMapper.writeValue}：
 * 后者需要把整表数据先聚成对象树再序列化，20 万行会直接 OOM。本类逐行 flush，
 * 内存占用与表大小无关。</p>
 *
 * <p>输出结构：</p>
 * <pre>
 * { "meta": {...}, "tables": [ { "name": "issue", "rowCount": 12, "rows": [ {...}, ... ] } ] }
 * </pre>
 *
 * <p>值类型统一归一为 JSON 基础类型（字符串 / 数字 / 布尔 / null），
 * 时间类输出 {@code yyyy-MM-dd HH:mm:ss}，二进制输出 Base64 —— 不依赖任何 Jackson 扩展模块，
 * 避免 JSR-310 模块未注册导致导出中途抛异常。</p>
 */
public class JsonDumpWriter implements Closeable {

    /** 时间格式，与全站 {@code @JsonFormat} 保持一致 */
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JsonGenerator generator;

    /** 当前表已写出的行数，用于 endTable 回填 rowCount 之外的自检 */
    private long currentTableRows = 0L;

    /** 累计写出字节数的粗略估算（用于上限保护） */
    private long approximateBytes = 0L;

    /**
     * 构造写出器。
     *
     * @param out 目标输出流（调用方负责最终关闭底层流）
     * @throws IOException 创建生成器失败
     */
    public JsonDumpWriter(OutputStream out) throws IOException {
        JsonFactory factory = new JsonFactory();
        this.generator = factory.createGenerator(out, JsonEncoding.UTF8);
        this.generator.useDefaultPrettyPrinter();
    }

    /**
     * 写文件头元信息并开启 tables 数组。
     *
     * @param meta 元信息键值对（appName / appVersion / exportedAt / scope / ...）
     * @throws IOException 写出失败
     */
    public void begin(Map<String, Object> meta) throws IOException {
        generator.writeStartObject();
        generator.writeFieldName("meta");
        generator.writeStartObject();
        for (Map.Entry<String, Object> entry : meta.entrySet()) {
            writeField(entry.getKey(), entry.getValue());
        }
        generator.writeEndObject();
        generator.writeFieldName("tables");
        generator.writeStartArray();
    }

    /**
     * 开始一张表。
     *
     * @param table   表名
     * @param columns 列名（仅作为元信息记录，行数据以实际键为准）
     * @throws IOException 写出失败
     */
    public void beginTable(String table, List<String> columns) throws IOException {
        currentTableRows = 0L;
        generator.writeStartObject();
        generator.writeStringField("name", table);
        generator.writeFieldName("columns");
        generator.writeStartArray();
        if (columns != null) {
            for (String column : columns) {
                generator.writeString(column);
            }
        }
        generator.writeEndArray();
        generator.writeFieldName("rows");
        generator.writeStartArray();
    }

    /**
     * 写出一行数据。
     *
     * @param row 列名 → 值
     * @throws IOException 写出失败
     */
    public void writeRow(Map<String, Object> row) throws IOException {
        generator.writeStartObject();
        if (row != null) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                writeField(entry.getKey(), entry.getValue());
            }
        }
        generator.writeEndObject();
        currentTableRows++;
        approximateBytes += 64L + (row == null ? 0L : row.size() * 24L);
    }

    /**
     * 结束当前表。
     *
     * @throws IOException 写出失败
     */
    public void endTable() throws IOException {
        generator.writeEndArray();
        generator.writeNumberField("rowCount", currentTableRows);
        generator.writeEndObject();
        generator.flush();
    }

    /**
     * 结束整个文档。
     *
     * @throws IOException 写出失败
     */
    public void end() throws IOException {
        generator.writeEndArray();
        generator.writeEndObject();
        generator.flush();
    }

    /**
     * 当前表已写出行数。
     *
     * @return 行数
     */
    public long getCurrentTableRows() {
        return currentTableRows;
    }

    /**
     * 累计写出字节数的粗略估算（供上限保护判断，非精确值）。
     *
     * @return 估算字节数
     */
    public long getApproximateBytes() {
        return approximateBytes;
    }

    @Override
    public void close() throws IOException {
        generator.close();
    }

    /**
     * 写单个字段，按 Java 类型归一为 JSON 基础类型。
     *
     * @param name  字段名
     * @param value 字段值，可为 null
     * @throws IOException 写出失败
     */
    private void writeField(String name, Object value) throws IOException {
        if (value == null) {
            generator.writeNullField(name);
            return;
        }
        if (value instanceof String s) {
            generator.writeStringField(name, s);
        } else if (value instanceof Boolean b) {
            generator.writeBooleanField(name, b);
        } else if (value instanceof Integer i) {
            generator.writeNumberField(name, i);
        } else if (value instanceof Long l) {
            generator.writeNumberField(name, l);
        } else if (value instanceof Short sh) {
            generator.writeNumberField(name, sh.intValue());
        } else if (value instanceof Byte by) {
            generator.writeNumberField(name, by.intValue());
        } else if (value instanceof Double d) {
            generator.writeNumberField(name, d);
        } else if (value instanceof Float f) {
            generator.writeNumberField(name, f);
        } else if (value instanceof BigDecimal bd) {
            generator.writeNumberField(name, bd);
        } else if (value instanceof BigInteger bi) {
            generator.writeNumberField(name, new BigDecimal(bi));
        } else if (value instanceof byte[] bytes) {
            generator.writeStringField(name, Base64.getEncoder().encodeToString(bytes));
        } else if (value instanceof LocalDateTime dt) {
            generator.writeStringField(name, dt.format(DATE_TIME_FORMAT));
        } else if (value instanceof LocalDate d) {
            generator.writeStringField(name, d.toString());
        } else if (value instanceof LocalTime t) {
            generator.writeStringField(name, t.toString());
        } else if (value instanceof Timestamp ts) {
            generator.writeStringField(name, ts.toLocalDateTime().format(DATE_TIME_FORMAT));
        } else if (value instanceof java.sql.Date sd) {
            generator.writeStringField(name, sd.toLocalDate().toString());
        } else if (value instanceof java.util.Date ud) {
            generator.writeStringField(name,
                    new Timestamp(ud.getTime()).toLocalDateTime().format(DATE_TIME_FORMAT));
        } else {
            generator.writeStringField(name, String.valueOf(value));
        }
    }
}
