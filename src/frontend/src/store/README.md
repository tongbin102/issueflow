# store 目录 — Pinia 状态

> 三个 store：`user` / `theme` / `app`；均持久化到 localStorage，刷新可恢复。

## user.js（`useUserStore`，id: `user`）
- **state**：`token`、`userInfo`、`roles`
- **getters**：
  - `isLoggedIn` — 是否有 token
  - `role` — `roles[0]`（主角色，用于快捷判断/默认首页）
  - `isAdmin` / `isDeveloper` / `isTester` / `isSubmitter` — 角色判断
  - `realName` — `userInfo.realName || userInfo.username`
- **actions**：
  - `login(username, password)` — 调 `api/auth.login`，存 token + userInfo + roles，持久化
  - `getInfo()` — 拉当前用户信息（角色补全兜底）
  - `logout()` — 调后端登出（忽略失败）→ 清 token + userInfo + roles + localStorage
  - `defaultHomePath()` — ADMIN 返回 `/admin`，否则 `/user`
  - `persist()` — 写 `localStorage` key `if_user`

## theme.js（`useThemeStore`，id: `theme`）
- **state**：`themeColor`(默认 `#409EFF`)、`layout`(默认 `side`)、`menuConfig`(默认 `{}`)
- **getters**：`currentThemeColor`
- **actions**：
  - `init()` — 调用 `utils/theme.applyThemeVars` 注入 CSS 变量
  - `setTheme(partial)` — 整体设置 themeColor/layout/menuConfig，注入变量并持久化
  - `setThemeColor(color)` — 仅切主题色
  - `persist()` — 写 `localStorage` key `if_theme`

## app.js（`useAppStore`，id: `app`）
- **state**：`sidebarCollapsed`(bool)、`device`('desktop'|'mobile')
- **getters**：`isMobile` — `device === 'mobile'`
- **actions**：
  - `toggleSidebar()` / `setSidebarCollapsed(value)`
  - `setDevice(device)` — 由 layouts 的 resize 监听设置（≤768px 判为 mobile）
  - `persist()` — 写 `localStorage` key `if_app`

## 依赖关系
- `user` → `api/auth`、`utils/auth`（token 存取）
- `theme` → `utils/theme`（CSS 变量注入）
- `app` → 无外部依赖（由 `layouts` 写入 device）
- 被 `router/index.js`（守卫）、`layouts/*`、`views/*`、`utils/permission` 使用
