package com.issueflow.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 组织新建/编辑请求（Phase 5 扩展 code/leaderId/status/description）
 */
@Data
public class OrganizationReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 组织名称 */
    @NotBlank(message = "组织名称不能为空")
    private String name;

    /** 组织编码（必填唯一） */
    @NotBlank(message = "组织编码不能为空")
    private String code;

    /** 负责人 user.id（可空） */
    private Long leaderId;

    /** 状态：1 启用 / 0 禁用（默认 1） */
    private Integer status = 1;

    /** 组织描述（可空） */
    private String description;

    /** 父级 id，0 表示根，默认 0 */
    private Long parentId = 0L;

    /** 排序号，升序展示，默认 0 */
    private Integer sort = 0;
}
