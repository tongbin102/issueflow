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
    ROLE_CODE_DUPLICATE(1009, "角色码已存在或与内置角色冲突"),
    PROJECT_HAS_OPEN_ISSUES(40020, "该项目下存在未关闭问题，无法停用"),
    MODULE_NOT_FOUND(40030, "模块不存在"),
    MODULE_NAME_DUPLICATE(40031, "同一父级下已存在同名模块"),
    MODULE_DEPTH_EXCEEDED(40032, "模块层级不能超过 10 层"),
    MODULE_MOVE_CYCLE(40033, "不能移动到自身或其子孙模块下"),
    MODULE_HAS_ISSUES(40034, "该模块（含子模块）下存在关联问题，无法删除"),
    MODULE_PROJECT_MISMATCH(40035, "模块与问题所属项目不一致"),
    MODULE_DEPENDENCY_CYCLE(40036, "依赖关系存在循环，无法保存");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
