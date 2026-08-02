package com.issueflow.dto.fieldconfig.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 字段区域保存/编辑请求（对应 field_section 可写属性）。
 * <p>{@code code} 创建后不可改；{@code is_system=1} 区域仅可改名/排序（Service 层硬拦截删除）。</p>
 */
@Data
public class FieldSectionReq {

    /** 区域编码（大写下划线），创建后不可改 */
    @NotBlank(message = "区域编码不能为空")
    private String code;

    /** 区域名称 */
    @NotBlank(message = "区域名称不能为空")
    private String name;

    /** i18n key */
    private String i18nKey;

    /** 生效范围：本期恒 GLOBAL */
    private String typeScope;

    /** 排序 */
    private Integer sort;

    /** 1 启用 / 0 停用 */
    private Integer enabled;
}
