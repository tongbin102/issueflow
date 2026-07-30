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
    MODULE_DEPENDENCY_CYCLE(40036, "依赖关系存在循环，无法保存"),
    FLOW_NODE_NOT_FOUND(40040, "流程节点不存在"),
    FLOW_TRANSITION_NOT_FOUND(40041, "流转规则不存在"),
    FLOW_NODE_HAS_USAGE(40042, "该流程节点存在关联流转或问题，无法删除"),
    FLOW_NODE_HAS_TRANSITION(40047, "该流程节点仍被流转规则引用，无法删除"),
    FLOW_NODE_HAS_ISSUE(40048, "该状态下仍有存量问题，无法删除节点"),
    FLOW_NODE_STATUS_INVALID(40049, "流程节点状态码非法（仅支持 0-4）"),
    FLOW_NODE_STATUS_DUPLICATE(40050, "该状态已被其他流程节点占用"),
    FLOW_TRANSITION_DUPLICATE(40051, "相同源状态与目标状态的流转规则已存在"),
    ORG_CODE_DUPLICATE(40044, "组织编码已存在"),
    ORG_PARENT_CYCLE(40052, "上级组织不能为自身或其子孙组织"),
    USER_LEADER_CYCLE(40045, "上级领导不能为自己或形成循环"),
    SYSTEM_RESET_DENIED(40046, "无数据初始化权限");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
