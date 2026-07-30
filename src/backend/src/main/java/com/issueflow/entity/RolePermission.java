package com.issueflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色-权限映射实体（不继承 BaseEntity，无逻辑删除）
 */
@Data
@TableName("role_permission")
public class RolePermission implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roleId;

    private Long permissionId;

    private LocalDateTime createdAt;
}
