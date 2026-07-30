package com.issueflow.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 模块新建 / 编辑请求。
 *
 * <p>create 使用全部字段；update 仅取 {@code name} / {@code description}
 * （projectId / parentId 由路径上的模块自身决定，移动请走 move 接口）。</p>
 */
@Data
public class ModuleReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 所属项目 id（create 必填；update 忽略） */
    private Long projectId;

    /** 父模块 id，空或 0 表示根级（create 使用；update 忽略） */
    private Long parentId;

    /** 模块名称（同父级下唯一） */
    @NotBlank(message = "模块名称不能为空")
    @Size(max = 50, message = "模块名称长度不能超过 50 字")
    private String name;

    /** 模块描述 */
    @Size(max = 200, message = "模块描述长度不能超过 200 字")
    private String description;
}
