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
