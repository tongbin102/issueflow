package com.issueflow.common;

import lombok.Getter;

/**
 * 统一响应码枚举
 */
@Getter
public enum ResultCode {

    SUCCESS(200, "success"),
    UNAUTHORIZED(401, "未认证，请先登录"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    VALID_ERROR(400, "参数校验失败"),
    SYSTEM_ERROR(500, "系统错误"),
    ISSUE_NOT_FOUND(1001, "问题不存在"),
    STATUS_TRANSITION_DENIED(1002, "状态流转不被允许"),
    FILE_TOO_LARGE(1003, "文件过大"),
    PERMISSION_DENIED(1004, "权限不足"),
    PROJECT_NAME_DUPLICATE(1005, "项目名称已存在"),
    NODE_HAS_CHILDREN(1006, "该节点下存在子节点，无法删除"),
    RELATION_CYCLE(1007, "问题关联存在环路，无法保存"),
    ROLE_BUILTIN_PROTECTED(1008, "内置角色受保护，禁止删除或修改角色码"),
    ROLE_CODE_DUPLICATE(1009, "角色码已存在或与内置角色冲突");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
