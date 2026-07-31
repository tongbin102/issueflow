package com.issueflow.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统配置项视图对象
 */
@Data
public class ConfigItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 配置键 */
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 描述 */
    private String description;

    /** 是否系统内置（内置项不可删除，仅可改值） */
    private Boolean builtin;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
