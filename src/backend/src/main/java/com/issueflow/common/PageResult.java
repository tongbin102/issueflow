package com.issueflow.common;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分页返回体
 *
 * @param <T> 数据类型
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页数据列表 */
    private List<T> list;

    /** 总记录数 */
    private Long total;

    /** 当前页码(从 1 开始) */
    private Long page;

    /** 每页大小 */
    private Long size;

    public PageResult() {
    }

    public PageResult(List<T> list, Long total, Long page, Long size) {
        this.list = list;
        this.total = total;
        this.page = page;
        this.size = size;
    }

    /**
     * 构造分页结果
     */
    public static <T> PageResult<T> of(List<T> list, Long total, Long page, Long size) {
        return new PageResult<>(list, total, page, size);
    }
}
