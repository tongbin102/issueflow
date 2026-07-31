package com.issueflow.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 字典类型列表视图对象
 */
@Data
public class DictTypeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 类型名称 */
    private String name;

    /** 类型编码 */
    private String code;

    /** 描述 */
    private String description;

    /** 排序号 */
    private Integer sort;

    /** 是否启用 */
    private Boolean enabled;

    /** 是否系统预设（预设类型不可删除、code 不可改） */
    private Boolean isSystem;

    /** 是否为系统枚举镜像类型（页面只读提示、隐藏「新增选项」） */
    private Boolean mirror;

    /** 该类型下未删除选项数量 */
    private Long itemCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
