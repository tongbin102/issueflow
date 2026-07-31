package com.issueflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.issueflow.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 统一文件记录（Phase 7 新增）
 * <p>与 {@link IssueAttachment} <b>并存</b>：问题详情仍读 issue_attachment（零回归），
 * file_record 提供后台统一文件视图。新增问题附件时双写。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("file_record")
public class FileRecord extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 存储名 uuid.ext */
    private String fileName;

    /** 原始文件名 */
    private String originalName;

    /** 相对存储根的路径 yyyyMM/uuid.ext（迁移存储根时不失效） */
    private String relativePath;

    /** 绝对路径（兼容存量 issue_attachment 回灌） */
    private String filePath;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 内容类型 */
    private String contentType;

    /** 小写扩展名，供筛选 */
    private String ext;

    /** 业务类型：ISSUE / AVATAR / MANUAL */
    private String bizType;

    /** 关联业务 id */
    private Long bizId;

    /** 上传者 user.id */
    private Long uploaderId;

    /** 存储类型：LOCAL（预留 OSS） */
    private String storageType;
}
