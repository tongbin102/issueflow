package com.issueflow.service;

import com.issueflow.common.BizException;
import com.issueflow.common.Constants;
import com.issueflow.common.ResultCode;
import com.issueflow.dto.req.BackupReq;
import com.issueflow.dto.resp.BackupEstimateVO;
import com.issueflow.entity.User;
import com.issueflow.mapper.BackupMapper;
import com.issueflow.mapper.UserMapper;
import com.issueflow.util.JsonDumpWriter;
import com.issueflow.util.SecurityUtils;
import com.issueflow.util.SqlDumpWriter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据备份导出服务（Phase 7 T8）。
 *
 * <p><b>表名安全双保险</b>（ARCH §7.9）：{@link BackupMapper} 用 {@code ${}} 拼接表名，
 * 因此表清单只能来自本类的 {@code List<String>} 常量；Service 层再做一次
 * {@code TABLES.contains(table)} 断言。<b>前端永远不能传表名</b>，只能选 scope。</p>
 *
 * <p><b>不产生半截文件</b>：先完整写入系统临时文件，全部成功后才把临时文件回传给客户端；
 * 中途任一异常都删除临时文件并抛业务异常（此时响应体尚未开始写，
 * 全局异常处理器可以正常返回 JSON 错误体供前端按 Content-Type 判定）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupService {

    /** 备份权限码 */
    private static final String PERM_EXPORT = "system:backup:export";

    /** 备份文件格式版本，结构变更时递增 */
    private static final int FORMAT_VERSION = 1;

    /** 文件名时间戳格式 */
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");

    /** 元信息时间格式 */
    private static final DateTimeFormatter META_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 核心配置范围表清单。
     *
     * <p>相对 ARCH §3.9 的两处对齐：① {@code dict_type} → 实际落库表名 {@code dict}
     * （口径以 V20260731 迁移脚本为准）；② 追加 {@code file_config} ——
     * 本期文件配置从 {@code sys_config} 的 {@code file.*} 键改为独立表，
     * 若不纳入清单，核心配置备份会<b>丢失文件存储配置</b>。</p>
     */
    private static final List<String> CORE_TABLES = List.of(
            "sys_config",
            "file_config",
            "menu",
            "permission",
            "role",
            "role_permission",
            "flow_node",
            "flow_transition",
            "issue_type",
            "dict",
            "dict_item",
            "scheduled_task"
    );

    /** 全量范围追加的业务表清单 */
    private static final List<String> BUSINESS_TABLES = List.of(
            "user",
            "organization",
            "project",
            "module",
            "module_dependency",
            "tag",
            "issue",
            "issue_history",
            "issue_relation",
            "issue_attachment",
            "file_record"
    );

    /**
     * 敏感列白名单：{@code 表名.列名}，导出时值统一替换为 {@code ***}。
     *
     * <p>「核心配置」范围本就不含 {@code user} 表，天然无密码问题；
     * 「全量」范围必须脱敏，且在文件头标注 {@code passwordMasked:true}
     * 并提示「本备份无法直接用于账号还原」。</p>
     */
    private static final Set<String> SENSITIVE_COLUMNS = Set.of("user.password");

    /** 脱敏后的占位值 */
    private static final String MASKED_VALUE = "***";

    private final BackupMapper backupMapper;
    private final UserMapper userMapper;
    private final PermissionService permissionService;

    // ============================ 预估 ============================

    /**
     * 导出前预估（权限 {@code system:backup:export}）。
     *
     * @param scope 范围 ALL / CORE
     * @return 预估视图（表数、总条数、逐表条数、建议文件名、超限告警）
     */
    public BackupEstimateVO estimate(String scope) {
        permissionService.requirePermission(PERM_EXPORT);
        String normalizedScope = normalizeScope(scope);
        List<String> tables = tablesOf(normalizedScope);

        BackupEstimateVO vo = new BackupEstimateVO();
        vo.setScope(normalizedScope);
        vo.setExcludedTables(new ArrayList<>(Constants.BACKUP_EXCLUDED_TABLES));
        vo.setAttachmentBinaryIncluded(Boolean.FALSE);

        long total = 0L;
        int tableCount = 0;
        for (String table : tables) {
            if (!existsTable(table)) {
                log.warn("[Backup] table {} not found, skipped in estimate", table);
                continue;
            }
            long rows = backupMapper.countTable(table);
            vo.getTables().add(new BackupEstimateVO.TableRows(table, rows));
            total += rows;
            tableCount++;
        }
        vo.setTableCount(tableCount);
        vo.setTotalRows(total);
        vo.setSuggestedFileName(fileNameOf(LocalDateTime.now(), "json"));
        if (total > Constants.BACKUP_MAX_ROWS) {
            vo.setWarning("数据量较大（约 " + total + " 行），导出可能耗时较久，请勿重复点击；"
                    + "若超过 " + Constants.BACKUP_MAX_ROWS + " 行上限将中断，请联系管理员分批导出");
        }
        return vo;
    }

    // ============================ 导出 ============================

    /**
     * 流式导出备份文件（权限 {@code system:backup:export}）。
     *
     * @param req      导出请求（scope + format）
     * @param response HTTP 响应
     */
    public void export(BackupReq req, HttpServletResponse response) {
        permissionService.requirePermission(PERM_EXPORT);
        String scope = normalizeScope(req == null ? null : req.getScope());
        String format = normalizeFormat(req == null ? null : req.getFormat());
        List<String> tables = tablesOf(scope);
        LocalDateTime now = LocalDateTime.now();
        String ext = "SQL".equals(format) ? "sql" : "json";
        String fileName = fileNameOf(now, ext);

        Path temp = null;
        try {
            temp = Files.createTempFile("issueflow-backup-", "." + ext);
            Map<String, Object> meta = buildMeta(scope, format, now);
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(temp))) {
                if ("SQL".equals(format)) {
                    writeSql(out, tables, meta);
                } else {
                    writeJson(out, tables, meta);
                }
            }
            transfer(temp, fileName, response);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Backup] export failed, scope={}, format={}", scope, format, e);
            throw new BizException(ResultCode.SYSTEM_ERROR, "备份导出失败：" + e.getMessage());
        } finally {
            deleteQuietly(temp);
        }
    }

    /**
     * JSON 格式写出。
     *
     * @param out    输出流
     * @param tables 表清单
     * @param meta   元信息
     * @throws Exception 写出失败
     */
    private void writeJson(OutputStream out, List<String> tables, Map<String, Object> meta) throws Exception {
        try (JsonDumpWriter writer = new JsonDumpWriter(out)) {
            // meta 段只放标量：逐表行数由 tables[].rowCount 承载，
            // 避免在流式写出前先把所有表 count 一遍（多一轮全表扫描）
            writer.begin(meta);
            for (String table : tables) {
                if (!existsTable(table)) {
                    continue;
                }
                List<String> columns = columnsOf(table);
                writer.beginTable(table, columns);
                long written = dumpTable(table, columns, row -> {
                    try {
                        writer.writeRow(row);
                    } catch (Exception e) {
                        throw new BizException(ResultCode.SYSTEM_ERROR,
                                "写出表 " + table + " 失败：" + e.getMessage());
                    }
                });
                writer.endTable();
                guardSize(writer.getApproximateBytes());
                log.info("[Backup] table {} dumped, rows={}", table, written);
            }
            writer.end();
        }
    }

    /**
     * SQL 格式写出。
     *
     * @param out    输出流
     * @param tables 表清单
     * @param meta   元信息
     * @throws Exception 写出失败
     */
    private void writeSql(OutputStream out, List<String> tables, Map<String, Object> meta) throws Exception {
        try (SqlDumpWriter writer = new SqlDumpWriter(out)) {
            writer.writeHeader(meta);
            for (String table : tables) {
                if (!existsTable(table)) {
                    continue;
                }
                List<String> columns = columnsOf(table);
                if (columns.isEmpty()) {
                    continue;
                }
                writer.beginTable(table, columns);
                long written = dumpTable(table, columns, row -> {
                    try {
                        writer.writeRow(row);
                    } catch (Exception e) {
                        throw new BizException(ResultCode.SYSTEM_ERROR,
                                "写出表 " + table + " 失败：" + e.getMessage());
                    }
                });
                writer.endTable();
                guardSize(writer.getApproximateBytes());
                log.info("[Backup] table {} dumped, rows={}", table, written);
            }
            writer.end();
        }
    }

    /**
     * 逐表游标读取并回调写出。
     *
     * @param table    表名（已通过白名单断言）
     * @param columns  列名
     * @param consumer 行消费者
     * @return 实际写出行数
     */
    private long dumpTable(String table, List<String> columns, RowConsumer consumer) {
        assertWhitelisted(table);
        long count = backupMapper.countTable(table);
        if (count > Constants.BACKUP_MAX_ROWS) {
            throw new BizException(ResultCode.SYSTEM_ERROR,
                    "数据量超出备份上限（表 " + table + " 共 " + count + " 行），请联系管理员分批导出");
        }
        long written = 0L;
        if (!columns.contains("id")) {
            // 无 id 主键的表退化为一次性读取（当前 schema 全部表均有 id，此为防御分支）
            for (Map<String, Object> row : backupMapper.selectAllRows(table)) {
                consumer.accept(maskRow(table, row));
                written++;
            }
            return written;
        }
        long lastId = 0L;
        while (true) {
            List<Map<String, Object>> batch =
                    backupMapper.selectPageByCursor(table, lastId, Constants.BACKUP_PAGE_SIZE);
            if (batch == null || batch.isEmpty()) {
                break;
            }
            for (Map<String, Object> row : batch) {
                consumer.accept(maskRow(table, row));
                written++;
                Object id = row.get("id");
                if (id instanceof Number number) {
                    lastId = Math.max(lastId, number.longValue());
                }
            }
            if (batch.size() < Constants.BACKUP_PAGE_SIZE) {
                break;
            }
        }
        return written;
    }

    /**
     * 敏感列脱敏（原地替换值，不改变列结构，保证 SQL/JSON 列数一致）。
     *
     * @param table 表名
     * @param row   行数据
     * @return 脱敏后的行数据
     */
    private Map<String, Object> maskRow(String table, Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return row;
        }
        for (String column : row.keySet()) {
            if (SENSITIVE_COLUMNS.contains(table + "." + column)) {
                row.put(column, MASKED_VALUE);
            }
        }
        return row;
    }

    // ============================ 元信息与文件 ============================

    /**
     * 构造文件头元信息（ARCH §3.9 的 6 类 + 操作人）。
     *
     * @param scope  范围
     * @param format 格式
     * @param now    导出时间
     * @return 有序元信息
     */
    private Map<String, Object> buildMeta(String scope, String format, LocalDateTime now) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("appName", "issueFlow");
        meta.put("appVersion", appVersion());
        meta.put("formatVersion", FORMAT_VERSION);
        meta.put("exportedAt", now.format(META_TS));
        meta.put("scope", scope);
        meta.put("format", format);
        meta.put("operator", operatorLabel());
        meta.put("attachmentBinaryIncluded", false);
        meta.put("passwordMasked", true);
        meta.put("passwordMaskedNote", "user.password 已脱敏为 ***，本备份无法直接用于账号还原");
        meta.put("excludedTables", String.join(",", Constants.BACKUP_EXCLUDED_TABLES));
        return meta;
    }

    /**
     * 应用版本号：优先取 jar 清单里的实现版本，开发态回退常量。
     *
     * @return 版本号
     */
    private String appVersion() {
        String version = BackupService.class.getPackage() == null
                ? null : BackupService.class.getPackage().getImplementationVersion();
        return (version == null || version.isBlank()) ? "1.0.0" : version;
    }

    /**
     * 操作人展示串 {@code admin(id=1)}。
     *
     * @return 操作人标识
     */
    private String operatorLabel() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return "unknown";
        }
        User user = userMapper.selectById(userId);
        String name = user == null ? "unknown" : user.getUsername();
        return name + "(id=" + userId + ")";
    }

    /**
     * 建议文件名 {@code backup_YYYY-MM-DD_HHMMSS.{ext}}。
     *
     * @param now 时间
     * @param ext 扩展名
     * @return 文件名
     */
    private String fileNameOf(LocalDateTime now, String ext) {
        return "backup_" + now.format(FILE_TS) + "." + ext;
    }

    /**
     * 把临时文件回传给客户端。
     *
     * @param temp     临时文件
     * @param fileName 下载文件名
     * @param response HTTP 响应
     * @throws Exception 传输失败
     */
    private void transfer(Path temp, String fileName, HttpServletResponse response) throws Exception {
        long size = Files.size(temp);
        if (size <= 0) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "备份内容为空，已中止下载");
        }
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("application/octet-stream");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition",
                "attachment;filename=\"" + encoded + "\";filename*=UTF-8''" + encoded);
        response.setContentLengthLong(size);
        try (InputStream in = Files.newInputStream(temp);
             OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
            out.flush();
        }
    }

    private void deleteQuietly(Path temp) {
        if (temp == null) {
            return;
        }
        try {
            Files.deleteIfExists(temp);
        } catch (Exception e) {
            log.warn("[Backup] delete temp file failed: {}, msg={}", temp, e.getMessage());
        }
    }

    // ============================ 白名单与校验 ============================

    /**
     * 按范围取表清单。
     *
     * @param scope ALL / CORE
     * @return 表名列表（去重、保序）
     */
    private List<String> tablesOf(String scope) {
        Set<String> tables = new LinkedHashSet<>(CORE_TABLES);
        if ("ALL".equals(scope)) {
            tables.addAll(BUSINESS_TABLES);
        }
        tables.removeAll(Constants.BACKUP_EXCLUDED_TABLES);
        return new ArrayList<>(tables);
    }

    /**
     * 表名白名单断言（防御性双保险，任何进入 {@code ${}} 拼接的表名都必须先过此关）。
     *
     * @param table 表名
     */
    private void assertWhitelisted(String table) {
        if (table == null || (!CORE_TABLES.contains(table) && !BUSINESS_TABLES.contains(table))) {
            throw new BizException(ResultCode.PERMISSION_DENIED, "非法的备份表名：" + table);
        }
    }

    /**
     * 表是否存在（迁移脚本未执行时跳过该表，避免整体导出失败）。
     *
     * @param table 表名
     * @return true 存在
     */
    private boolean existsTable(String table) {
        assertWhitelisted(table);
        try {
            return backupMapper.existsTable(table) > 0;
        } catch (Exception e) {
            log.warn("[Backup] existsTable({}) failed: {}", table, e.getMessage());
            return false;
        }
    }

    /**
     * 取表列名（已排除生成列，生成列不可 INSERT）。
     *
     * @param table 表名
     * @return 列名列表
     */
    private List<String> columnsOf(String table) {
        assertWhitelisted(table);
        List<String> columns = backupMapper.listColumns(table);
        return columns == null ? new ArrayList<>() : columns;
    }

    /**
     * 文件体积上限保护。
     *
     * @param bytes 已写出的估算字节数
     */
    private void guardSize(long bytes) {
        if (bytes > Constants.BACKUP_MAX_BYTES) {
            throw new BizException(ResultCode.SYSTEM_ERROR,
                    "数据量超出备份上限，请联系管理员分批导出");
        }
    }

    private String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return "CORE";
        }
        String value = scope.trim().toUpperCase();
        if (!"ALL".equals(value) && !"CORE".equals(value)) {
            throw new BizException(ResultCode.VALID_ERROR, "备份范围只能是 ALL 或 CORE");
        }
        return value;
    }

    private String normalizeFormat(String format) {
        if (format == null || format.isBlank()) {
            return "JSON";
        }
        String value = format.trim().toUpperCase();
        if (!"JSON".equals(value) && !"SQL".equals(value)) {
            throw new BizException(ResultCode.VALID_ERROR, "备份格式只能是 JSON 或 SQL");
        }
        return value;
    }

    /**
     * 行消费者（把「怎么写」交给格式化器，「怎么读」留在本类）。
     */
    @FunctionalInterface
    private interface RowConsumer {

        /**
         * 消费一行。
         *
         * @param row 行数据
         */
        void accept(Map<String, Object> row);
    }
}
