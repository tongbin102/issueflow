package com.issueflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户-角色映射实体（Phase8 W3 #11 新增，不继承 BaseEntity，无逻辑删除）。
 *
 * <p>与 {@code role_permission} 同款设计：关联随主体重建（整体替换），
 * 因此物理删除即可，无需 deleted 标记。存的是角色<b>码</b>而非角色 id，
 * 便于 JWT / SecurityContext 直接消费，避免鉴权链路多一次 id→code 反查。</p>
 */
@Data
@TableName("user_role")
public class UserRole implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 id，关联 user.id */
    private Long userId;

    /** 角色码，关联 role.code（如 ADMIN / DEVELOPER / TESTER / SUBMITTER） */
    private String roleCode;
}
