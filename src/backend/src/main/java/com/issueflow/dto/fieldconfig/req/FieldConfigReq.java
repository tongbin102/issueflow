package com.issueflow.dto.fieldconfig.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 字段配置保存/编辑请求（对应 field_config 可写属性）。
 * <p><b>Q4 铁律</b>：{@code code} 创建后不可改；{@code type} 编辑时与服务端不一致将抛
 * {@code FIELD_TYPE_IMMUTABLE}（Service 层强制忽略入参 type 并校验）。</p>
 */
@Data
public class FieldConfigReq {

    /** 所属区域 id（field_section.id） */
    private Long sectionId;

    /** 字段编码（小驼峰），全局唯一，创建后不可改 */
    @NotBlank(message = "字段编码不能为空")
    private String code;

    /** 字段标签 */
    @NotBlank(message = "字段名称不能为空")
    private String name;

    /** i18n key，如 field.label.title */
    private String i18nKey;

    /** 字段类型：TEXT/NUMBER/DATE/DATETIME/DICT/REF（创建后不可改） */
    @NotBlank(message = "字段类型不能为空")
    private String type;

    /** 1 必填 / 0 选填 */
    private Integer required;

    /** 占位提示 */
    private String placeholder;

    /** 默认值 */
    private String defaultValue;

    /** 栅格宽度 1~24 */
    private Integer span;

    /** TEXT 专用：1=textarea / 0=input */
    private Integer multiline;

    /** TEXT 专用：最大字符数 */
    private Integer maxLength;

    /** NUMBER 专用：最小值 */
    private BigDecimal minVal;

    /** NUMBER 专用：最大值 */
    private BigDecimal maxVal;

    /** NUMBER 专用：小数位数 */
    private Integer decimalScale;

    /** DICT 专用：dict_code */
    private String dictCode;

    /** REF 专用：ref_source_registry.code */
    private String refSource;

    /** REF 专用：select / tree */
    private String displayType;

    /** 1 多选 / 0 单选 */
    private Integer multiSelect;

    /** 依赖的上游字段 code */
    private String dependsOn;

    /** 传给 ref-options 的过滤参数名 */
    private String dependsParam;

    /** 生效范围：本期恒 GLOBAL（Q1） */
    private String typeScope;

    /** 区域内排序 */
    private Integer sort;

    /** 1 启用 / 0 停用 */
    private Integer enabled;

    /** F14：是否可作为列表列 */
    private Integer visibleInList;

    /** F14：是否可作为查询条件 */
    private Integer searchable;
}
