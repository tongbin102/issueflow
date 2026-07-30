package com.issueflow.dto.req;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 问题关联保存请求
 */
@Data
public class IssueRelationReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 前置任务 id 列表（每个 P → 边 (issueId, P)，P 是当前问题的前置） */
    private List<Long> predecessorIds = new ArrayList<>();

    /** 后置任务 id 列表（每个 S → 边 (S, issueId)，S 的后置是当前问题） */
    private List<Long> successorIds = new ArrayList<>();
}
