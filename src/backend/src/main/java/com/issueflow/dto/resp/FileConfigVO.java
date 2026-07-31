package com.issueflow.dto.resp;

import lombok.Data;

import java.io.Serializable;

/**
 * 文件配置视图对象（含存储占用统计）
 */
@Data
public class FileConfigVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 存储根目录 */
    private String storageRoot;

    /** 单文件大小上限（MB） */
    private Integer maxSizeMb;

    /** 允许的扩展名，逗号分隔 */
    private String allowedExts;

    /** 存储类型 */
    private String storageType;

    /** 已用空间（字节） */
    private Long usedSize;

    /** 文件总数 */
    private Long fileCount;

    /** 存储根目录当前是否可写（页面给出告警） */
    private Boolean writable;
}
