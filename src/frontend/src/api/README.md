# api 目录 — 业务请求封装

> 所有模块默认从 `./request` 导入单例 Axios；路径为相对 `/api` 的后缀（baseURL 已含 `/api`）。
> 响应已由 `request.js` 解包到 `data`，下方标注的"返回"为解包后的业务数据。

## request.js
- 单例 Axios 封装：请求注入 Bearer token；响应解包 `Result`；401→`/login`、403→`/403`、其他 `ElMessage.error`。
- 导出：`instance`（默认）、`API_BASE`（`import.meta.env.VITE_API_BASE || '/api'`）。

## auth.js（鉴权）
| 导出函数 | Method & Path | 返回 |
|---|---|---|
| `login(credentials)` | POST `/auth/login` | `LoginVO{token,userInfo,roles}` |
| `logout()` | POST `/auth/logout` | void |
| `getInfo()` | GET `/auth/info` | `LoginVO` |

## issue.js（问题 / 流转 / 附件）
| 导出函数 | Method & Path | 返回 |
|---|---|---|
| `pageIssues(params)` | GET `/issues` | `PageResult<IssueVO>` |
| `getIssue(id)` | GET `/issues/{id}` | `IssueDetailVO` |
| `createIssue(formData)` | POST `/issues`（multipart） | `IssueVO` |
| `updateIssue(id, data)` | PUT `/issues/{id}` | `IssueVO` |
| `deleteIssue(id)` | DELETE `/issues/{id}` | void |
| `getHistory(id, params)` | GET `/issues/{id}/history` | `PageResult<IssueHistoryVO>` |
| `changeStatus(id, payload)` | POST `/issues/{id}/status` | `IssueVO` |
| `reopenIssue(id, remark)` | POST `/issues/{id}/reopen` | `IssueVO` |
| `uploadAttachments(id, files)` | POST `/issues/{id}/attachments`（multipart, 字段 `files`） | `List<AttachmentVO>` |
| `downloadAttachment(id)` | GET `/attachments/{id}/download`（blob） | Blob |
| `previewAttachment(id)` | GET `/attachments/{id}/preview`（blob） | object URL（已 `createObjectURL`） |
| `deleteAttachment(id)` | DELETE `/attachments/{id}` | void |

> `IssuePageReq` 字段：page/size/status/severity/tag/version/assigneeId/reporterId/keyword/startDate/endDate。

## dashboard.js（看板）
| 导出函数 | Method & Path | 返回 |
|---|---|---|
| `overview(params)` | GET `/dashboard/overview` | `DashboardVO{statusDistribution,trendByDay,severityRatio,avgResolveCycle,resolveRate,total,closedTotal}` |
| `exportExcel(params)` | GET `/dashboard/export`（blob） | Blob（xlsx） |

> `DashboardQueryReq` 字段：start/end/version（yyyy-MM-dd）。

## user.js（用户 / 角色）
| 导出函数 | Method & Path | 返回 |
|---|---|---|
| `pageUsers(params)` | GET `/users` | `PageResult<UserVO>` |
| `createUser(data)` | POST `/users` | `UserVO` |
| `updateUser(id, data)` | PUT `/users/{id}` | `UserVO` |
| `deleteUser(id)` | DELETE `/users/{id}` | void |
| `listRoles()` | GET `/roles` | `List<Role>` |

## tag.js（标签）
| 导出函数 | Method & Path | 返回 |
|---|---|---|
| `listTags()` | GET `/tags` | `List<Tag>` |
| `createTag(data)` | POST `/tags` | `Tag` |
| `updateTag(id, data)` | PUT `/tags` | `Tag` |
| `deleteTag(id)` | DELETE `/tags/{id}` | void |

## sysConfig.js（系统配置 / 流程开关）
| 导出函数 | Method & Path | 返回 |
|---|---|---|
| `getConfig()` | GET `/sys/config` | `Map{themeColor,layout,menuConfig,flow}` |
| `setConfig(payload)` | PUT `/sys/config` | void |
| `getFlowConfig()` | GET `/flow/config` | `{rejectEnabled,reopenEnabled}` |
| `setFlowConfig(payload)` | PUT `/flow/config` | void |

## 依赖关系
- 全部依赖 `request.js`（单例 Axios）。
- `request.js` 依赖 `utils/auth`（token 存取）。
- 被 `store/user.js`、`components/*`、`views/*` 调用。
