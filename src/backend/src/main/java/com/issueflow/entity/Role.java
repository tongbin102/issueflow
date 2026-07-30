package com.issueflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色字典表（无逻辑删除字段）
 */
@Data
@TableName("role")
public class Role implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色码：SUBMITTER/DEVELOPER/TESTER/ADMIN */
    private String code;

    /** 角色名 */
    private String name;

    /** 描述 */
    private String description;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
