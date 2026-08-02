package com.issueflow.dto.fieldconfig.resp;

import lombok.Data;

import java.util.List;

/**
 * 字段区域视图（schema 契约 + 管理列表共用）。
 */
@Data
public class FieldSectionVO {

    private Long id;
    private String code;
    private String name;
    private String i18nKey;
    private String typeScope;
    private Integer sort;
    private Boolean enabled;
    private Boolean isSystem;
    /** 区域内字段（schema / 管理列表下挂） */
    private List<FieldConfigVO> fields;
}
