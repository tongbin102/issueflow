package com.issueflow.dto.fieldconfig.resp;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 字段配置视图（schema 契约 + 管理列表共用）。
 * <p>对外 JSON 输出 {@code system}（A4 列名 is_system 与契约一致）。</p>
 */
@Data
public class FieldConfigVO {

    private Long id;
    private Long sectionId;
    private String code;
    private String name;
    private String i18nKey;
    /** 字段类型：TEXT/NUMBER/DATE/DATETIME/DICT/REF */
    private String type;
    private Boolean required;
    private String placeholder;
    private String defaultValue;
    private Integer span;
    private Boolean multiline;
    private Integer maxLength;
    private BigDecimal minVal;
    private BigDecimal maxVal;
    private Integer decimalScale;
    private String dictCode;
    private String refSource;
    private String displayType;
    private Boolean multiSelect;
    private String dependsOn;
    private String dependsParam;
    /** A4：列名 is_system，JSON 输出 system */
    @JsonProperty("system")
    private Boolean system;
    private Boolean enabled;
    private Boolean visibleInList;
    private Boolean searchable;
    private String typeScope;
    private Integer sort;
}
