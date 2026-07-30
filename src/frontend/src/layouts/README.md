# layouts 目录 — 布局外壳

> 三个布局组件承载 `<router-view/>`；决定侧栏菜单、顶栏、主题切换与响应式。

## UserLayout.vue（用户界面）
- 职责：左侧栏（工作台/我的问题/提交问题/个人看板 菜单）+ 顶栏（标题、主题色 `el-color-picker`、`userStore.realName` 下拉"退出登录"）+ 内容区 `<router-view>`。
- 菜单：`el-menu` `router` 模式，`index` 指向 `/user`、`/user/issues`、`/user/create`、`/user/stats`。
- 响应式：桌面常驻侧栏（可折叠）；`≤768px` 侧栏变为**抽屉式**（点击汉堡按钮开合 + 遮罩关闭）。

## AdminLayout.vue（管理后台）
- 职责：与 UserLayout 结构一致，菜单项为管理功能：概览/问题管理/流程监控/用户管理/流程配置/系统设置。
- 菜单 `index` 指向 `/admin/index`、`/admin/issues`、`/admin/flow-monitor`、`/admin/users`、`/admin/flow-config`、`/admin/settings`。
- 响应式：同 UserLayout（桌面侧栏 / 移动端抽屉）。

## BlankLayout.vue（空白外壳）
- 职责：居中卡片容器，承载 `Login` / `NotFound` / `Forbidden` 等独立页；浅色渐变背景。
- 不含侧栏/顶栏，仅一个 `<router-view>`。

## 响应式（移动端抽屉侧栏）
- 两布局均通过 `store/app.setDevice(window.innerWidth <= 768 ? 'mobile' : 'desktop')`（resize 监听）。
- `mobile` 时：侧栏 `.is-mobile-open` 抽屉态 + `.if-mobile-mask` 遮罩；`desktop` 时：`.is-collapsed` 折叠（由 `appStore.sidebarCollapsed` 控制）。
- 顶栏汉堡按钮：`mobile` 切换抽屉，`desktop` 切换 `sidebarCollapsed`。

## 依赖关系
- 均依赖 `store/user`（用户名/角色/登出）、`store/app`（设备/折叠）、`store/theme`（主题色）。
- `UserLayout`/`AdminLayout` → `utils/theme`（`setThemeColor` 即时生效）、`router`、`element-plus`。
- 被 `router/routes.js` 作为各路由的 `component` 懒加载。
