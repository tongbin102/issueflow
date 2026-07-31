package com.issueflow.util;

import com.issueflow.common.BizException;
import com.issueflow.common.Constants;
import com.issueflow.common.ResultCode;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

/**
 * 附件存储工具：落盘 /data/attachments/{yyyyMM}/{uuid}.{ext}，校验大小与类型
 */
@Component
public class FileUtil {

    /** 附件存储根目录（可被 application.yml 的 app.attachment-base-path 覆盖，默认 /data/attachments） */
    @Value("${app.attachment-base-path:/data/attachments}")
    private String baseDir;

    private Path basePath;

    @PostConstruct
    public void init() {
        this.basePath = Paths.get(baseDir);
    }

    /**
     * 存储结果封装
     *
     * @param fileName    存储名 uuid.ext
     * @param filePath    完整存储路径
     * @param fileSize    字节数
     * @param contentType 内容类型
     */
    public record StoredFile(String fileName, String filePath, long fileSize, String contentType) {
    }

    /**
     * 存储上传文件
     *
     * @param file 上传文件
     * @return 存储结果
     */
    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.VALID_ERROR, "文件内容为空");
        }
        if (file.getSize() > Constants.MAX_ATTACHMENT_SIZE) {
            throw new BizException(ResultCode.FILE_TOO_LARGE);
        }
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            throw new BizException(ResultCode.VALID_ERROR, "文件类型不允许（content_type 为空）");
        }

        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf("."));
        }
        String yyyyMM = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String fileName = UUID.randomUUID().toString().replace("-", "") + ext;

        Path dir = basePath.resolve(yyyyMM);
        Path target = dir.resolve(fileName);
        try {
            Files.createDirectories(dir);
            file.transferTo(target.toFile());
        } catch (IOException e) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "文件存储失败：" + e.getMessage());
        }
        return new StoredFile(fileName, target.toString(), file.getSize(), contentType);
    }

    /**
     * 判断内容类型是否为图片（可内联预览）
     */
    public boolean isImage(String contentType) {
        return contentType != null && contentType.startsWith("image/");
    }

    // ======================= Phase 7 新增（统一文件管理） =======================

    /**
     * Phase 7 存储结果：同时携带相对路径与绝对路径。
     *
     * <p>相对路径入库（{@code file_record.relative_path}），迁移存储根后仍可定位；
     * 绝对路径仅供本次调用即时使用与兼容存量数据。</p>
     *
     * @param fileName     存储名 uuid.ext
     * @param relativePath 相对存储根的路径 yyyyMM/uuid.ext
     * @param absolutePath 绝对路径
     * @param fileSize     字节数
     * @param contentType  内容类型
     * @param ext          小写扩展名（不含点），无扩展名时为空串
     */
    public record StoredObject(String fileName,
                               String relativePath,
                               String absolutePath,
                               long fileSize,
                               String contentType,
                               String ext) {
    }

    /**
     * 路径穿越安全解析（ARCH §7.8 硬约束）。
     *
     * <p>断言 {@code normalize()} 后的绝对路径仍位于 {@code base} 之下，
     * 拦截 {@code ../../etc/passwd} 一类构造。<b>任何由外部输入参与拼装的路径
     * 都必须经过本方法</b>，不得直接 {@code Paths.get(userInput)}。</p>
     *
     * @param base     存储根目录（绝对路径）
     * @param relative 相对路径，可为 null（视为根目录本身）
     * @return 归一化后的安全绝对路径
     * @throws BizException 根目录未配置或路径越界
     */
    public static Path resolveSafe(String base, String relative) {
        if (base == null || base.isBlank()) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "存储根目录未配置");
        }
        Path root = Paths.get(base).toAbsolutePath().normalize();
        if (relative == null || relative.isBlank()) {
            return root;
        }
        // 统一分隔符并剥离前导斜杠，避免 resolve 时被当成绝对路径直接替换 root
        String cleaned = relative.replace('\\', '/').trim();
        while (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        Path target = root.resolve(cleaned).toAbsolutePath().normalize();
        if (!target.startsWith(root)) {
            throw new BizException(ResultCode.PERMISSION_DENIED, "非法的文件路径");
        }
        return target;
    }

    /**
     * 按运行期文件配置存储上传文件（Phase 7 统一入口）。
     *
     * <p>与旧版 {@link #store(MultipartFile)} <b>并存</b>：旧方法服务于存量问题附件链路
     * （零回归），本方法服务于统一文件管理 / 头像 —— 大小上限与扩展名白名单来自
     * {@code file_config} 表，改配置后<b>无需重启</b>即刻生效。</p>
     *
     * @param file        上传文件
     * @param storageRoot 存储根目录（绝对路径）
     * @param maxBytes    单文件字节上限，&le;0 表示不限制
     * @param allowedExts 允许的小写扩展名集合；null 或空集合表示不限制
     * @return 存储结果
     * @throws BizException 文件为空 / 超限 / 扩展名不允许 / 落盘失败
     */
    public StoredObject storeTo(MultipartFile file,
                                String storageRoot,
                                long maxBytes,
                                Set<String> allowedExts) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.VALID_ERROR, "文件内容为空");
        }
        if (maxBytes > 0 && file.getSize() > maxBytes) {
            throw new BizException(ResultCode.FILE_TOO_LARGE,
                    "文件大小超过上限 " + (maxBytes / 1024 / 1024) + "MB");
        }
        String original = file.getOriginalFilename();
        String ext = extensionOf(original);
        if (allowedExts != null && !allowedExts.isEmpty() && !allowedExts.contains(ext)) {
            throw new BizException(ResultCode.VALID_ERROR,
                    "不允许的文件类型：" + (ext.isEmpty() ? "(无扩展名)" : ext));
        }

        String yyyyMM = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String fileName = UUID.randomUUID().toString().replace("-", "")
                + (ext.isEmpty() ? "" : "." + ext);
        String relativePath = yyyyMM + "/" + fileName;

        Path target = resolveSafe(storageRoot, relativePath);
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target.toFile());
        } catch (IOException e) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "文件存储失败：" + e.getMessage());
        }
        String contentType = (file.getContentType() == null || file.getContentType().isBlank())
                ? "application/octet-stream" : file.getContentType();
        return new StoredObject(fileName, relativePath, target.toString(),
                file.getSize(), contentType, ext);
    }

    /**
     * 取小写扩展名（不含点）。
     *
     * @param fileName 文件名，可为 null
     * @return 小写扩展名；无扩展名返回空串
     */
    public static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase();
    }

    /**
     * 探测存储根目录当前是否可写（文件配置页给出告警）。
     *
     * @param storageRoot 存储根目录
     * @return true 目录存在（或可创建）且可写
     */
    public static boolean isWritable(String storageRoot) {
        if (storageRoot == null || storageRoot.isBlank()) {
            return false;
        }
        try {
            Path root = Paths.get(storageRoot).toAbsolutePath().normalize();
            if (!Files.exists(root)) {
                Files.createDirectories(root);
            }
            return Files.isDirectory(root) && Files.isWritable(root);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 删除磁盘文件（忽略失败）
     */
    public void deleteFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(filePath));
        } catch (IOException ignored) {
            // 文件可能已不存在，忽略
        }
    }
}
