package com.issueflow.dto.resp;

import lombok.Data;

import java.io.Serializable;

/**
 * 字典下拉选项（全站下拉唯一数据源，轻量结构）
 * <p>「(已停用)」后缀由前端按 enabled 拼接（跟随 i18n 语言），后端不拼中文。</p>
 */
@Data
public class DictOptionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 选项名称 */
    private String name;

    /** 选项编码（前端 i18n 映射 key，同字典内唯一） */
    private String code;

    /** 是否启用 */
    private Boolean enabled;

    /** 扩展字段（枚举镜像存数值 code，如优先级数值） */
    private String extra;
}
