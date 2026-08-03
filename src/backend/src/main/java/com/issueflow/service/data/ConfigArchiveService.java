package com.issueflow.service.data;

import com.issueflow.config.DataManagementProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置文件归档（Phase10 数据管理）。
 *
 * <p>把 {@code issueflow.data-management.config-files} 列出的配置文件读出来，
 * <b>逐行脱敏</b>后作为 {@code config/*.snapshot} 条目打进备份包。</p>
 *
 * <p><b>为什么要逐行处理而不是整文件加密</b>：备份包的价值之一是
 * 「出事时人能打开看看当时配置长什么样」。整体加密会让这个用途消失，
 * 而逐行脱敏保留了结构与非敏感项的可读性，只把 value 这一侧抹掉。</p>
 *
 * <p>支持两种常见格式的键值识别：</p>
 * <ul>
 *   <li>YAML / properties：{@code key: value} 与 {@code key=value}</li>
 *   <li>dotenv：{@code KEY=value}</li>
 * </ul>
 * 无法识别为键值的行（注释、列表项、纯文本）原样保留。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigArchiveService {

    private final DataManagementProperties properties;
    private final SensitiveMaskService sensitiveMaskService;

    /** 单个配置文件允许的最大字节数（2 MB），超出只保留提示，避免把日志类大文件打进包 */
    private static final long MAX_FILE_BYTES = 2L * 1024 * 1024;

    /**
     * 收集并脱敏全部配置文件。
     *
     * @return 条目名（不含 {@code config/} 前缀） → 脱敏后的文本内容；无可用文件时返回空 Map
     */
    public Map<String, String> collectMaskedConfigs() {
        Map<String, String> result = new LinkedHashMap<>();
        for (Path path : resolveConfigPaths()) {
            try {
                if (!Files.exists(path) || !Files.isRegularFile(path)) {
                    // 不存在的条目静默跳过：不同部署形态配置文件位置本就不同
                    continue;
                }
                if (Files.size(path) > MAX_FILE_BYTES) {
                    result.put(entryNameOf(path, result),
                            "# 该配置文件超过 2MB，已跳过归档以避免备份包膨胀\n");
                    continue;
                }
                List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                result.put(entryNameOf(path, result), maskLines(lines));
            } catch (IOException e) {
                log.warn("[ConfigArchive] 读取配置文件失败，已跳过: {}", e.getClass().getSimpleName());
            }
        }
        log.info("[ConfigArchive] 配置快照收集完成，文件数={}", result.size());
        return result;
    }

    /**
     * 逐行脱敏。
     *
     * @param lines 原始行
     * @return 脱敏后的整段文本
     */
    public String maskLines(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        sb.append("# IssueFlow 配置快照（敏感项已脱敏，切勿直接用于生产覆盖）\n");
        for (String line : lines) {
            sb.append(maskLine(line)).append('\n');
        }
        return sb.toString();
    }

    /**
     * 单行脱敏。
     *
     * @param line 原始行，可为 null
     * @return 脱敏后的行
     */
    public String maskLine(String line) {
        if (line == null || line.isEmpty()) {
            return "";
        }
        String trimmed = line.trim();
        // 注释与空行原样保留
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) {
            return line;
        }
        int sep = indexOfSeparator(line);
        if (sep < 0) {
            return line;
        }
        String rawKey = line.substring(0, sep).trim();
        // 去掉 yaml 的列表符号与 dotenv 的 export 前缀，拿到干净的 key
        String key = rawKey.replaceFirst("^-\\s*", "").replaceFirst("^export\\s+", "").trim();
        if (key.isEmpty() || !sensitiveMaskService.isSensitive(key)) {
            return line;
        }
        String value = line.substring(sep + 1).trim();
        if (value.isEmpty()) {
            return line;
        }
        String masked = sensitiveMaskService.process(key, stripQuotes(value));
        return line.substring(0, sep + 1) + " " + masked;
    }

    /**
     * 解析配置文件路径列表。
     *
     * @return 路径列表，可能为空
     */
    public List<Path> resolveConfigPaths() {
        List<Path> paths = new ArrayList<>();
        String raw = properties.getConfigFiles();
        if (raw == null || raw.trim().isEmpty()) {
            return paths;
        }
        for (String item : raw.split(",")) {
            String candidate = item.trim();
            if (candidate.isEmpty()) {
                continue;
            }
            try {
                paths.add(Paths.get(candidate));
            } catch (Exception e) {
                log.warn("[ConfigArchive] 配置文件路径非法，已跳过: {}", e.getClass().getSimpleName());
            }
        }
        return paths;
    }

    /**
     * 找到 key 与 value 的分隔符位置（冒号或等号，取更靠前的那个）。
     *
     * @param line 原始行
     * @return 分隔符下标；未找到返回 -1
     */
    private int indexOfSeparator(String line) {
        int colon = line.indexOf(':');
        int equal = line.indexOf('=');
        if (colon < 0) {
            return equal;
        }
        if (equal < 0) {
            return colon;
        }
        return Math.min(colon, equal);
    }

    /**
     * 去掉值两侧的引号。
     *
     * @param value 原始值
     * @return 去引号后的值
     */
    private String stripQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    /**
     * 生成包内条目名，重名时追加序号。
     *
     * @param path     源文件路径
     * @param existing 已有条目
     * @return 唯一条目名
     */
    private String entryNameOf(Path path, Map<String, String> existing) {
        Path fileName = path.getFileName();
        String base = fileName == null ? "config" : fileName.toString();
        String candidate = base + ".snapshot";
        int index = 1;
        while (existing.containsKey(candidate)) {
            candidate = base + "." + index + ".snapshot";
            index++;
        }
        return candidate;
    }
}
