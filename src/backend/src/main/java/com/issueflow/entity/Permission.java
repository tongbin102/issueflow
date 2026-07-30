package com.issueflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 权限目录实体（不继承 BaseEntity，无逻辑删除；与 role / sys_config 同风格）
 */
@Data
@TableName("permission")
public class Permission implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 权限码 module:resource:action */
    private String code;

    /** 权限名称 */
    private String name;

    /** 模块 */
    private String module;

    /** 动作 */
    private String action;

    /** 端维度：1=前台端 2=后台端（用于授权页分组） */
    private Integer type;

    /** 排序号 */
    private Integer sort;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
