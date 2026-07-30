package com.issueflow.dto.req;

import lombok.Data;

import java.io.Serializable;

/**
 * 操作历史查询请求（按操作人 + 时间范围 + 分页）
 */
@Data
public class HistoryQueryReq implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer page = 1;

    private Integer size = 10;

    /** 操作人 id（可空，查全部） */
    private Long operatorId;

    /** 起始时间 yyyy-MM-dd（可空） */
    private String start;

    /** 结束时间 yyyy-MM-dd（可空） */
    private String end;
}
