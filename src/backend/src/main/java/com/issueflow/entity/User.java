package com.issueflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.issueflow.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户表（单角色模型）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user")
public class User extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 登录名 */
    private String username;

    /** BCrypt 密文 */
    private String password;

    /** 真实姓名 */
    private String realName;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String phone;

    /** 角色 id，关联 role.id */
    private Long roleId;

    /** 所属组织 id，关联 organization.id（可空：并非所有用户都归属组织，Phase8 W2 #9 新增） */
    private Long orgId;

    /** 上级领导 user.id（可空，不允许指向自己） */
    private Long leaderId;

    /** 状态：1 启用 / 0 禁用 */
    private Integer status;

    /** 头像相对路径（相对文件存储根，Phase7 新增） */
    private String avatar;

    /** 昵称，为空时展示 realName（Phase7 新增） */
    private String nickname;

    /** 上次改密时间（Phase7 新增） */
    private LocalDateTime pwdUpdatedAt;

    /** 是否已绑定微信（0 否 / 1 是，Phase7 新增，由绑定流程维护） */
    private Integer bindWechat;

    /** 是否已绑定钉钉（0 否 / 1 是，Phase7 新增，由绑定流程维护） */
    private Integer bindDingtalk;
}
