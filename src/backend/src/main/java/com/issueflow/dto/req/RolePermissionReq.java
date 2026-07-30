package com.issueflow.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 角色权限分配请求
 */
@Data
public class RolePermissionReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 权限码列表（整体替换） */
    @NotNull(message = "权限码列表不能为空")
    private List<String> permissionCodes = new ArrayList<>();
}
