package com.issueflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.issueflow.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典项（Phase 7 新增）
 * <p>对应数据库表 {@code dict_item}（用户指令口径：冗余存储 dict_code + item_code）。
 * 注意：表存在生成列
 * {@code code_active = IF(deleted=0, CONCAT(dict_code,'_',item_code), NULL)}，
 * 用于「同类型内 item_code 条件唯一」，该列<b>不得</b>在本实体中映射。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dict_item")
public class DictItem extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属字典编码（冗余存储，避免回显/统计时 JOIN dict 表） */
    private String dictCode;

    /** 字典项编码（大写），同字典内唯一，预设项不可改 */
    private String itemCode;

    /** 选项名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 升序展示 */
    private Integer sort;

    /** 1 启用 / 0 停用 */
    private Integer enabled;

    /** 1 系统预设（删除接口硬拦截，仅可停用）/ 0 自定义 */
    private Integer isSystem;

    /** 预留扩展字段（枚举镜像类字典存对应数值 code / 颜色标记等） */
    private String extra;
}
