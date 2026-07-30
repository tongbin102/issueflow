# views 目录 — 页面

> 路由定义见 `router/routes.js`；页面级权限由 `meta.roles` + 路由守卫控制。

## 公共
| 页面 | 路由 | 角色 | 职责 |
|---|---|---|---|
| `Login.vue` | `/login` | 公开 | 统一登录页（账号/密码，"记住我"），登录后按角色跳首页；默认管理员 admin/admin123 提示 |
| `error/Forbidden.vue` | `/403` | 公开 | 403 无权限页 |
| `error/NotFound.vue` | `/:pathMatch` | 公开 | 404 页 |

## user/（用户界面，UserLayout 承载，`roles: USER_ROLES` 全部四类）
| 页面 | 路由 | 职责 |
|---|---|---|
| `UserDashboard.vue` | `/user`（name: user-dashboard） | 工作台首页：按状态统计卡（我提交）+ 我的趋势图（`TrendChart` + `overview`） |
| `UserIssueList.vue` | `/user/issues` | 我的问题：`IssueTable scope="mine"` + 详情抽屉 + 编辑对话框 |
| `IssueCreate.vue` | `/user/create` | 提交问题：`IssueForm` 提交（multipart 带附件），成功后打开详情抽屉展示编号 |
| `UserStats.vue` | `/user/stats` | 个人看板：KPI（平均周期/解决率/总数）+ `TrendChart` + `DistributionChart` + 导出 PNG/Excel（个人数据范围） |

## admin/（管理后台，AdminLayout 承载，仅 `ADMIN`）
| 页面 | 路由 | 职责 |
|---|---|---|
| `Dashboard.vue` | `/admin/index` | 全局看板：KPI + 趋势/分布图 + 导出 PNG/Excel（全量数据范围） |
| `AdminIssueList.vue` | `/admin/issues` | 问题管理：`IssueTable scope="all"` + 详情抽屉 + 编辑对话框（"提交问题"同跳 `/user/create`） |
| `FlowMonitor.vue` | `/admin/flow-monitor` | 验证流程监控：状态统计卡 + 最近流转表格（`pageIssues` 最新 15 条）+ 详情抽屉 |
| `UserManage.vue` | `/admin/users` | 用户与角色管理：用户分页表格 + 新建/编辑对话框（角色下拉来自 `listRoles`），RBAC 配置 |
| `FlowConfig.vue` | `/admin/flow-config` | 流程配置：回退开关（`flow_reject_enabled`）/ 重开开关（`flow_reopen_enabled`）两个 switch，即时保存 |
| `SystemSettings.vue` | `/admin/settings` | 系统设置：承载 `ThemeConfigPanel`（主题色/布局/菜单配置） |

## 依赖关系
```
Login        → store/user、router
user/*       → components(IssueTable/IssueForm/IssueDetailDrawer/TrendChart/DistributionChart/DashboardFilters)、api/*、store/user
admin/*      → 同上 + api/sysConfig、api/user
IssueCreate  → components/IssueForm、IssueDetailDrawer、api/issue
UserStats/Dashboard → api/dashboard、utils/exportUtil
```
