package com.issueflow.dto.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 文件配置保存请求（写入 sys_config 的 file.* 4 键）
 */
@Data
public class FileConfigReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 存储根目录（必须绝对路径，保存时校验可写） */
    @NotBlank(message = "存储根目录不能为空")
    @Size(max = 255, message = "存储根目录不能超过 255 字")
    private String storageRoot;

    /** 单文件大小上限（MB），1-100 */
    @NotNull(message = "单文件大小上限不能为空")
    @Min(value = 1, message = "单文件大小上限不能小于 1MB")
    @Max(value = 100, message = "单文件大小上限不能大于 100MB")
    private Integer maxSizeMb;

    /** 允许的扩展名，逗号分隔（小写，不含点） */
    @NotBlank(message = "允许的扩展名不能为空")
    @Size(max = 500, message = "允许的扩展名不能超过 500 字")
    private String allowedExts;

    /** 存储类型，目前仅 LOCAL */
    @NotBlank(message = "存储类型不能为空")
    @Pattern(regexp = "^LOCAL$", message = "存储类型目前仅支持 LOCAL")
    private String storageType = "LOCAL";
}
