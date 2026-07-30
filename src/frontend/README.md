# issueFlow 前端（Vue3 SPA）

> 技术栈：Vue3（`@vue/composition-api` <script setup>）+ Element Plus + Pinia + Vue Router 4 + Axios + ECharts + Vite

## 一、技术栈与依赖

| 维度 | 选型 |
|---|---|
| 框架 | Vue 3.4 + Vite 5 |
| UI | Element Plus 2.7 + `@element-plus/icons-vue` |
| 状态 | Pinia 2（`user` / `theme` / `app`） |
| 路由 | Vue Router 4（`createWebHistory`） |
| 请求 | Axios（`src/api/request.js` 单例封装） |
| 图表 | ECharts 5 |
| 导出 | `file-saver`（Excel/PNG 下载） |

## 二、目录总览（`src/`）

| 目录 | 职责 |
|---|---|
| `api/` | 业务请求封装（auth/issue/dashboard/user/tag/sysConfig/request） |
| `components/` | 可复用组件（表单/表格/抽屉/时间线/流转按钮/附件/图表/筛选/主题面板） |
| `views/` | 页面（`Login`、`user/` 4 个、`admin/` 6 个、`error/` 2 个） |
| `store/` | Pinia stores（`user` / `theme` / `app`） |
| `router/` | `routes.js`（路由表）+ `index.js`（守卫） |
| `layouts/` | `UserLayout` / `AdminLayout` / `BlankLayout` |
| `utils/` | `auth` / `theme` / `permission` / `format` / `exportUtil` |
| `styles/` | `variables.css` / `theme.css` / `index.css`（CSS 变量与主题） |
| `main.js` / `App.vue` | 入口挂载、全局指令注册、主题初始化 |

## 三、本地运行

```bash
cd src/frontend
npm install            # 安装依赖
npm run dev            # 开发服务器（默认代理 /api → http://localhost:8080）
npm run build          # 产物输出到 dist/
npm run preview        # 预览构建产物
```

- 环境变量：`.env.development` / `.env.production` 中的 `VITE_API_BASE`（默认 `/api`）。
- Node 版本要求：18+。

## 四、主题机制

- 主题变量以 **CSS 变量** 形式注入 `document.documentElement`（`utils/theme.js` 的 `applyThemeVars`）：
  - `--theme-color` / `--el-color-primary` 及主色阶梯（`--el-color-primary-light-1..5`、`--el-color-primary-dark-2`）
  - `--layout-mode`（side / top / mix）
- 优先级：**用户本地（localStorage `if_theme`）> 后端配置（`/api/sys/config`）> 默认值**。
- `store/theme.js` 持久化 `themeColor`(默认 `#409EFF`)、`layout`(默认 `side`)、`menuConfig`；`main.js` 启动时 `useThemeStore().init()` 注入。

## 五、权限指令 `v-permission`

- 注册：在 `main.js` 中 `app.directive('permission', permissionDirective)`。
- 用法：`<el-button v-permission="['ADMIN']">删除</el-button>`。
- 行为：按 `userStore.roles` 判断，无权限则 `display:none`；角色异步加载时通过 `watch` 自动刷新显隐。
- 页面级访问由路由 `meta.roles` + 守卫控制（见 `router/README.md`）。

## 六、API 封装（`api/request.js`）

- 单例 Axios，baseURL = `VITE_API_BASE || '/api'`，超时 15s。
- **请求拦截**：注入 `Authorization: Bearer <token>`（取自 `utils/auth.getToken()`）。
- **响应拦截**：解包 `Result` —— `code===200` 返回 `data`；非 Result 结构（如文件流 blob）原样返回；`401` 清 token 跳 `/login`；`403` 跳 `/403`；其他 `ElMessage.error`。
- 各业务 `api/*.js` 仅导出请求函数，不直接操作 UI。

## 七、依赖关系（顶层）

```
views      → components / api / store / router / utils
components  → api / store / utils(exportUtil/format)
store       → api / utils(auth/theme)
router      → store/user、utils
utils       → store、api(request)
main.js     → router / store / utils(permission/theme)
```
