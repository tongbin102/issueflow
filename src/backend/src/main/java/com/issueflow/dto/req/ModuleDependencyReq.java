package com.issueflow.dto.req;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 模块依赖设置请求（全量替换语义：空数组 / null = 清空该模块的全部依赖）。
 */
@Data
public class ModuleDependencyReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 本模块所依赖的模块 id 列表（单向 A→B，仅展示语义） */
    private List<Long> dependsOnIds;
}
