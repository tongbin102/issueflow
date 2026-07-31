package com.issueflow.dto.resp;

import lombok.Data;

import java.io.Serializable;

/**
 * 问题类型下拉选项（轻量结构）
 * <p>「(已停用)」后缀由前端按 enabled 拼接（跟随 i18n 语言），后端不拼中文。</p>
 */
@Data
public class IssueTypeOptionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 类型名称 */
    private String name;

    /** 类型编码（前端 i18n 映射 key） */
    private String code;

    /** 是否启用 */
    private Boolean enabled;
}
