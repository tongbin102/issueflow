package com.issueflow.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件记录视图对象
 */
@Data
public class FileRecordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 原始文件名 */
    private String originalName;

    /** 小写扩展名 */
    private String ext;

    /** 内容类型 */
    private String contentType;

    /** 文件大小（字节，前端格式化） */
    private Long fileSize;

    /** 上传者展示名 */
    private String uploaderName;

    /** 业务类型 ISSUE / AVATAR / MANUAL */
    private String bizType;

    /** 关联业务 id */
    private Long bizId;

    /** 业务引用展示串，如 IS-20260810-0312（批量回填，禁 N+1） */
    private String bizRef;

    /** 相对路径 */
    private String relativePath;

    /** 是否可在线预览（图片类） */
    private Boolean previewable;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
