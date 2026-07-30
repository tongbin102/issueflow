package com.issueflow.dto.resp;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 登录视图对象
 */
@Data
public class LoginVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** JWT token */
    private String token;

    /** 用户信息 */
    private UserVO userInfo;

    /** 角色码列表 */
    private List<String> roles;
}
