package com.issueflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.issueflow.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典类型（Phase 7 新增）
 * <p>对应数据库表 {@code dict}（用户指令口径：表名 dict，编码列 dict_code）。
 * 注意：表存在生成列 {@code code_active = IF(deleted=0, dict_code, NULL)} 用于「未删除行 dict_code 唯一」，
 * 该列<b>不得</b>在本实体中映射，否则 MyBatis-Plus 会尝试写入生成列导致 SQL 报错。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dict")
public class Dict extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 字典编码（大写），程序依赖，创建后不可修改 */
    private String dictCode;

    /** 字典名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 升序展示 */
    private Integer sort;

    /** 1 启用 / 0 停用 */
    private Integer enabled;

    /** 1 系统预设（不可删除、编码不可改）/ 0 自定义 */
    private Integer isSystem;
}
