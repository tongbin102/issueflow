package com.issueflow.dto.req;

import lombok.Data;

import java.io.Serializable;

/**
 * 文件列表分页 + 筛选请求
 */
@Data
public class FilePageReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页，默认 1 */
    private Integer page = 1;

    /** 每页大小，默认 10 */
    private Integer size = 10;

    /** 关键词（原始文件名模糊匹配） */
    private String keyword;

    /** 扩展名筛选（小写，不含点） */
    private String ext;

    /** 业务类型筛选：ISSUE / AVATAR / MANUAL */
    private String bizType;

    /** 起始日期 yyyy-MM-dd（按 created_at） */
    private String startDate;

    /** 结束日期 yyyy-MM-dd（按 created_at） */
    private String endDate;
}
