package com.issueflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.issueflow.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * REF 字段引用源白名单（Q7 + A1）。
 * <p>对应表 {@code ref_source_registry}。前端只传 {@code code}，永不传表名/列名；
 * 后端经「正则 + information_schema」双校验后，方可将 {@code table_name/label_field/...}
 * 拼入动态 SQL（详见 {@code RefSourceService} / {@code RefSourceStartupValidator}）。</p>
 *
 * <p>表存在生成列 {@code code_active = IF(deleted=0, code, NULL)} 用于条件唯一，不映射本实体。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ref_source_registry")
public class RefSourceRegistry extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 引用源编码（大写），前端只传此值，永不传表名 */
    private String code;

    /** 引用源名称（配置页下拉展示） */
    private String name;

    /** 目标表名，须过正则 + information_schema 校验 */
    private String tableName;

    /** 展示列（下拉 label） */
    private String labelField;

    /** 取值列（下拉 value），默认 id */
    private String valueField;

    /** flat 平铺列表 / tree 树形 */
    private String queryType;

    /** 树形自关联父列，query_type=tree 时必填 */
    private String parentField;

    /** 依赖过滤列：被 depends_on 触发时用于 WHERE 的列 */
    private String filterField;

    /** 排序列，为空时按 value_field 升序 */
    private String orderField;

    /** 1 启用 / 0 停用 */
    private Integer enabled;
}
