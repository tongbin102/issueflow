package com.issueflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.issueflow.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 问题自定义字段值（竖表，仅存 {@code is_system=0} 的自定义字段值，Q2/Q3）。
 * <p>对应表 {@code issue_field_value}。内置字段值仍在 issue 主表原列，不进本表。
 * 表存在生成列 {@code pair_active = IF(deleted=0, CONCAT(issue_id,'_',field_code), NULL)}
 * 用于「(issue_id, field_code) 条件唯一」，该列<b>不映射</b>到本实体。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("issue_field_value")
public class IssueFieldValue extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属问题 issue.id（无外键） */
    private Long issueId;

    /** 字段编码 field_config.code（冗余存 code 而非 id，避免回显 JOIN） */
    private String fieldCode;

    /** TEXT/DICT/REF 值；多选为逗号拼接 */
    private String valueText;

    /** NUMBER 值 */
    private BigDecimal valueNum;

    /** DATE/DATETIME 值（DATE 取 00:00:00） */
    private LocalDateTime valueDate;
}
