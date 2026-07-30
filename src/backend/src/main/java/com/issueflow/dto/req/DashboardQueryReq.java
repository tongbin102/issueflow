package com.issueflow.dto.req;

import lombok.Data;

import java.io.Serializable;

/**
 * 看板查询请求（时间范围 + 版本维度）
 */
@Data
public class DashboardQueryReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 起始日期 yyyy-MM-dd（可空） */
    private String start;

    /** 结束日期 yyyy-MM-dd（可空） */
    private String end;

    /** 应用版本维度（可空，过滤 env_app_version） */
    private String version;
}
