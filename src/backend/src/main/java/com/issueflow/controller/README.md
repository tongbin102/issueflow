# controller 包 — REST 端点总览

> 统一前缀 `/api`；除登录外均需 JWT（`Authorization: Bearer <token>`）。
> 角色列：S=提交者 D=开发 T=测试 A=管理员；`*`=任意登录用户；公开=无需 token。
> 写操作的管理员校验：User/Tag/SysConfig 控制器内置 `requireAdmin()`，IssueFlow 控制器同样校验 `ROLE_ADMIN`。

## 鉴权 `AuthController` `@RequestMapping("/api/auth")`

| Method | Path | 角色 | 说明 |
|---|---|---|---|
| POST | `/api/auth/login` | 公开 | 登录，返回 `LoginVO`（token + userInfo + roles） |
| POST | `/api/auth/logout` | * | 登出，将 jti 写入 Redis 黑名单 |
| GET | `/api/auth/info` | * | 当前登录用户信息 |

## 问题 `IssueController` `@RequestMapping("/api/issues")`

| Method | Path | 角色 | 说明 |
|---|---|---|---|
| POST | `/api/issues`（multipart） | * | 新建问题（可附附件），自动编号、status=OPEN |
| PUT | `/api/issues/{id}` | 创建者 / A | 编辑（仅非空字段） |
| DELETE | `/api/issues/{id}` | 创建者 / A | 逻辑删除（级联删附件与历史） |
| GET | `/api/issues/{id}` | 创建者（己）/ D / T / A | 详情（含附件 + 最近历史） |
| GET | `/api/issues` | S 仅己 / 其他全量 | 分页 + 多条件筛选 |
| GET | `/api/issues/{id}/history` | 创建者（己）/ D / T / A | 操作历史（分页 + 操作人/时间范围） |

## 流转 `IssueFlowController` `@RequestMapping("/api")`

| Method | Path | 角色 | 说明 |
|---|---|---|---|
| POST | `/api/issues/{id}/status` | 按状态机规则 | 状态流转 `{toStatus, remark}`，写历史 |
| POST | `/api/issues/{id}/reopen` | A（需 `flow_reopen_enabled`） | 重开（已关闭→待处理） |
| GET | `/api/flow/config` | A | 读取流程开关 `{rejectEnabled, reopenEnabled}` |
| PUT | `/api/flow/config` | A | 写入流程开关（`FlowConfigReq`） |

## 看板 `DashboardController` `@RequestMapping("/api/dashboard")`

| Method | Path | 角色 | 说明 |
|---|---|---|---|
| GET | `/api/dashboard/overview` | S 仅己 / 其他全量 | 看板聚合 `DashboardVO`（趋势/分布/周期/解决率/严重占比） |
| GET | `/api/dashboard/export` | 同上 | 导出 Excel（文件流） |

## 附件 `AttachmentController` `@RequestMapping("/api")`

| Method | Path | 角色 | 说明 |
|---|---|---|---|
| POST | `/api/issues/{id}/attachments` | 创建者 / A | 上传附件（字段名 `files`，≤20MB） |
| GET | `/api/attachments/{id}/download` | * | 下载（非图片为附件下载） |
| GET | `/api/attachments/{id}/preview` | * | 图片内联预览 |
| DELETE | `/api/attachments/{id}` | 创建者 / A | 逻辑删 + 删文件 |

## 用户 / 角色 `UserController` `@RequestMapping("/api")`

| Method | Path | 角色 | 说明 |
|---|---|---|---|
| GET | `/api/users` | A | 用户分页列表 |
| POST | `/api/users` | A | 新增用户（`UserReq`，密码 BCrypt） |
| PUT | `/api/users/{id}` | A | 编辑用户 |
| DELETE | `/api/users/{id}` | A | 逻辑删除 |
| GET | `/api/roles` | * | 角色字典列表 |

## 标签 `TagController` `@RequestMapping("/api/tags")`

| Method | Path | 角色 | 说明 |
|---|---|---|---|
| GET | `/api/tags` | * | 标签列表 |
| POST | `/api/tags` | A（写） | 新增标签 |
| PUT | `/api/tags` | A（写） | 更新标签 |
| DELETE | `/api/tags/{id}` | A（写） | 逻辑删除 |

## 系统配置 `SysConfigController` `@RequestMapping("/api/sys")`

| Method | Path | 角色 | 说明 |
|---|---|---|---|
| GET | `/api/sys/config` | * | 公开配置（主题色/布局/菜单/流程开关） |
| PUT | `/api/sys/config` | A | 按 `configKey` 写配置（`SysConfigReq`） |
