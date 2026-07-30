# router 目录 — 路由与守卫

> 路由表 `routes.js`（静态导入，按布局分组）+ 守卫 `index.js`。
> 历史模式：`createWebHistory()`。

## 一、路由表结构（routes.js）

路由按**布局外壳**分组，子路由懒加载（`() => import(...)`）：

| 父路由（layout） | path | 子路由（name → component） | meta.roles |
|---|---|---|---|
| `BlankLayout` | `/login` | `login` → `Login.vue` | 公开 `public:true` |
| `BlankLayout` | `/403` | `forbidden` → `Forbidden.vue` | 公开 |
| `UserLayout` | `/`(redirect `/user`) | `user-dashboard`→`UserDashboard`、 `user-issues`→`UserIssueList`、 `issue-create`→`IssueCreate`、 `user-stats`→`UserStats` | `USER_ROLES`（全部四类） |
| `AdminLayout` | `/admin`(redirect `/admin/index`) | `admin-dashboard`→`Dashboard`、 `admin-issues`→`AdminIssueList`、 `flow-monitor`→`FlowMonitor`、 `user-manage`→`UserManage`、 `flow-config`→`FlowConfig`、 `system-settings`→`SystemSettings` | `['ADMIN']` |
| `BlankLayout` | `/:pathMatch(.*)*` | `not-found` → `NotFound.vue` | 公开 |

- `USER_ROLES = ['SUBMITTER','DEVELOPER','TESTER','ADMIN']`（定义在 routes.js 顶部）。
- 父级 `meta` 仅承载 `title`；页面级权限写在子路由 `meta.roles`。

## 二、路由守卫逻辑（index.js `beforeEach`）

1. **公开路由**（`to.meta.public`）：直接放行；若已登录访问 `/login`，跳 `defaultHomePath()`。
2. **未登录访问受保护路由**：`next({ path:'/login', query:{ redirect: to.fullPath } })`。
3. **角色未加载**（本地态失效，`roles` 为空）：先 `userStore.getInfo()` 兜底；失败则登出跳 `/login`。
4. **角色校验**：若 `to.meta.roles` 为非空数组，要求 `userStore.roles` 至少有一项命中，否则 `ElMessage.error` + `next('/403')`。
5. 通过则 `next()`。

## 三、meta.roles 用法

- 在 `routes.js` 子路由上声明：`meta: { roles: ['ADMIN'] }` 或 `meta: { roles: USER_ROLES }`。
- 守卫据此与 `userStore.roles` 求交集；空数组/未声明则不限制（公开路由用 `public:true` 标识）。
- 与按钮级 `v-permission` 指令互补：页面进得去（meta.roles）≠ 按钮可见（v-permission）。

## 依赖关系
- `index.js` → `routes.js`（路由表）、`store/user`（`isLoggedIn`/`roles`/`getInfo`/`defaultHomePath`）、`element-plus(ElMessage)`。
- 被 `main.js` 引入并 `app.use(router)`。
