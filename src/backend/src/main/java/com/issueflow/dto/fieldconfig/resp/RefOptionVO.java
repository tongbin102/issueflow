package com.issueflow.dto.fieldconfig.resp;

import lombok.Data;

import java.util.List;

/**
 * REF 候选选项（{@code GET /api/field-configs/ref-options} 返回）。
 * <p>flat：children 为 null/空；tree：children 递归填充。</p>
 */
@Data
public class RefOptionVO {

    /** 取值（可能是 Long 或 String，取决于目标表 value_field 类型） */
    private Object value;

    /** 展示文案 */
    private String label;

    /** 树形子节点（flat 为 null） */
    private List<RefOptionVO> children;
}
