package com.issueflow.dto.req;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 模块移动请求（Q3 即拖即存：一次拖拽 = 一个接口）。
 */
@Data
public class ModuleMoveReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标父模块 id，空或 0 表示根级 */
    private Long targetParentId;

    /**
     * 拖拽完成后目标层级的完整有序 id 列表（含被拖节点自身）。
     * 后端按此顺序把目标层 sort 全量重排为 1..n；为空时被拖节点挂到目标层末尾。
     */
    private List<Long> orderedSiblingIds;
}
