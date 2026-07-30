package com.issueflow.dto.resp;

import lombok.Data;

import java.io.Serializable;

/**
 * 关联问题引用视图对象
 */
@Data
public class IssueRefVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String issueNo;

    private String title;

    private Integer status;
}
