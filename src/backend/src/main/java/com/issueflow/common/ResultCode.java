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
    SYSTEM_RESET_DENIED(40046, "无数据初始化权限"),
    ISSUE_TYPE_NOT_FOUND(40060, "问题类型不存在"),
    ISSUE_TYPE_HAS_USAGE(40062, "该类型下存在关联问题，无法删除，可改为停用"),
    ISSUE_TYPE_DISABLED(40063, "该问题类型已停用，不可选择"),
    DICT_TYPE_NOT_FOUND(40070, "字典类型不存在"),
    DICT_TYPE_CODE_DUPLICATE(40071, "字典类型编码已存在"),
    DICT_TYPE_SYSTEM_PROTECTED(40072, "系统预设字典类型不可删除"),
    DICT_TYPE_HAS_ITEMS(40073, "该类型下仍存在选项，请先删除选项"),
    DICT_TYPE_MIRROR_READONLY(40074, "该类型为系统枚举镜像，不可新增选项"),
    DICT_ITEM_NOT_FOUND(40075, "字典选项不存在"),
    DICT_ITEM_CODE_DUPLICATE(40076, "该类型下选项编码已存在"),
    DICT_ITEM_SYSTEM_PROTECTED(40077, "系统预设项不可删除，可改为停用"),
    DICT_ITEM_HAS_USAGE(40078, "该选项下存在关联问题，无法删除，可改为停用"),
    DICT_ITEM_DISABLED(40079, "该选项已停用，不可选择"),

    // ===== Phase9 动态字段配置（注：原 §7.6 拟用的 40070-40079 已被 DICT_* 占用，整体顺延至 40090-40100，见 ARCH §8.8）=====
    FIELD_TYPE_IMMUTABLE(40090, "字段类型创建后不可修改"),
    FIELD_CODE_DUPLICATE(40091, "字段编码已存在"),
    FIELD_SYSTEM_PROTECTED(40092, "内置字段不可删除或修改编码/类型"),
    FIELD_DEPENDS_SELF(40093, "字段不可依赖自身"),
    FIELD_DEPENDS_CYCLE(40094, "存在循环依赖：{path}"),
    FIELD_DEPENDS_MULTI_NOT_ALLOWED(40095, "多选字段不可作为依赖源"),
    FIELD_DEPENDS_LEVEL_EXCEEDED(40096, "本期仅支持单级依赖"),
    FIELD_DEPENDS_SOURCE_INVALID(40097, "依赖源字段不存在或已停用"),
    FIELD_DEPENDS_SCOPE_MISMATCH(40098, "依赖源与当前字段生效范围不一致"),
    REF_SOURCE_NOT_ALLOWED(40099, "引用源不在白名单中"),
    REF_SOURCE_ILLEGAL_IDENTIFIER(40100, "引用源配置非法"),
    /** 自定义字段必填校验失败（落库前兜底，severity 与 Phase9 段一致） */
    FIELD_VALUE_REQUIRED(40101, "必填字段未填写"),

    // ===== Phase10 数据管理（备份 / 恢复），号段 40110-40125 =====
    /** 已有备份/恢复任务在执行，全局互斥 */
    DATA_TASK_RUNNING(40110, "已有备份或恢复任务正在执行，请稍后再试"),
    /** 任务不存在或进度已过期（Redis TTL 2h） */
    DATA_TASK_NOT_FOUND(40111, "任务不存在或进度已过期"),
    /** 备份记录不存在 */
    BACKUP_NOT_FOUND(40112, "备份记录不存在"),
    /** 备份文件已丢失（记录在但磁盘文件没了） */
    BACKUP_FILE_MISSING(40113, "备份文件已丢失，无法下载或恢复"),
    /** 备份仍在进行中，不可下载/删除/恢复 */
    BACKUP_NOT_READY(40114, "备份尚未完成，请等待任务结束"),
    /** 备份包结构非法（缺 manifest.json / db 目录等） */
    BACKUP_PACKAGE_INVALID(40115, "备份包格式不正确，请上传由本系统导出的备份文件"),
    /** SHA-256 校验和不匹配 */
    BACKUP_CHECKSUM_MISMATCH(40116, "备份文件校验失败，文件可能已损坏"),
    /** 上传文件超出体积上限 */
    BACKUP_UPLOAD_TOO_LARGE(40117, "上传文件超出大小限制"),
    /** mysqldump / mysql 客户端不可用 */
    DATA_TOOL_UNAVAILABLE(40118, "数据库备份工具不可用，请联系运维检查部署环境"),
    /** 备份执行失败（详细原因脱敏后写入 error_msg） */
    BACKUP_EXECUTE_FAILED(40119, "备份执行失败"),
    /** 恢复执行失败 */
    RESTORE_EXECUTE_FAILED(40120, "数据恢复失败，已尝试保留恢复前安全备份"),
    /** 系统处于恢复只读期，拒绝写操作 */
    DATA_SYSTEM_READONLY(40121, "系统正在执行数据恢复，暂时只读，请稍后重试"),
    /** 数据管理模块被关闭 */
    DATA_MANAGEMENT_DISABLED(40122, "数据管理功能未启用"),
    /** 备份包版本与当前应用不兼容 */
    BACKUP_VERSION_INCOMPATIBLE(40123, "备份包版本与当前系统不兼容");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
