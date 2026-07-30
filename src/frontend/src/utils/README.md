# utils 目录 — 工具函数

> 纯函数/指令，无页面结构；被 api / store / components / layouts 复用。

## auth.js（token 存取）
- `getToken()` — 读 `localStorage` key `if_token`
- `setToken(token)` — 写入；为空则删除
- `removeToken()` — 删除 token
- 被 `api/request.js`（请求拦截注入）、`store/user.js` 使用。

## theme.js（CSS 变量注入）
- `applyThemeVars({ themeColor, layout })` — 写入 `document.documentElement` 的 CSS 变量：
  - 主题色：`--theme-color`、`--el-color-primary` + 主色阶梯（`--el-color-primary-light-1..5`、`--el-color-primary-dark-2`，由 `mixColor` 计算）
  - 布局：`--layout-mode`
- `mixColor(base, overlay, weight)` / `hexToRgb` / `rgbToHex` — 颜色混合辅助。
- 被 `store/theme.init/setTheme/setThemeColor`、`main.js` 使用。

## permission.js（v-permission 指令）
- `hasPermission(required)` — `required` 为角色码或数组，判断 `userStore.roles` 是否命中其一（空/空数组返回 true）。
- 默认导出 `permissionDirective`（`mounted`/`updated`/`unmounted`）：按角色显隐（`display:none`），并 `watch` roles 变化刷新。
- 注册：`main.js` 中 `app.directive('permission', permissionDirective)`。
- 用法：`<el-button v-permission="['ADMIN']">删除</el-button>`。

## format.js（日期/枚举中文映射）
- `formatDate(value, fmt='YYYY-MM-DD HH:mm:ss')` — 日期格式化。
- 枚举选项：`STATUS_OPTIONS`(0~4)、`SEVERITY_OPTIONS`(0~3)、`ROLE_LABELS`、`ACTION_LABELS`。
- 标签/颜色映射：`STATUS_TAG_TYPE`/`STATUS_COLORS`、`SEVERITY_TAG_TYPE`/`SEVERITY_COLORS`。
- 转换函数：`statusLabel`/`severityLabel`/`roleLabel`/`actionLabel`、`statusTagType`/`statusColor`/`severityTagType`/`severityColor`。
- 被 `components/*`、`views/*` 广泛使用。

## exportUtil.js（导出/下载）
- `downloadFile(content, filename)` — Blob 或 URL 触发下载。
- `downloadBlob(blob, filename)` — 用 `file-saver` 保存 Blob。
- `exportChartPng(chart, filename)` — 调 ECharts `getDataURL` 导出 PNG（看板图表导出）。
- 被 `components/AttachmentUploader`、`components/charts/*`、`views/UserStats`、`views/admin/Dashboard` 使用。

## 依赖关系
```
auth.js     → localStorage（被 api/request、store/user 调用）
theme.js    → document（被 store/theme、main.js 调用）
permission.js → store/user（被 main.js 注册为指令）
format.js   → 无业务依赖（被 components/views 调用）
exportUtil.js → file-saver、echarts（被 components/views 调用）
```
