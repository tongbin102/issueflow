package com.issueflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.issueflow.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字段区域（表单分区 / 页签）。
 * <p>对应表 {@code field_section}。表存在生成列
 * {@code code_active = IF(deleted=0, code, NULL)} 用于「未删除行 code 条件唯一」，
 * 该列<b>不映射</b>到本实体（避免 MyBatis-Plus 写入生成列导致 SQL 报错）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("field_section")
public class FieldSection extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 区域编码（大写下划线），程序依赖，创建后不可改 */
    private String code;

    /** 区域名称（页签标题，i18n 缺失时兜底文案） */
    private String name;

    /** i18n key，如 field.section.BASIC；为空则回退 name */
    private String i18nKey;

    /** 生效范围：本期恒为 GLOBAL；P2-F16 存 issue 类型 code */
    private String typeScope;

    /** 升序展示（页签左右顺序） */
    private Integer sort;

    /** 1 启用 / 0 停用 */
    private Integer enabled;

    /** 1 系统预设区域，删除接口硬拦截，仅可改名/排序 */
    private Integer isSystem;
}
