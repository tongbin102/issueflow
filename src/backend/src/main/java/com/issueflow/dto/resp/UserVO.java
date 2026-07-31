package com.issueflow.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户视图对象（隐去密码）
 */
@Data
public class UserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String username;

    private String realName;

    private String email;

    private String phone;

    /** 角色 id */
    private Long roleId;

    /** 角色码 */
    private String roleCode;

    /** 角色名 */
    private String roleName;

    /** 上级领导 user.id */
    private Long leaderId;

    /** 上级领导显示名（realName 优先，缺省 username） */
    private String leaderName;

    /** 状态：1 启用 / 0 禁用 */
    private Integer status;

    /** 头像相对路径（Phase7 新增） */
    private String avatar;

    /** 昵称（Phase7 新增，为空时展示 realName） */
    private String nickname;

    /** 是否已绑定微信（0 否 / 1 是，Phase7 新增） */
    private Integer bindWechat;

    /** 是否已绑定钉钉（0 否 / 1 是，Phase7 新增） */
    private Integer bindDingtalk;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
