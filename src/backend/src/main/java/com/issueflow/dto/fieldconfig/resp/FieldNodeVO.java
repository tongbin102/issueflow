package com.issueflow.dto.fieldconfig.resp;

import lombok.Data;

/**
 * 管理页树形表格节点（区域为父 / 字段为子，row-key 前缀防撞号）。
 * <p>前端用 {@code nodeType} 区分渲染；区域行不展示 type/refSource/dependsOn。</p>
 */
@Data
public class FieldNodeVO {

    /** 节点主键（区域或字段 id） */
    private Long id;

    /** 父节点 id（区域为 null） */
    private Long parentId;

    /** 区域编码 / 字段编码 */
    private String code;

    /** 名称 */
    private String name;

    /** 节点类型：section / field */
    private String nodeType;

    /** 字段类型（区域为 null） */
    private String type;

    /** REF 源编码（区域为 null） */
    private String refSource;

    /** 依赖上游字段 code（区域为 null） */
    private String dependsOn;

    /** 排序 */
    private Integer sort;

    /** 启用 */
    private Boolean enabled;

    /** 系统内置（删除/改编码/改类型硬拦截） */
    private Boolean system;
}
