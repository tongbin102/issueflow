package com.issueflow.dto.fieldconfig.resp;

import lombok.Data;

import java.util.List;

/**
 * 表单渲染契约（{@code GET /api/field-configs/schema} 返回）。
 * <p>sections 与 fields 均已按 sort 升序；enabled=false 字段仍下发（详情只读展示）；
 * deleted=1 不下发；systemTabs 恒定追加（不入库）。</p>
 */
@Data
public class FieldSchemaVO {

    /** 生效范围（本期恒 GLOBAL） */
    private String typeScope;

    /** 全量字段配置最大 updated_at 毫秒，前端可做本地缓存比对 */
    private Long version;

    /** 区域列表（含 fields） */
    private List<FieldSchemaSectionVO> sections;

    /** 系统固定页签：relation / history / attachment（不入库，恒定追加） */
    private List<String> systemTabs;
}
