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
- **BUG-03 / scope 接线（后端，`IssueService#pageQuery`）**：现读取 `IssuePageReq.scope`，当
  `scope=mine` 且当前用户**非 ADMIN、非 SUBMITTER** 时追加 `reporter_id = 当前用户` 过滤（真·我的问题）。
  SUBMITTER 仍由上方「仅查自己」逻辑兜底（安全底线）；ADMIN 传 `scope=mine` 视为看全站、不加过滤
  （保留管理员全局排障能力）。与上一轮新增的 `Constants.SCOPE_MINE/SCOPE_ALL`、
  `IssuePageReq.scope` 字段、前端 `IssueTable` 下发 `scope` 形成完整闭环。
- **BUG-02（前端）：工作台卡片点击跳转筛选**。`UserDashboard.vue` 统计卡片加
  `@click="goList(card.status)"` 与 `cursor:pointer`，`goList(status)` 跳转
  `/user/my-issues?status={status}`；`UserIssueList.vue` 读取 `route.query.status` 沉淀为
  `listFilters.status` 并下传 `IssueTable` 的 `filters` prop。类型对齐 `useStatusOptions()`
  （value 为数字），`route.query.status` 统一 `Number()` 转换，避免跳转后筛选不生效。
- **BUG-06（前端）：`UserDashboard` 加载态**。新增 `loading` ref，`load()` 以 `loading.value = true`
  起始、`finally` 收尾；`<el-row>` 加 `v-loading="loading"`，卡片加载期间显示遮罩。
- **BUG-07（前端）：`IssueTable` 响应外部 `filters` 变化**。新增对 `props.filters` 的 `watch`（deep），
  父组件传入的筛选（如工作台卡片跳转带 status）即时合并到本地 `filters` 并重拉数据；用户本地手动改筛选只动
  本地 `filters`、不触发该 watch，无死循环。
- **回归修复（Round 2，QA 第 2 轮回归验证）**：本轮针对 8/1 接线修复的 A / B / C 三项回归问题，
  与上方 BUG-02 / 03 / 06 / 07 同源（同一 `UserDashboard` / `DashboardService` / `IssueService` 链路），
  未回退既有改动。
  - **A（P0 回归）— 修复「查看我的」按钮事件参数 bug**（`src/frontend/src/views/user/UserDashboard.vue` L27）：
    原 `@click="goList"` 为裸函数引用，Vue 会把 `MouseEvent`（`PointerEvent`）作为首个实参传入
    `goList(event)`，导致跳转 URL 出现 `?status=[object PointerEvent]` → `Number()` 得 `NaN`
    → 后端返回 400。改为 `@click="goList()"`，并在 `goList(status)` 内增加空值 / `NaN` 守卫：
    当 `status == null || Number.isNaN(Number(status))` 时走无参分支（查全量 / 默认态）。
    **边界已验证**：`status=0`（待处理）经 `Number(0)` 得 `0`，非 `NaN`，守卫不误吞，正常命中「待处理」筛选。
  - **B（P1 行为变更，⚠️ 用户可见）— 工作台统计口径与列表页对齐为「真·我的」**
    （`src/backend/.../service/DashboardService.java` L31）：
    由 `Constants.ROLE_SUBMITTER.equals(roleCode) ? currentUser : null`
    改为 `Constants.ROLE_ADMIN.equals(roleCode) ? null : currentUser`。
    **行为变更说明**：ADMIN 仍统计全站（`currentUser=null` 不加过滤）；
    SUBMITTER / DEVELOPER / TESTER 由原先统计「全站」收窄为统计「本人提交的问题」
    （`reporter_id = 当前用户`），与 `IssueService#pageQuery` 的 `scope=mine` 口径对称。
    **⚠️ 提示**：从不提单的 DEVELOPER / TESTER 打开工作台可能看到全 0，这是口径变更的预期结果，
    **不是数据丢失**（8/1 曾有整库丢失事故，请勿据此误判为故障）。
  - **C（P2）— 加载态覆盖补全**（`UserDashboard.vue` L23）：趋势图卡片补加 `v-loading="loading"`，
    此前 `loading` 仅覆盖状态卡片行，趋势图在加载期间无遮罩；现与状态卡片行一致显示加载态。

> **本期修复日期：2026-08-01**（与上一轮 BUG-01 `cnt→count` 别名、scope 字段/常量补丁同源，本次补齐
> BUG-02/03/06/07 接线，未回退、未重复修改上一轮已落盘改动）。

### Known Issues
- **B-R3（待 PM 确认）— 「真·我的」口径维度与角色职责错配**：
  DEVELOPER / TESTER 的工作对象通常是「指派给自己的问题」（`assignee_id`），
  而当前「真·我的」口径按「本人提交」（`reporter_id`）过滤，二者语义不同——
  DEVELOPER / TESTER 即便自己不提单，也可能被指派大量问题，
  现行 `reporter_id` 口径会令其工作台 / 列表看不到这些被指派的问题。
  产品语义待 PM 确认，可能后续调整为**按角色区分 reporter / assignee 维度**
  （如 DEVELOPER / TESTER 默认走 `assignee_id`、SUBMITTER 走 `reporter_id`）。
  本轮未做该调整，仅作为已知残留记录，留待 PM 决策。

### Security
- （无）

---

## 生产事故恢复 & 备份加固（2026-08-01）

**issueFlow 生产数据库 `issueflow_db` 整库丢失事故**的恢复与善后。
本条目如实记录事故经过与代价，供后续追溯，请勿删改。

### 事故记录（Incident）

- **2026-08-01 11:13**，24 号机（`10.55.3.24`）MySQL 容器 `mysql-gihtg`
  被外部项目 **domainHub 的 reset 脚本重建**，`issueflow_db` **整库消失**。
- **无任何备份可恢复**：`/home/jsadmin/db-backups/` 历史上只备份了
  `quiz_test` 与 `weekly_report`，**从未包含 `issueflow_db`**。
- **后果**：**7/30 上线以来的全部业务数据永久丢失，不可找回**
  （issue / 附件记录 / 操作历史 / 用户自建数据等）。
- **恢复方式**：经授权后用仓库内 16 个 SQL 迁移脚本**从零重建**库结构与种子数据，
  共 26 张表，线上服务已恢复；**业务数据未恢复、也无法恢复**。
- **同实例其他项目库（`quiz_test` / `weekly_report` / `mysql` / `sys`）未受影响**，
  已用 `scripts/verify-other-dbs.sh` 只读佐证。

### Added

- **新增 `scripts/backup-issueflow-db.sh`：`issueflow_db` 每日自动备份**
  （事故直接整改项）。已部署到 24 号机并安装 cron `0 2 * * *`（每日 02:00）。
  - 产物 `/home/jsadmin/db-backups/issueflow_db-YYYYMMDD-HHMMSS.sql.gz`；
    `mysqldump` 参数含 `--default-character-set=utf8mb4` `--single-transaction`
    `--routines` `--triggers` `--events`，只导 `--databases issueflow_db`
    （**严禁 `--all-databases`**，该实例多项目共用）。
  - **保留最近 7 天**：只匹配 `issueflow_db-` 前缀，再用
    `^issueflow_db-[0-9]{8}-[0-9]{6}\.sql\.gz$` 严格校验，时间取自**文件名**而非
    `mtime`；命名不符者一律跳过不删；**永远至少保留最新 1 份**。
    `pre-reinstall-*` / `business-only-*` / `restore-users.sql` 等其他项目备份**绝不触碰**。
  - **密码不落库、不进 `ps`**：经 `MYSQL_ROOT_PASSWORD` 环境变量或
    `/home/jsadmin/.issueflow-backup.env`（`chmod 600`，属主 `jsadmin`，不入 git）读取，
    运行期通过 `--defaults-extra-file` 临时文件传给 `mysqldump`，`trap` 保证用完即删。
  - **产物校验**：非空 + 大小下限 + `gzip -t` + 抽检含 `CREATE TABLE`，
    任一不过即**丢弃半成品并非 0 退出**；先写 `.partial` 再改名，杜绝半成品冒充可用备份。
  - 日志追加至 `/home/jsadmin/db-backups/backup.log`。
- **新增 `scripts/restore-run-order.sh`**：固化 16 个 SQL 的**正确执行顺序**并逐个校验，
  失败即停；支持 `REBUILD=1` 重建（**库名硬编码 `issueflow_db`，不接受外部传参**，防误 DROP 他库）。
- **新增 `scripts/verify-other-dbs.sh`**：只读佐证脚本，逐表统计
  `quiz_test` / `weekly_report` 行数，证明灌库未误伤同实例其他项目。

### Fixed

- **补齐 7 个 SQL 缺失的 `SET NAMES utf8mb4;` 声明**（中文双重编码缺陷）：
  `src/backend/src/main/resources/db/schema.sql`、`db/data.sql`、
  `scripts/migrate-add-updated-at.sql`、
  `scripts/V20260801_issueflow_phase8_wave1.sql`、
  `scripts/V20260802_issueflow_phase8_wave2.sql`、
  `scripts/V20260803_issueflow_phase8_wave3.sql`、
  `scripts/V20260804_issueflow_phase8_wave4.sql`。
  至此仓库 **16 个 SQL 全部覆盖**。
  - **成因**：容器内 mysql 客户端 `default-character-set=auto` 实测解析为 **latin1**，
    他人用 `mysql < xxx.sql` 手工执行时，中文字面量按 latin1 解释后转存 utf8mb4，
    产生双重编码（`管理员` 应为 `E7AEA1E79086E59198`，实测写成 `C3A7C2AEC2A1...`）。
  - **Spring 兼容性已确认**：`schema.sql` / `data.sql` 由 `spring.sql.init` 加载，
    项目未覆盖 `spring.sql.init.separator`，Spring Boot 3.2.5 的 ScriptUtils
    使用默认分隔符 `;`，`SET NAMES utf8mb4;` 为合法独立语句；两文件均不含
    `DELIMITER`，切分后首条语句即 `SET NAMES utf8mb4`，**解析不受影响**（已实测）。
  - **验证方式**：在临时库 `issueflow_charset_check` 中**故意不加**
    `--default-character-set` 执行 `schema.sql` + `data.sql`，
    `HEX(name)` 输出 `E7AEA1E79086E59198`（正确），校验后该临时库已 DROP。

### Security

- 备份凭据以 `chmod 600` 独立文件存放于服务器，**仓库内不含任何 root 密码明文**；
  crontab 中亦不含密码。

### Docs

- `README.md` 新增 **4.11 数据库备份与恢复**：备份脚本与 cron 计划、灾难恢复步骤、
  **SQL 正确执行顺序表**（含「**文件名字母序 ≠ 执行顺序**」警告，
  典型反例：Phase7 字母序在 Phase6 之前但依赖 Phase6 的 `issue.type_id`）、
  以及 `SET NAMES utf8mb4` 约定；4.3 常用脚本补充三个新脚本。

> **遗留项**：本次仅恢复库结构与种子数据，**业务数据无法找回**。
> 另建议推动 domainHub 侧收敛其 reset 脚本的作用域，避免再次波及共用实例内的他方库。

---

## Code Review & 清理（2026-08-01）

基于 Code Review 报告执行的一轮**安全、最小变更**清理，属纯工程整洁度整理，
**不涉及任何功能、接口、数据或行为变化，对线上功能零影响（纯清理）**。

### Removed
- **移除死依赖 `hutool-all`**（`src/backend/pom.xml`）：全代码库 `import cn.hutool` 计 0 处引用，
  确认为未使用依赖，删除其 `<dependency>` 声明及配套的 `<hutool.version>` 属性；其余 14 个依赖保持不动。
- **清理 gitignored 临时文件**：删除 `src/frontend/install.log`、`src/frontend/install2.log`
  与 `scripts/.p7a.sql`、`scripts/.p7bc.sql`（均已被 `.gitignore` 忽略、从未入库，删除无版本风险）。
  另：`src/frontend/dist-qa-*`（9 个历史构建产物目录）因单目录约 88 文件、超出 sandbox 批量删除守卫阈值 50
  而被拦截，未强行绕过，移交主理人统一清理；**未触碰** `node_modules` / `dist` / `target` 等正常可再生构建缓存。

### Fixed
- **填充 3 个文件共 7 处空 `catch` 块**（低风险整洁度）：为静默吞异常的 `catch (e) {}` 空块补充
  `console.error('[组件] 上下文 failed:', e)` 错误日志，便于线上排障；仅新增日志，**成功逻辑与既有行为零改动**。
  - `src/frontend/src/components/ModuleTreePanel.vue`：2 处（`deleteModule` / `batchDeleteModule`）
  - `src/frontend/src/views/admin/FlowConfig.vue`：4 处（`loadFlowConfig` / `deleteFlowNode` /
    `deleteFlowTransition` / `resetFlowDefault`）
  - `src/frontend/src/views/user/IssueCreate.vue`：1 处（`createIssue`）
  - 另有 `catch (e) {} finally { ... }` 型（`finally` 内含状态复位）非本轮范围，保持原样不动。

> **范围说明**：本轮为纯清理，未改动任何生产安全配置（JWT 密钥兜底 / SQL 日志 / `sql.init.mode` 等留作后续专项决策），
> 未做鉴权 AOP 重构 / 权限缓存改造 / 大文件拆分等较大重构；README 无对外变化，未改。

---

## [Phase8-Wave5] - 2026-08-05

UI / 数据优化三项：**#1** 头像下拉顺序调整、**#2** 前台侧边菜单默认展开且刷新保持、
**#4** Phase6 图标白名单缺口补齐（根治 W4 遗留项）。
前端改动：`layouts/UserLayout.vue`、`components/SideMenu.vue`；
迁移脚本：`scripts/V20260805_issueflow_phase6_whitelist_fix.sql`（幂等，可重复执行），
并对 `scripts/V20260803_issueflow_phase6.sql` §12.2 白名单追加一行取值（根治）。

> **协作说明**：本 Wave 与另一轮的「问题表单交互」项（#3）并行开发、文件互不重叠；
> 本节仅记录本轮负责的 #1 / #2 / #4，未触碰 `IssueForm.vue` / `IssueFormSections.vue` /
> `FormDrawer.vue` / `UserIssueList.vue` / `locale/issue.js`。

### Added
- （无新功能；均为既有能力的交互 / 数据订正）

### Changed
- **#1 头像下拉「清理缓存」上移到「个人中心」之上**（`layouts/UserLayout.vue`）：
  前台顶栏头像下拉最终顺序调整为 **清理缓存 → 个人中心 → 退出登录（`divided`）**。
  仅交换两个 `el-dropdown-item` 位置，文案（i18n `layout.topbar.clearCache` /
  `layout.topbar.profileCenter`）与 `onCommand` 逻辑零改动。
- **#2 前台左侧菜单默认展开 + 刷新保持 + 手动折叠**（`components/SideMenu.vue`，仅前台 `type=1`）：
  - 页面加载时**所有层级**父菜单自动展开（递归 `collectParentIndices` 覆盖多级嵌套，非仅顶层）。
  - 用户手动折叠 / 展开后刷新页面**保持该状态**：以 `localStorage['if-menu-closed-type1']`
    记录「已手动折叠」集合，初始展开项 = 全部父级 index − 已折叠集合。
  - 保留 el-menu 原生点击折叠 / 展开交互（`@open` / `@close` 仅做持久化，不干预内部状态）。
  - 新增顶层 prop `defaultExpandAll`（Boolean，默认 `false`）；`UserLayout.vue` 的
    `<SideMenu :type="1" />` 改为 `<SideMenu :type="1" :default-expand-all="true" />`。
  - **后台（`type=2`）零行为变更**：不注入任何展开 / 持久化绑定，渲染时机与原逻辑一致。
  - **实现说明（对需求的必要偏差）**：本项目 `element-plus@2.14.3` 的 `el-menu`
    只有 `default-openeds`（**无** `openeds`），且该 prop 仅在组件创建时读取一次、非响应式。
    故改用 `default-openeds` + 就绪门闸 `menuReady`（等菜单树异步加载完、`openedMenus`
    算好后再渲染顶层 `el-menu`），以拿到正确初始展开态；`default-openeds` 里含被权限过滤的
    父级 index 亦无害（仅存在性比对，不会像 `menuRef.open()` 那样抛错）。

### Fixed
- **#4 Phase6 图标白名单缺口补齐（根治）**：
  - `scripts/V20260803_issueflow_phase6.sql` §12.2 的白名单自愈 `UPDATE` 追加
    `FolderOpened` / `Share` / `Files` / `SetUp` / `Timer` 五个取值，使 Wave4 新图标
    （及 Phase7 的 `Timer`）在「单独重跑 Phase6」时不再被回刷为 `Grid`。
  - 新增幂等迁移 `scripts/V20260805_issueflow_phase6_whitelist_fix.sql`：对受影响菜单
    （id=17 项目管理→`FolderOpened`、id=16 流程管理→`Share`、id=6 流程配置→`Share`、
    id=26 文件管理→`Files`、id=29 配置管理→`SetUp`）按 `id` 守卫式重断言，并复制带扩充
    白名单的自愈语句 + 末尾自检 `SELECT`（校验 5 个 id 均为新值、`deleted=0` 全盘无 `Grid`
    占位 / 无空 icon）。
  - 本项即 **Wave4 CHANGELOG「运维注意」遗留项（根治方案）** 的落地：原
    「任何时候单独重跑 Phase6，必须紧接着重跑一次 W4」的运维约定自此不再必需。

### Security
- 无。#1 / #2 为前端交互 / 展示层改动；#4 仅订正 `menu.icon` 展示字段与白名单，
  不涉及权限码、鉴权链路与任何敏感配置。

### #3 提交新问题弹窗标签重做与提交修复

> 与本 Wave 的 #1 / #2 / #4 并行开发、随后合入本章节归档；#3 的改动集中在
> 「问题表单交互」链路，与上文各项文件互不重叠（`FormDrawer.vue` /
> `IssueForm.vue` / `IssueFormSections.vue` / `views/user/UserIssueList.vue` /
> `locales/{zh-CN,en-US}/issue.js`），**未触碰** K1 已交付的
> `UserLayout.vue` / `SideMenu.vue` / `V20260803` / `V20260805` / README §4.7。

- **标签「已填写」红点标识**（#3.1）：`IssueForm.vue` 新增 `filledTabs` 计算属性
  （`{ basic, detail, attachment }`，基于 `model` 响应式），并以 `:filled-tabs`
  传给 `IssueFormSections.vue`；后者用 `el-tab-pane` 的 `#label` 插槽渲染
  「标签文字 + 纯 CSS 小红点」，仅在对应标签有内容时点亮。判定规则：
  - basic：标题非空，或类型 / 来源 / 严重 / 优先级 / 项目 / 模块任一非默认值，或标签为非空数组；
  - detail：描述 / 复现步骤 / 四项环境信息任一非空；
  - attachment：新建态本地暂存文件 > 0，编辑态已上传附件 > 0。
  内容持久保存在 `model` 中，切换标签不清空、红点保持。
- **标签自由切换、不再离开校验**（#3.2）：移除 `IssueForm.vue` 的 `:before-leave`
  绑定与 `onBeforeLeaveTab` / `BASIC_FIELDS`；`IssueFormSections.handleBeforeLeave`
  恒返回 `true`（`beforeLeave` prop 保留仅为向后兼容）。全量校验仅在点击「提交」时
  由 `IssueForm.submit()` 统一执行；校验未过时**显式 `ElMessage.warning`
  「请完善必填项后再提交」**，并定位首个错误标签 + 滚动高亮错误字段。`@tab-change` 懒加载保留。
- **文案「问题描述」→「详细信息」**（#3.3）：`locales/zh-CN/issue.js` 的
  `issue.tab.description` 由「问题描述」改为「详细信息」；`en-US` 对应键
  `Description` → `Details`。模板仍用 `t('issue.tab.description')`，仅改 locale。
- **提交修复（根治「系统错误」）**（#3.4）：`views/user/UserIssueList.vue` 的
  `onCreateSubmit` 由「按扁平字段逐个 `append`」改为与 `IssueCreate.vue` 一致的
  **单个 `issue` JSON part**——
  `fd.append('issue', new Blob([JSON.stringify(data)], { type: 'application/json' }))`
  + `files` 逐个 `append`，命中后端 `IssueController.create` 的
  `@RequestPart("issue") @Valid IssueCreateReq` 契约。空表单点提交在 `submit()`
  校验阶段即被拦截并给 warning，**不发起请求**、不再触发「系统错误」。
  （`views/admin/AdminIssueList.vue` 无「新建」入口、仅编辑走 `updateIssue(JSON)`，无同类 bug，未改。）
- **全屏适配：标签布局跟随全屏态**（#3.5）：`FormDrawer.vue` 在 `setup` 顶层
  `provide('drawerFullscreen', isFullscreen)`（响应式 ref）；`IssueFormSections.vue`
  `inject('drawerFullscreen', ref(false))`，`tabPosition` 改为
  `computed(() => (drawerFullscreen || isNarrow) ? 'top' : 'left')`——弹窗全屏时标签
  横排（top）、收缩时恢复竖排（left），两种布局红点均可见。`IssueDetailDrawer` 等无
  provide 的调用点 inject 默认 `false`，行为不变；抽屉 `onClosed` 仍复位
  `isFullscreen=false`，provide 的 ref 自动同步。
- **移动端适配**（#3.6）：`FormDrawer.vue` 引入 `useAppStore`，`isMobile`（`appStore.isMobile`
  或视口 `<=768px`）；`watch(modelValue)` 在移动端打开时强制 `isFullscreen=true`
  （满宽 + 标签自动横排），全屏按钮 `v-if` 收紧为 `fullscreenable && !isMobile`（移动端隐藏）。

---

## [Phase8-Wave4] - 2026-08-04

菜单体系收尾订正：基础设施排序、全量图标语义统一、历史残留菜单行清理。
迁移脚本：`scripts/V20260804_issueflow_phase8_wave4.sql`（幂等，可重复执行）。
**本 Wave 为纯数据订正，前后端代码零改动**（`.vue` / `.java` 均未修改）。

> **⚠️ 验收范围提示（务必先读）**
> 本次**唯二有界面可见变化**的改动是 **#1（基础设施排序）** 与 **#3（图标统一）**。
> **#5 部署后界面无任何可见变化** —— 被删的 `id=7` / `id=18` 经 24 号库实机核查
> 均为 `deleted=1` 软删行，本就不渲染到侧边栏。
> 线上**不存在**「系统设置 → 点击跳 404」这个入口，**请勿按此去找并据以验收**。

### Added
- （无，本次不涉及新功能）

### Changed
- **「基础设施」排到「系统管理」下方**（需求 #1）：`menu.sort` 由 `6` 调整为 `8`（`id=25`）。
  `parent_id` 保持 `0` 不变——二者本就是根级兄弟节点，**不做父子嵌套**，仅修正显示先后。
  根级最终顺序（仅计可见节点 `deleted=0`）：
  概览 → 业务管理 → 问题类型 → 流程管理 → 系统管理 → 基础设施。
  （`id=17`「项目管理」已于 Phase7 软删除、不渲染，故不在根级顺序中；
  `id=7`「系统设置」同为软删行，见下方 Removed。）
- **菜单图标语义统一**（需求 #3）：后台 8 项图标订正，消除 `Grid` 占位与跨子树的无意义重复；
  前台 4 项可见（id=14 提交问题已软删）（`HomeFilled` 工作台 / `Tickets` 问题管理 /
  `DataLine` 个人看板 / `Document` 我的问题）已全部贴切，未改动。

  | id | 菜单 | 原图标 | 新图标 | 原因 |
  |---|---|---|---|---|
  | 17 | 项目管理 | `Management` | `FolderOpened` | 软删行（`deleted=1`，不渲染），无害留存操作 |
  | 16 | 流程管理 | `Operation` | `Share` | 与「配置管理」撞名；`Share` 表流转分支 |
  | 10 | 菜单管理 | `Grid` | `Menu` | 消除 `Grid` 占位，语义精确命中 |
  | 22 | 系统设置 | `Monitor` | `Setting` | `Monitor` 语义为监控，与设置页不符 |
  | 6 | 流程配置 | `Tools` | `Share` | 与「基础设施」撞名；改为呼应父级 |
  | 4 | 流程监控 | `Switch` | `Monitor` | `Monitor` 精确命中「监控」 |
  | 26 | 文件管理 | `Folder` | `Files` | 与「项目配置」撞名 |
  | 29 | 配置管理 | `Operation` | `SetUp` | `Operation` 语义空泛；与 `Setting` 齿轮区分 |

  > `id=17`「项目管理」为软删行（`deleted=1`，不渲染），改其图标属**无害的留存操作**，
  > 仅为墓碑行日后一旦恢复即带正确图标；可见菜单中 `Management` **本就唯一**
  > （即 `id=2` 业务管理），原「与 `id=2` 撞名」的说法**不成立**，已订正。
  > 因此「业务管理」（`id=2`）保留 `Management`：该图标在可见菜单内唯一，
  > 语义上也正是最佳匹配，故不产生无效 UPDATE。
  > 剩余同名图标均为**父子 / 同族呼应**（`Share`：流程管理 / 流程配置；
  > `Setting`：系统管理 / 系统设置 / 备份设置 / 文件配置），非无意义重复。
  > 全部取值已核对为 `@element-plus/icons-vue` 真实导出名（`main.js` 全量全局注册，
  > `SideMenu.resolveIcon()` 对非法名兜底 `Grid`）。

### Removed
- **清理两行历史残留菜单记录**（需求 #5）：`DELETE FROM menu WHERE id IN (7, 18)`。
  **两行均为 `deleted=1` 软删行，本就不渲染到侧边栏，删除后无任何用户可见变化。**
  本项是数据库层面的墓碑行清理，**不是**活跃故障修复。
  - `id=7`「系统设置」`/admin/settings` —— Phase2 遗留根级入口，`routes.js` 无此路由。
    该行**已被两次软删**：Phase3（`V20260730_issueflow_phase3.sql` L43）、
    Phase6（`V20260803_issueflow_phase6.sql` L161）。实际 `deleted=1`，
    **从未渲染到侧边栏，线上不存在这个 404 入口**，本次仅做物理清理。
    （真实设置页另有菜单指向，与本次删除无关：`id=22` → `/admin/system/site`、
    `id=19` → `/admin/system/settings`。）
  - `id=18`「模块配置」`/admin/modules` —— Wave1（#8）下线页面与路由时已软删除
    （`deleted=1`，不渲染），本次做**物理清理**，去掉残留脏行。
  - `DELETE` **刻意不加 `AND deleted = 0`**：两行均已是 `deleted=1`，
    加该条件会导致其（尤其 `id=18`）永远清理不掉。
  - 无 `role_menu` 类关联表（菜单可见性由 `menu.permission` + 用户权限码驱动），
    且两行均无子节点，**软删行的删除无任何副作用**，不产生孤儿关联或悬挂子树。

### Fixed
- 修复「基础设施」显示在「系统管理」之前、与 README §4.7 所述菜单结构不一致的问题（需求 #1）。

> **勘误（本条为 QA 门禁第 1 轮回退后订正）**：本区块原列有
> 「修复后台侧边栏『系统设置』（`/admin/settings`）点击跳 404 的失效入口问题」一条，
> 经 24 号库实机核查（menu 全表 31 行为地面真值）**该描述失实**：`id=7` 实际 `deleted=1`，
> 线上从无该入口，不存在可点击的 404。该条目已删除，需求 #5 的真实性质改列入
> 上方 **Removed**（历史残留墓碑行清理，用户可见影响为零）。

### Security
- 无。本次仅订正 `menu` 表展示层字段（`sort` / `icon`）并删除两行软删菜单记录，
  不涉及权限码、鉴权链路与任何敏感配置。

### 运维注意（WARN：Phase6 图标白名单会回刷本 Wave 新图标）
- `V20260803_issueflow_phase6.sql` §12.2（L151-159）有一条**白名单式 icon 自愈**：
  任何不在白名单内的 `icon` 会被强制刷成 `Grid`。本 Wave 新值
  `FolderOpened` / `Share` / `Files` / `SetUp` **均不在该白名单内**
  （`Menu` / `Monitor` / `Setting` 在白名单内，不受影响）。
- **按正常版本序执行（Phase6 早于 W4）无任何风险**，W4 是最后一次写入，不会被覆盖。
  仅当「W4 执行后又**单独重跑 Phase6**」时，上述 4 个图标会被刷回 `Grid`。
- **处置**：本次未在 W4 末尾追加自愈语句 —— 该风险场景中 Phase6 在 W4 之后执行，
  写在 W4 末尾的语句都先于它运行，起不到防护作用（属无效加固）。
  W4 §2 的 8 条 `UPDATE` 本身即幂等自愈：**重跑 W4 即可复原全部图标**。
- **运维约定**：任何时候单独重跑 Phase6，必须紧接着重跑一次 W4。
- **根治方案（遗留项）**：把 4 个新值补进 Phase6 §12.2 白名单，需改动 Phase6 脚本，
  超出本次「仅文档订正、零 DML 变更」范围，留待后续 Wave 处理。

### 代码审查结论（需求 #5 附带项）
- 已审查「整体风格设置」全链路事件绑定：`AdminStyleDrawer.vue`（主题模式 / 主题色 /
  侧边菜单类型 / 内容区域宽度 / 固定 Header / 固定侧边菜单 / 色弱模式 / 恢复默认）、
  `AdminLayout.vue` 顶栏 `styleSettings` 入口、`SystemSettings.vue`、`SiteSettings.vue`。
  **全部 `@click` / `@change` 均指向已定义方法，逻辑有效，无需代码改动。**

---

## [Phase8-Wave3] - 2026-08-03

需求 #11：用户多角色（单角色 `role_id` → 多角色 `user_role` + `user.roles`）。
迁移脚本：`scripts/V20260803_issueflow_phase8_wave3.sql`（幂等，可重复执行）。

### Added
- **用户多角色模型**（需求 #11）：
  - 新增关系表 `user_role`（`user_id` + `role_code`，存码不存 id；`UNIQUE KEY (user_id, role_code)`），
    与 `role_permission` 同口径——关联随主体重建（整体替换），无逻辑删除。
  - `user` 表新增 `roles VARCHAR(500)`（JSON 数组角色码文本，如 `["ADMIN","TESTER"]`），
    作为 `user_role` 的**冗余读缓存**，用于列表 / 登录免 N+1 查询。
  - 后端新增 `entity/UserRole`、`mapper/UserRoleMapper`、`service/UserRoleService`
    （`listRoles` / `normalize` / `replaceRoles` 整体替换 / `removeByUserId`）。
  - `User` / `UserReq` / `UserVO` 新增 `roles`（`List<String>`）；`User.roles` 用
    `JacksonTypeHandler`（`autoResultMap=true`）读写 JSON 列。
  - `UserController` 新增 `GET /api/users/{id}/roles` → 返回该用户全部角色码（编辑回显兜底）。
  - `SecurityUtils` 新增 `getCurrentRoleCodes()`（全部角色）/ `hasRole(String)`；
    `getCurrentRoleCode()` 改为主角色优先级逻辑（ADMIN → 非 SUBMITTER → 首位）。
  - 前端：`UserManage.vue` 角色下拉由单选改为**多选** `el-select multiple collapse-tags`，
    列表角色列改为多 `el-tag` 展示（首个为主角色）；`api/user.js` 新增 `listUserRoles(id)`。
- i18n 中英双语新增 `user.form.roles` / `user.placeholder.selectRoles` /
  `user.tip.primaryRole` / `user.msg.rolesRequired`。

### Changed
- **JWT 多角色**：`JwtUtil.generate(Long, List<String> roles)`，`roleCode` claim 由单字符串升级为
  **角色码数组**；新增 `getRoles(String)` 同时兼容旧版单值 token，升级瞬间存量 token 不失效；
  `JwtAuthenticationFilter` 遍历 `getRoles` 逐个写入 `SimpleGrantedAuthority`。
- **权限取并集**：`PermissionService.currentRoleIds()` 由单角色升级为多角色，
  `requirePermission` 在 ADMIN 直接放行后取全部角色权限**并集**做 OR 判定。
- **状态机取并集**：`StateMachine.isAllowed(from, to, Collection<String>)` 新增多角色重载，
  用户任一角色被规则允许即放行；保留原单角色重载委托。
- **IssueFlowService.changeStatus**：流转校验改用 `SecurityUtils.getCurrentRoleCodes()` 并集，
  上下文缺失时退化为入参主角色（行为不变）。
- **用户增改对齐多角色**：`UserService.createUser` / `updateUser` 通过 `resolveRoleAssignment`
  解析 `roles`（优先）或退化兼容仅传 `roleId` 的调用方，统一对齐 `roleId`（主角色）与 `roles`，
  并整体替换 `user_role`；`getUserVO` / `AuthService.login|info` 均下发全部角色码。
- 默认管理员初始化（`IssueFlowApplication.initAdminUser`）同步写 `user.roles=["ADMIN"]` 与
  `user_role` 一行。

### Removed
- 用户角色「单选」的 `UserReq.roleId` 强制校验（`@NotNull` 已移除）；多角色下二者二选一即可，
  由 `UserService` 统一校验「不能同时为空」。

### Fixed
- 修复多角色用户登录后仅持有单角色权限、状态机仅按单一角色校验的问题（需求 #11 直接消除）。

### Security
- 角色码仅经 JWT（HS256）与 `user_role` 关系表在服务端消费，前端只读、不下发明文角色定义；
  默认密码逻辑不变（仍仅在服务端与管理端接口内流转）。
- JWT 升级**向后兼容**：旧版单值 `roleCode` token 仍可正常解析，避免升级瞬间全员强制重新登录。

### API 契约变更
- `GET /api/users/{id}` 响应 `UserVO` **新增** `roles`（`List<String>`，全部角色码，单角色为单元素，向后兼容）。
- `POST /api/users`、`PUT /api/users/{id}` 请求体**新增**可选字段 `roles`（`List<String>` 角色码数组）；
  `roleId` 仍保留（兼容历史调用方，缺省时退化取 `roles` 首位对应的角色 id 为主角色）。
- **新增** `GET /api/users/{id}/roles` → `List<String>`（该用户全部角色码）。
- `LoginVO` **新增** `roles`（`List<String>`），与 `userInfo.roles` 一致。

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
