package com.issueflow.enums;

/**
 * REF 引用源查询类型（对应 {@code ref_source_registry.query_type}）。
 * <p>flat=平铺列表；tree=树形（依赖 parent_field 自关联）。</p>
 */
public enum RefQueryType {

    /** 平铺列表 */
    FLAT,
    /** 树形 */
    TREE;

    /**
     * 按枚举名解析（忽略大小写）。
     *
     * @param code 查询类型字符串
     * @return 匹配枚举，非法返回 FLAT（兜底）
     */
    public static RefQueryType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return FLAT;
        }
        try {
            return RefQueryType.valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return FLAT;
        }
    }
}
