package com.issueflow.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 问题类型新增/编辑请求
 */
@Data
public class IssueTypeReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 类型名称 */
    @NotBlank(message = "类型名称不能为空")
    @Size(max = 50, message = "类型名称不能超过 50 字")
    private String name;

    /** 类型编码（大写字母开头，仅大写字母/数字/下划线） */
    @NotBlank(message = "类型编码不能为空")
    @Size(max = 50, message = "类型编码不能超过 50 字")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "类型编码须以大写字母开头，仅含大写字母、数字、下划线")
    private String code;

    /** 描述 */
    @Size(max = 200, message = "描述不能超过 200 字")
    private String description;

    /** 排序号（升序） */
    private Integer sort = 0;

    /** 是否启用（默认启用） */
    private Boolean enabled = Boolean.TRUE;
}
