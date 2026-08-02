package com.issueflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.issueflow.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 字段配置（动态表单元数据，本期核心表）。
 * <p>对应表 {@code field_config}。表存在生成列
 * {@code code_active = IF(deleted=0, code, NULL)} 用于「全局 code 条件唯一」，
 * 该列<b>不映射</b>到本实体。</p>
 *
 * <p>设计要点（与架构文档一致）：
 * <ul>
 *   <li>{@code type} 以 {@code String} 存储枚举名（与 dict 风格一致），Service 层转 {@code FieldType}；创建后不可改（Q4）。</li>
 *   <li>{@code is_system} 列名在对外 JSON 中输出为 {@code system}（A4）。</li>
 *   <li>{@code code} 全局唯一，创建后不可改；{@code is_system=1} 时须与 Issue 实体属性同名（Q2）。</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("field_config")
public class FieldConfig extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属区域 field_section.id（无外键） */
    private Long sectionId;

    /** 字段编码（小驼峰），全局唯一，创建后不可改 */
    private String code;

    /** 字段标签（i18n 缺失时兜底） */
    private String name;

    /** i18n key，如 field.label.title */
    private String i18nKey;

    /** 字段类型：TEXT/NUMBER/DATE/DATETIME/DICT/REF（枚举名字符串，创建后不可改） */
    private String type;

    /** 1 必填 / 0 选填 */
    private Integer required;

    /** 占位提示 */
    private String placeholder;

    /** 默认值（字符串形态，按 type 解析） */
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

    /** NUMBER 专用：小数位数，NULL=整数 */
    private Integer decimalScale;

    /** DICT 专用：dict.dict_code */
    private String dictCode;

    /** REF 专用：ref_source_registry.code 白名单编码 */
    private String refSource;

    /** REF 专用：select 平铺 / tree 树形 */
    private String displayType;

    /** 1 多选 / 0 单选（DICT/REF 有效） */
    private Integer multiSelect;

    /** 依赖的上游字段 code（本期单级，Q6） */
    private String dependsOn;

    /** 传给 ref-options 的过滤参数名；为空取 registry.filter_field */
    private String dependsParam;

    /** 1 内置字段（F12）：仅可改 name/i18n_key/required/sort/placeholder/span */
    private Integer isSystem;

    /** F14 元数据：是否可作为列表列（本期只落库） */
    private Integer visibleInList;

    /** F14 元数据：是否可作为查询条件（本期只落库） */
    private Integer searchable;

    /** P2-F19 预留：字段级权限标识，本期恒 NULL */
    private String permCode;

    /** 生效范围：本期恒 GLOBAL（Q1） */
    private String typeScope;

    /** 区域内升序展示 */
    private Integer sort;

    /** 1 启用 / 0 停用 */
    private Integer enabled;

    /**
     * 对外 JSON 输出 {@code system}（A4：列名 is_system 与既有风格一致，契约不变）。
     *
     * @return 是否系统内置
     */
    @JsonProperty("system")
    public Integer getIsSystem() {
        return isSystem;
    }
}
