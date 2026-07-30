package com.issueflow.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 附件视图对象
 */
@Data
public class AttachmentVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long issueId;

    /** 存储名 uuid.ext */
    private String fileName;

    /** 原始文件名 */
    private String originalName;

    /** 存储路径 */
    private String filePath;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 内容类型 image/png 等 */
    private String contentType;

    private Long uploaderId;

    private String uploaderName;

    /** 是否为图片（用于前端是否可预览） */
    private Boolean image;

    /** 下载地址 */
    private String url;

    /** 预览地址（图片内联） */
    private String previewUrl;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
