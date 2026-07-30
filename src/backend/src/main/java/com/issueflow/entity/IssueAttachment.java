package com.issueflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.issueflow.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 问题附件表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("issue_attachment")
public class IssueAttachment extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 关联问题 id */
    private Long issueId;

    /** 存储名（uuid.ext） */
    private String fileName;

    /** 原始文件名 */
    private String originalName;

    /** 存储路径 /data/attachments/... */
    private String filePath;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 内容类型 image/png 等 */
    private String contentType;

    /** 上传者 id */
    private Long uploaderId;
}
