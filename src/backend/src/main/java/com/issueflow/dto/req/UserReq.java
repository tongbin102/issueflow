package com.issueflow.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 用户新增/编辑请求
 */
@Data
public class UserReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 登录名 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /**
     * 密码（非必填）。
     * <p>Phase8 W2 #7：新增用户时前端不再提供密码输入框——为空则由服务端取
     * {@code site.default_password}（SiteConfigService#getDefaultUserPassword）作为初始密码；
     * 编辑时为空表示保持原密码不变。</p>
     */
    private String password;

    /** 真实姓名 */
    private String realName;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String phone;

    /**
     * 主角色 id（关联 role.id）。
     * <p>Phase8 W3 #11：多角色后不再由 Bean Validation 强制——与 {@link #roles} 二选一即可，
     * 由 {@code UserService} 统一校验「两者不能同时为空」并对齐主角色，
     * 兼容仍只传 roleId 的历史调用方。</p>
     */
    private Long roleId;

    /**
     * 全部角色码（Phase8 W3 #11 新增，如 {@code ["ADMIN","TESTER"]}）。
     * <p>非法/不存在的角色码会被服务端剔除；为空时退化为按 {@link #roleId} 赋单角色。
     * 首位视为主角色，服务端据此回填 {@code roleId}。</p>
     */
    private List<String> roles;

    /** 所属组织 id（关联 organization.id，可空；Phase8 W2 #9 新增） */
    private Long orgId;

    /** 上级领导 user.id（可空，不允许指向自己） */
    private Long leaderId;

    /** 状态：1 启用 / 0 禁用（默认 1） */
    private Integer status = 1;

    /** 头像相对路径（相对文件存储根，Phase7 新增，管理员可设置） */
    private String avatar;

    /** 昵称（Phase7 新增，为空时展示 realName） */
    private String nickname;
}
