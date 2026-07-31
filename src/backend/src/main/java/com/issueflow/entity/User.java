package com.issueflow.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.issueflow.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户表（多角色模型：role_id 为主角色，roles 为全部角色码）
 *
 * <p>Phase8 W3 #11：由单角色升级为多角色。{@code role_id} 保留为「主角色」，
 * 兼容既有按单角色判定的业务逻辑；{@code roles} 为 JSON 冗余列，是
 * {@code user_role} 关系表的读缓存，用于列表/登录场景免去 N+1 查询。
 * 两者的写入均由 UserService 统一维护，保证一致。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "user", autoResultMap = true)
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

    /** 主角色 id，关联 role.id（多角色下取 roles 首位对应的角色） */
    private Long roleId;

    /**
     * 全部角色码（JSON 数组，如 {@code ["ADMIN","TESTER"]}，Phase8 W3 #11 新增）。
     * <p>为 {@code user_role} 关系表的冗余读缓存；为 null 时以关系表为准。</p>
     */
    @TableField(value = "roles", typeHandler = JacksonTypeHandler.class)
    private List<String> roles;

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
