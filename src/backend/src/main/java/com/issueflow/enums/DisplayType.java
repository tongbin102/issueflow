package com.issueflow.enums;

/**
 * REF 字段展示类型（ARCH §7.1）。
 * <p>{@code field_config.display_type} 取值；为空时由 {@code ref_source_registry.query_type} 兜底。</p>
 */
public enum DisplayType {

    /** 平铺下拉（el-select） */
    SELECT,
    /** 树形下拉（el-tree-select） */
    TREE;

    /**
     * 按枚举名解析（忽略大小写）。
     *
     * @param code 展示类型字符串
     * @return 匹配枚举，或 null（空值合法，由 registry 兜底）
     */
    public static DisplayType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return DisplayType.valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
