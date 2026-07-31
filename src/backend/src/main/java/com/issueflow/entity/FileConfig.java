package com.issueflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.issueflow.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件存储配置（Phase 7 新增，全局唯一一行）
 * <p>采用独立表而非 sys_config 的 file.* 键，保证「文件配置」只有一个真源，
 * 避免两处存储不一致（本期与 ARCH §3.7 的差异，见交付说明）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("file_config")
public class FileConfig extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 存储根目录，必须为绝对路径 */
    private String storageRoot;

    /** 单文件大小上限(MB)，取值 1-100 */
    private Integer maxSizeMb;

    /** 允许的扩展名，逗号分隔小写 */
    private String allowedExts;

    /** 存储方式：LOCAL（预留 OSS） */
    private String storageType;
}
