package com.issueflow.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 项目新建/编辑请求
 */
@Data
public class ProjectReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 项目名称（唯一） */
    @NotBlank(message = "项目名称不能为空")
    private String name;

    /** 项目描述 */
    private String description;

    /** 状态：1 启用 / 0 停用，默认 1 */
    private Integer status = 1;

    /** 负责人 id（user.id），非必填 */
    private Long leaderId;

    /** 项目成员 id 列表，逗号分隔，非必填 */
    private String memberIds;
}
