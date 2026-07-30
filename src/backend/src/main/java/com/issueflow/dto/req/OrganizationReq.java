package com.issueflow.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 组织新建/编辑请求
 */
@Data
public class OrganizationReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 组织名称 */
    @NotBlank(message = "组织名称不能为空")
    private String name;

    /** 父级 id，0 表示根，默认 0 */
    private Long parentId = 0L;

    /** 排序号，升序展示，默认 0 */
    private Integer sort = 0;
}
