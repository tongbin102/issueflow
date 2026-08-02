package com.issueflow.dto.fieldconfig.resp;

import lombok.Data;

import java.util.List;

/**
 * schema 区域节点（含 fields）。
 */
@Data
public class FieldSchemaSectionVO {

    private String code;
    private String name;
    private String i18nKey;
    private Integer sort;
    private List<FieldConfigVO> fields;
}
