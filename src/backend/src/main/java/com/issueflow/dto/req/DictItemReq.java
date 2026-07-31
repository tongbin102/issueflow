package com.issueflow.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 字典项新增/编辑请求
 * <p>以 {@code typeCode}（字典类型编码，落库列 dict_code）标识归属类型，不再使用 type_id。
 * 预设项（is_system=1）编辑时服务端<b>静默忽略</b> {@code code}，不报错。</p>
 */
@Data
public class DictItemReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 所属字典类型编码（ISSUE_SOURCE / ISSUE_PRIORITY / ...），必须已存在且未删除 */
    @NotBlank(message = "所属字典类型编码不能为空")
    @Size(max = 50, message = "字典类型编码不能超过 50 字")
    private String typeCode;

    /** 选项名称 */
    @NotBlank(message = "选项名称不能为空")
    @Size(max = 50, message = "选项名称不能超过 50 字")
    private String name;

    /** 选项编码（大写字母开头，仅大写字母/数字/下划线；同字典内唯一） */
    @NotBlank(message = "选项编码不能为空")
    @Size(max = 50, message = "选项编码不能超过 50 字")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "选项编码须以大写字母开头，仅含大写字母、数字、下划线")
    private String code;

    /** 描述 */
    @Size(max = 200, message = "描述不能超过 200 字")
    private String description;

    /** 排序号（升序） */
    private Integer sort = 0;

    /** 是否启用（默认启用） */
    private Boolean enabled = Boolean.TRUE;

    /** 扩展字段（枚举镜像类字典存对应数值 code） */
    @Size(max = 200, message = "扩展字段不能超过 200 字")
    private String extra;
}
