package com.issueflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.issueflow.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("project")
public class Project extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 项目名称（唯一） */
    private String name;

    /** 项目描述 */
    private String description;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

    /** 负责人 id（user.id） */
    private Long leaderId;

    /** 项目成员 id 列表，逗号分隔（上限约 80 人） */
    private String memberIds;
}
