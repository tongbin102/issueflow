package com.issueflow.dto.fieldconfig.resp;

import lombok.Data;

/**
 * REF 引用源下拉项（配置页选择 refSource 用）。
 */
@Data
public class RefSourceVO {

    /** 引用源编码（大写） */
    private String code;

    /** 名称 */
    private String name;

    /** 查询类型 flat/tree */
    private String queryType;

    /** 默认展示类型 select/tree（来自 query_type 兜底） */
    private String displayType;
}
