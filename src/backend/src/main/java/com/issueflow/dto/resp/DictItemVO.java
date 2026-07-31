package com.issueflow.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 字典项列表视图对象
 */
@Data
public class DictItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 所属字典类型编码（落库列 dict_code） */
    private String typeCode;

    /** 选项编码（前端 i18n 映射 key，同字典内唯一） */
    private String code;

    /** 选项名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 排序号 */
    private Integer sort;

    /** 是否启用 */
    private Boolean enabled;

    /** 是否系统预设（预设项不可删除，可停用） */
    private Boolean isSystem;

    /** 扩展字段（枚举镜像存数值 code） */
    private String extra;

    /** 被业务引用条数（仅 ISSUE_SOURCE 有意义，其余为 0） */
    private Long refCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
