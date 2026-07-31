# Changelog

所有 notable 变化均记录于此文件。

本文档遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/) 规范，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)（SemVer）。

## [Unreleased]

### Added
- （待规划）

### Changed
- （无）

### Fixed
- （无）

### Security
- （无）

---

## [Phase8-Wave2] - 2026-08-02

新增用户免填密码、用户所属组织、问题所属项目必填、问题弹窗竖形标签页四项改动。
迁移脚本：`scripts/V20260802_issueflow_phase8_wave2.sql`（幂等，可重复执行）。

### Added
- **用户「所属组织」**（需求 #9）：
  - 库表：`user` 新增 `org_id BIGINT NULL` + 索引 `idx_user_org`（可空，不加外键，
    与 `issue.project_id` 同口径——`organization` 走逻辑删除，外键会与 `deleted` 冲突）。
  - 后端：`User` / `UserReq` 新增 `orgId`；`UserVO` 新增 `orgId` + `orgName`
    （由 `organization.name` 反查，未归属或组织已删时为 `null`）。
  - 前端：`UserManage.vue` 新增/编辑抽屉增加「所属组织」`el-select`（平铺、可搜索、可清空，
    数据源 `GET /api/organizations?status=1`）；列表新增「组织」列展示 `orgName`。
- **问题弹窗左侧竖形标签页**（需求 #12）：`IssueFormSections.vue` 由「4 分区折叠」重写为
  `el-tabs tab-position="left"` 容器，5 个标签——基本信息 / 问题描述 / 附件上传 / 关联信息 / 操作历史。
  - `before-leave` 钩子：离开「基本信息」前校验其必填项，不通过则阻止切换并 `ElMessage` 提示。
  - 切换动画：内容区淡入 + 轻微上移（0.24s），`prefers-reduced-motion` 下自动关闭。
  - 响应式：窗口宽度 < 768px 时 `tab-position` 自动切 `top`（水平标签）。
  - `IssueForm.vue` 新增 `mode` prop（`submit` / `edit` / `view`，`view` 为整表单只读），
    编辑态附件与操作历史按标签首次激活懒加载（`GET /api/issues/{id}`、`/history`）。
- `IssueDetailDrawer.vue`（查看态）复用同一标签容器，「流转操作」常驻标签页上方，任意标签均可执行流转。

### Changed
- **新增用户不再录入密码**（需求 #7）：`UserManage.vue` 新增弹窗移除密码输入框及其校验规则，
  提交不再下发 `password`；`UserService.createUser` 在 `password` 为空/空白时调用
  `SiteConfigService.getDefaultUserPassword()`（Wave 1 已就绪，读 `site.default_password`）
  取默认密码后 BCrypt 加密落库。**编辑时密码为空仍保持原密码不变**，逻辑未动。
  `UserReq.password` 语义由「新增必填」改为「全程非必填」（原本就无 `@NotBlank`，仅更新注释与服务端分支）。
- **问题「所属项目」改为必填**（需求 #6）：`IssueCreateReq.projectId` / `IssueUpdateReq.projectId`
  加 `@NotNull`；前端 `IssueForm.vue` 项目下拉加 `required` 规则（自动渲染红星）并移除 `clearable`。
  `IssueUpdateReq.projectId` 原「非空才更新」分支在校验层保证非空后等价于始终更新，Service 未改。
- **问题描述改为非必填**（需求 #12）：移除 `IssueForm.vue` 中 `description` 的 `required` 规则
  （i18n 键 `issue.rules.descriptionRequired` 保留，供历史引用兜底）。
- `ProfileService.profile()` 的 `orgName` 由恒返回 `null` 改为按 `user.org_id` 反查 `organization.name`
  （替换第 97 行「本期未加 org_id」的历史注释）。
- 「环境信息」四字段（`envOs` / `envBrowser` / `envAppVersion` / `envDevice`）随「复现步骤」并入
  「问题描述」标签，以满足需求指定的 5 标签结构；**字段一个未减**。
- i18n 中英双语同步新增 `issue.tab.*`（5 个标签名）、`issue.tabTip.*`（3 条提示）、
  `issue.rules.projectRequired`；`user.col.org` / `user.form.org` / `user.placeholder.selectOrg` 复用既有词条。

### Removed
- `UserManage.vue` 新增用户抽屉的「密码」`el-input` 及 `rules.password` 校验规则（需求 #7）。
- `IssueFormSections.vue` 的 `el-collapse` 折叠实现与 `mode` / `showAttachment` 旧展开语义
  （`expand(name)` 方法名保留并改为「激活标签」，调用点无需改动）。

### Fixed
- 修复「用户资料页组织名称恒为空」的历史遗留（`ProfileService` 第 97 行 TODO，需求 #9 附带）。

### Security
- 默认密码仅在 `GET /api/admin/site/config`（需 `site:config:update` 权限）与
  `UserService.createUser` 服务端内部流转；公开 `GET /api/site/config` 仍只返回 7 个展示键，
  不下发 `site.default_password`；前端不再持有任何密码明文，也未写入 Pinia `appStore`。

### API 契约变更
- `GET /api/users`、`POST /api/users`、`PUT /api/users/{id}` 的响应 `UserVO` **新增** `orgId`、`orgName` 两个字段（向后兼容）。
- `POST /api/users` 的请求体 `password` 变为**可省略**（省略时服务端取默认密码）；**新增**可选字段 `orgId`。
- `PUT /api/users/{id}` 请求体**新增**可选字段 `orgId`（「存在即覆盖」，传 `null` 解除组织归属）。
- `POST /api/issues`、`PUT /api/issues/{id}` 的 `projectId` 由可选变为**必填**，
  缺失时返回 `VALID_ERROR`（**破坏性变更**：历史调用方需补该字段）。

---

## [Phase8-Wave1] - 2026-08-01

后台标题联动、菜单命名规整、模块配置页下线、前台页脚四项改动。
迁移脚本：`scripts/V20260801_issueflow_phase8_wave1.sql`（幂等，可重复执行）。

### Added
- **新增用户默认密码配置**（需求 #2）：「系统设置」页新增「安全设置」分组，
  维护 `sys_config` 键 `site.default_password`（默认 `123456`）；
  表单为密码框（支持切换明文），前端校验非空 + 长度 6~32，后端 `SiteConfigReq` 以
  `@NotBlank` + `@Size(min=6,max=32)` 同步兜底。
- **管理端站点配置读接口** `GET /api/admin/site/config`（需 `site:config:update` 权限）：
  返回全部 8 个 `site.*` 键（含敏感的 `site.default_password`），供「系统设置」页回填表单。
- **前台底部页脚**（需求 #4）：`UserLayout` 的 `.if-main` 底部新增页脚，
  展示 `site.copyright` 与 `site.icp`；二者皆空时整体不渲染；
  居中 / 12px / 次要色 / 上边框分隔，位于滚动容器之外不遮挡内容。后台布局不加。

### Changed
- **后台左上角标题跟随「网站名称」配置**（需求 #10）：`AdminLayout` 侧栏展开态由写死的
  `t('layout.logo.admin')` 改为 `appStore.siteName`（折叠态仍为 `siteShortName`）；
  顶栏 `pageTitle` 的兜底同步改为 `appStore.siteName`，与 `UserLayout` 写法一致。
- **后台菜单文案重命名**（需求 #2，路由 `path` / 组件均未变更）：
  - `/admin/system/site`：「网站设置」→「**系统设置**」（页面标题 `site.page.title` 同步）
  - `/admin/system/settings`：「系统设置」→「**备份设置**」（页面标题 `system.title` 同步）
- i18n 中英双语词条同步更新（`locales/{zh-CN,en-US}/{menu,site,system}.js`）。
- README 新增「4.6 站点配置项」与「4.7 后台菜单结构」两节，并补充默认密码说明。

### Removed
- **「模块配置」页面及菜单入口下线**（需求 #8）：
  删除 `views/admin/ModuleManage.vue`、`routes.js` 中 `/admin/modules` 路由、
  `i18nEnum.js` 的 `MENU_KEY_BY_PATH['/admin/modules']` 映射及 `menu.admin.modules` 中英词条；
  SQL 软删除 `menu` 表 `/admin/modules` 记录。
  **保留** `ModuleTreePanel.vue` / `ModuleTreeDrawer.vue`——`ProjectManage.vue` 仍复用其完成模块维护。

### Fixed
- 修复后台侧栏标题与浏览器/顶栏兜底标题不跟随「网站名称」配置的问题（需求 #10）。

### Security
- `site.default_password` 属敏感项：**不**纳入公开接口 `GET /api/site/config` 的返回白名单
  （该接口在 `SecurityConfig.WHITE_LIST` 中匿名可访问），仅经鉴权的管理端接口下发；
  同时不写入前端 Pinia `appStore`，避免随公开站点信息扩散。

---

## [1.0.0] - 2026-07-30

首个正式版本，覆盖缺陷全生命周期管理闭环。

### Added
- **问题记录**：结构化表单（标题/描述/严重等级/标签/复现步骤/环境信息/附件），自动生成唯一编号 `IS-YYYYMMDD-序号`。
- **验证流程**：五态状态机（待处理→处理中→待验证→验证通过→已关闭），支持测试回退与管理员重开，每次流转记录操作人/时间/备注。
- **RBAC**：四类角色（提交者/开发人员/测试人员/管理员）基于 JWT 的认证与按角色数据隔离。
- **统计看板**：问题趋势、状态分布、平均解决周期、解决率、严重等级占比；支持时间范围 + 版本筛选；图表可导出 PNG / Excel。
- **REST API**：27 个端点，统一返回 `Result<T>`、分页 `PageResult<T>`、API 认证（Knife4j 文档 `/doc.html`）。
- **Docker 部署**：`docker-compose.yml` 一键编排 mysql / redis / backend / frontend。
- **统一登录与双布局主题可配**：统一登录页；UserLayout / AdminLayout 双布局；主题色 / 布局 / 菜单配置（默认管理员 admin/admin123）。

### Changed
- （无）

### Fixed
- 初始版本，无历史修复项。

### Security
- JWT 无状态认证（HS256，2h 有效期）。
- 登出 / 强制失效采用 Redis 黑名单（`jwt:blacklist:{jti}`）。
- 角色权限隔离：提交者仅见自己数据，管理员全局可见；写操作按角色校验。

---

## 链接

- 仓库：https://github.com/tongbin102/issueflow
- 架构设计：`./architecture.md`
- 产品需求：`./prd.md`
- 设计笔记：`./design-notes.md`
- 架构决策：`./adr/`
- 问题记录：`./issues/issue-log.md`
