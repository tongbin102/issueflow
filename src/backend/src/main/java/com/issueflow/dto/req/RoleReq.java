package com.issueflow.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 角色新建/编辑请求
 */
@Data
public class RoleReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 角色码（新建必填，编辑不可改） */
    @NotBlank(message = "角色码不能为空")
    private String code;

    /** 角色名称 */
    @NotBlank(message = "角色名称不能为空")
    private String name;

    /** 描述 */
    private String description;
}
