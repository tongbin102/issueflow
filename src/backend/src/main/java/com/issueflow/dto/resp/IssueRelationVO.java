package com.issueflow.dto.resp;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 问题关联视图对象
 */
@Data
public class IssueRelationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 前置任务列表 */
    private List<IssueRefVO> predecessors = new ArrayList<>();

    /** 后置任务列表（反向推导） */
    private List<IssueRefVO> successors = new ArrayList<>();
}
