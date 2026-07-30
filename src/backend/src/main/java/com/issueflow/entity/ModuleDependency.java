package com.issueflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.issueflow.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 模块依赖表（单向：A 依赖 B，仅展示语义）
 *
 * <p>唯一索引 uk(from_module_id,to_module_id) 与软删复活相斥，
 * 故本表由 Service 层「物理清空 + 重建」维护（全局唯一例外），
 * {@code deleted} 列仅用于对齐 BaseEntity 范式。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("module_dependency")
public class ModuleDependency extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 依赖方模块 id（A） */
    private Long fromModuleId;

    /** 被依赖模块 id（B） */
    private Long toModuleId;
}
