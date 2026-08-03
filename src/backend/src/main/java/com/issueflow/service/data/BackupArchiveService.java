package com.issueflow.service.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.issueflow.common.BizException;
import com.issueflow.common.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * 备份归档打包 / 解包（Phase10 数据管理）。
 *
 * <p>备份包结构固定为：</p>
 * <pre>
 * issueflow_backup_20260803_101530_FULL.zip
 *   ├── manifest.json      元信息（类型 / 库名 / 版本 / 表数 / 生成时间）
 *   ├── db/issueflow_db.sql   数据库 dump（DB_ONLY / FULL 才有）
 *   └── config/*.snapshot     脱敏后的配置快照（CONFIG_ONLY / FULL 才有）
 * </pre>
 *
 * <p><b>解包安全红线 —— Zip Slip</b>：上传恢复的 zip 来自外部，
 * 若条目名是 {@code ../../etc/passwd}，朴素的 {@code new File(dir, entryName)}
 * 会把文件写到备份目录之外，形成任意文件写入漏洞。
 * 因此 {@link #unpack} 对每个条目都做「规范化后必须仍在目标目录内」的校验，
 * 越界一律拒绝并中止整个解包。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupArchiveService {

    /** 包内元信息文件名 */
    public static final String MANIFEST_ENTRY = "manifest.json";

    /** 包内数据库 dump 的条目名 */
    public static final String DB_ENTRY = "db/issueflow_db.sql";

    /** 包内配置目录前缀 */
    public static final String CONFIG_ENTRY_PREFIX = "config/";

    /** 解包时允许的最大解压总字节数（16 GB），兜住 zip 炸弹 */
    private static final long MAX_UNPACK_BYTES = 16L * 1024 * 1024 * 1024;

    /** 解包时允许的最大条目数，兜住海量小文件型 zip 炸弹 */
    private static final int MAX_UNPACK_ENTRIES = 10000;

    private final ObjectMapper objectMapper;

    /**
     * 打开一个 zip 输出流（调用方负责写条目并关闭）。
     *
     * @param target 目标 zip 文件路径
     * @return zip 输出流
     * @throws IOException IO 异常
     */
    public ZipOutputStream openZip(Path target) throws IOException {
        Files.createDirectories(target.getParent());
        return new ZipOutputStream(Files.newOutputStream(target), StandardCharsets.UTF_8);
    }

    /**
     * 向 zip 写入一个文本条目。
     *
     * @param zos     zip 输出流
     * @param name    条目名（使用 / 分隔）
     * @param content 文本内容
     * @throws IOException IO 异常
     */
    public void writeTextEntry(ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    /**
     * 向 zip 写入 manifest.json。
     *
     * @param zos      zip 输出流
     * @param manifest 元信息键值
     * @throws IOException IO 异常
     */
    public void writeManifest(ZipOutputStream zos, Map<String, Object> manifest) throws IOException {
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest);
        writeTextEntry(zos, MANIFEST_ENTRY, json);
    }

    /**
     * 计算文件 SHA-256（小写 hex）。
     *
     * @param file 目标文件
     * @return 摘要串
     * @throws IOException IO 异常
     */
    public String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new BufferedInputStream(Files.newInputStream(file));
                 DigestInputStream dis = new DigestInputStream(in, digest)) {
                byte[] buffer = new byte[8192];
                while (dis.read(buffer) != -1) {
                    // 读完即可，摘要由 DigestInputStream 累积
                }
            }
            return toHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必备算法，走到这里说明 JRE 被裁剪过
            throw new IOException("SHA-256 算法不可用", e);
        }
    }

    /**
     * 校验 zip 结构是否为合法的 issueflow 备份包。
     *
     * <p>判定标准：能被 {@link ZipFile} 正常打开，且含 {@value #MANIFEST_ENTRY}，
     * 同时至少含一个 db 或 config 条目。</p>
     *
     * @param zipPath zip 路径
     * @return manifest 内容（解析失败返回空 Map）
     */
    public Map<String, Object> validateAndReadManifest(Path zipPath) {
        try (ZipFile zipFile = new ZipFile(zipPath.toFile(), StandardCharsets.UTF_8)) {
            ZipEntry manifestEntry = zipFile.getEntry(MANIFEST_ENTRY);
            if (manifestEntry == null) {
                throw new BizException(ResultCode.VALID_ERROR, "备份包缺少 manifest.json，不是有效的 IssueFlow 备份文件");
            }
            boolean hasPayload = zipFile.stream().anyMatch(entry ->
                    DB_ENTRY.equals(entry.getName()) || entry.getName().startsWith(CONFIG_ENTRY_PREFIX));
            if (!hasPayload) {
                throw new BizException(ResultCode.VALID_ERROR, "备份包内既无数据库文件也无配置文件，无法恢复");
            }
            try (InputStream in = zipFile.getInputStream(manifestEntry)) {
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = objectMapper.readValue(json, Map.class);
                return parsed == null ? new LinkedHashMap<>() : parsed;
            } catch (Exception e) {
                log.warn("[BackupArchive] manifest 解析失败，按空处理: {}", e.getClass().getSimpleName());
                return new LinkedHashMap<>();
            }
        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            throw new BizException(ResultCode.VALID_ERROR, "备份包无法读取，可能已损坏或不是 zip 格式");
        }
    }

    /**
     * 解包到指定目录（含 Zip Slip 防护与解压体积上限）。
     *
     * @param zipPath   源 zip
     * @param targetDir 目标目录（不存在则创建）
     * @return 解出的条目名 → 落地绝对路径
     */
    public Map<String, Path> unpack(Path zipPath, Path targetDir) {
        Map<String, Path> extracted = new LinkedHashMap<>();
        Path normalizedTarget = targetDir.toAbsolutePath().normalize();
        try {
            Files.createDirectories(normalizedTarget);
        } catch (IOException e) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "创建解包目录失败");
        }

        long totalBytes = 0L;
        int entryCount = 0;
        try (ZipFile zipFile = new ZipFile(zipPath.toFile(), StandardCharsets.UTF_8)) {
            var entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (++entryCount > MAX_UNPACK_ENTRIES) {
                    throw new BizException(ResultCode.VALID_ERROR, "备份包条目过多，疑似异常文件，已中止解包");
                }
                Path resolved = normalizedTarget.resolve(entry.getName()).normalize();
                // Zip Slip 防护：规范化后必须仍在目标目录内
                if (!resolved.startsWith(normalizedTarget)) {
                    throw new BizException(ResultCode.VALID_ERROR, "备份包内含非法路径条目，已拒绝解包");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(resolved);
                    continue;
                }
                Files.createDirectories(resolved.getParent());
                try (InputStream in = zipFile.getInputStream(entry)) {
                    long written = Files.copy(in, resolved, StandardCopyOption.REPLACE_EXISTING);
                    totalBytes += written;
                    if (totalBytes > MAX_UNPACK_BYTES) {
                        throw new BizException(ResultCode.VALID_ERROR, "备份包解压体积超出上限，已中止解包");
                    }
                }
                extracted.put(entry.getName(), resolved);
            }
        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "备份包解包失败，文件可能已损坏");
        }
        log.info("[BackupArchive] 解包完成 entries={} bytes={}", extracted.size(), totalBytes);
        return extracted;
    }

    /**
     * 把输入流原样写入 zip 的一个条目（用于把 dump 流直接灌进包里）。
     *
     * @param zos  zip 输出流
     * @param name 条目名
     * @return 可写入的输出流视图（调用方写完后须调用 {@link #closeEntry(ZipOutputStream)}）
     * @throws IOException IO 异常
     */
    public OutputStream beginEntry(ZipOutputStream zos, String name) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        return zos;
    }

    /**
     * 结束当前 zip 条目。
     *
     * @param zos zip 输出流
     * @throws IOException IO 异常
     */
    public void closeEntry(ZipOutputStream zos) throws IOException {
        zos.closeEntry();
    }

    /**
     * 字节数组转小写 hex。
     *
     * @param bytes 原始字节
     * @return hex 串
     */
    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            String hex = Integer.toHexString(b & 0xFF);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }
}
