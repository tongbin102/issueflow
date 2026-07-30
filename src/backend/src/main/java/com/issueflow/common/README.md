# common 包 — 统一返回体、异常与公共常量

> 被 Controller / Service / handler 各层通用依赖，是跨层契约基础。

## Result<T>（统一返回体）
- 字段：`code`(int)、`message`(String)、`data`(T)、`timestamp`(long，构造时自动赋值)
- 工厂：`success(T)` / `success()` / `error(Integer code, String msg)` / `error(ResultCode)` / `error(ResultCode, String msg)`
- 约定：成功 `code=200`，业务失败由 `GlobalExceptionHandler` 统一包装

## PageResult<T>（分页返回体）
- 字段：`list`、`total`、`page`(从1)、`size`
- 工厂：`of(list, total, page, size)`

## ResultCode（响应码枚举）
| code | 含义 |
|---|---|
| 200 SUCCESS | 成功 |
| 401 UNAUTHORIZED | 未认证 |
| 403 FORBIDDEN | 无权限 |
| 404 NOT_FOUND | 资源不存在 |
| 400 VALID_ERROR | 参数校验失败 |
| 500 SYSTEM_ERROR | 系统错误 |
| 1001 ISSUE_NOT_FOUND | 问题不存在 |
| 1002 STATUS_TRANSITION_DENIED | 状态流转不被允许 |
| 1003 FILE_TOO_LARGE | 文件过大 |
| 1004 PERMISSION_DENIED | 权限不足 |

## BizException（业务异常）
- `extends RuntimeException`，携带 `code`(Integer)
- 构造：`BizException(Integer code, String msg)` / `BizException(ResultCode)` / `BizException(ResultCode, String msg)`
- 由 `handler.GlobalExceptionHandler` 捕获并转为 `Result.error(code, message)`

## BaseEntity（实体公共基类）
- 字段：`id`(`@TableId` AUTO)、`createdAt`(`@TableField` INSERT 填充)、`updatedAt`(INSERT_UPDATE 填充)、`deleted`(`@TableLogic`)
- 被 `User/Issue/IssueAttachment/IssueHistory/Tag` 继承；`Role`/`SysConfig` 不继承

## Constants（全局常量）
- 角色码：`ROLE_SUBMITTER/DEVELOPER/TESTER/ADMIN`
- Redis：`REDIS_JWT_BLACKLIST_PREFIX = "jwt:blacklist:"`
- 附件：`ATTACHMENT_BASE_PATH="/data/attachments"`、`ATTACHMENT_STATIC_URL_PREFIX="/api/attachments/static/"`、`MAX_ATTACHMENT_SIZE=20MB`
- 分页默认：`DEFAULT_PAGE=1`、`DEFAULT_SIZE=10`
- JWT：`JWT_EXPIRATION_SECONDS=7200`
- 配置键：`CFG_FLOW_REOPEN_ENABLED`(flow_reopen_enabled)、`CFG_FLOW_REJECT_ENABLED`(flow_reject_enabled)、`CFG_THEME_COLOR`(theme_color)、`CFG_LAYOUT`(layout)、`CFG_MENU_CONFIG`(menu_config)

## 依赖关系
- `common` 不依赖任何业务包；被 `controller`/`service`/`security`/`handler`/`util` 依赖。
- `ResultCode` 被 `BizException`/`GlobalExceptionHandler` 使用；`Constants` 被 `service`/`security`/`config`/`util` 使用。
