package com.issueflow.dto.req;

import lombok.Data;

import java.io.Serializable;

/**
 * 个人活动记录分页请求（登录日志 + 本人问题操作记录归并时间线）
 */
@Data
public class ActivityPageReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页，默认 1 */
    private Integer page = 1;

    /** 每页大小，默认 10 */
    private Integer size = 10;

    /** 类型：ALL / LOGIN / ISSUE，默认 ALL */
    private String type = "ALL";

    /** 起始日期 yyyy-MM-dd */
    private String startDate;

    /** 结束日期 yyyy-MM-dd */
    private String endDate;
}
