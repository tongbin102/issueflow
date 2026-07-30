package com.issueflow.dto.resp;

import lombok.Data;

import java.io.Serializable;

/**
 * 项目下拉选项（含 status 供前端置灰/标注停用）
 */
@Data
public class ProjectOptionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;
}
