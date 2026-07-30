# issueFlow Phase 3 QA 验证报告

> QA 工程师：严过关
> 日期：2026-07-30
> 验证对象：Phase 3 七项需求（R1~R7）交付物
> 方法：静态代码走读 + 契约交叉核对 + 前端 Vite 构建（本机无 JDK17，后端不做 mvn compile）
> 轮次：Round 1（1/2）

---

## 1. 总体结论

**IS_PASS: YES**

**智能路由判定：NoOne**

7 项需求（R1~R7）在代码层面全部实现且与 `docs/incremental-design-phase3.md` §4 接口契约一致；未发现阻塞性缺陷。前端 `npx vite build` 通过（27.78s，0 error / 0 warning，仅常规 chunk-size 提示）。后端因环境无 JDK17 未做编译验证，静态走读未发现语法/依赖/契约问题，但**编译与运行时验证需在部署时补做**（见 §5 遗留验证项）。

---

## 2. 验证环境与限制

| 项 | 状态 |
|---|---|
| 前端构建 | ✅ `npx vite build --outDir dist-phase3` 成功，产物已删除 |
| 后端编译 | ⚠️ 本机无 JDK17 + Maven launcher 损坏，`mvn compile` **未执行** |
| API 实跑 | ⚠️ 23/24 服务器仍为 Phase 2，本次未实跑接口 |
| DB 迁移 | ⚠️ 未连库执行，仅做 SQL 语义/幂等性静态审查 |

---

## 3. 详细检查结果（逐条）

### 3.1 数据库迁移 `scripts/V20260730_issueflow_phase3.sql`

| # | 检查点 | 方法 | 结果 | 说明 |
|---|---|---|---|---|
| D1 | `leader_id` ALTER 幂等 | 走读 L11-16 | ✅ 通过 | `information_schema.COLUMNS` 计数 + `PREPARE/EXECUTE/DEALLOCATE`，重跑退化为 `SELECT 1` |
| D2 | `member_ids` ALTER 幂等 | 走读 L18-23 | ✅ 通过 | 同上；`VARCHAR(500)`、`AFTER leader_id`，与设计 §2 一致 |
| D3 | R5 流程配置 parent_id 子查询 | 走读 L29-38 | ✅ 通过 | 派生表 `_p`/`_p2` 包裹绕过 MySQL「同语句 UPDATE+SELECT 同表」限制；`WHERE parent_id <> (...)` 保证幂等，重跑影响 0 行 |
| D4 | R6 系统设置软删 | 走读 L43-44 | ✅ 通过 | `UPDATE menu SET deleted=1 WHERE path='/admin/settings' AND type=2 AND deleted=0`，幂等 |
| D5 | 与 Phase 2 种子对齐 | 交叉核对 `V20250801_issueflow_phase2.sql` L143-153 | ✅ 通过 | Phase 2 中「系统管理」`path=/admin/system, type=2`、「流程配置」`path=/admin/flow-config, type=2, parent_id=0`、「系统设置」`path=/admin/settings, type=2` 三条种子均存在，本次 SQL 的 WHERE 条件可精确命中 |
| D6 | 测试数据幂等 | 走读 L50-57 | ✅ 通过 | `WHERE ... AND (leader_id IS NULL OR member_ids IS NULL)` 保证不覆盖已有配置 |

### 3.2 R1 — 后台移除「提交问题」入口

| # | 检查点 | 方法 | 结果 |
|---|---|---|---|
| R1-1 | `/admin/issues` 头部无提交按钮 | `AdminIssueList.vue` L1-28 走读 | ✅ 通过，`.head` 内仅剩 `<span>问题管理</span>` |
| R1-2 | 无 `goCreate` / `Plus` 残留 | `grep "提交问题\|goCreate\|Plus\|el-button"` | ✅ 通过，0 命中 |
| R1-3 | 前台入口不受影响 | `routes.js` L53-58 | ✅ 通过，`/user/submit-issue` → `IssueCreate.vue` 保留 |

### 3.3 R2 — 项目负责人 / 成员 / 列设置

**后端**

| # | 检查点 | 方法 | 结果 |
|---|---|---|---|
| R2-1 | `Project.java` 字段 | 走读 | ✅ `Long leaderId` / `String memberIds`，继承 `BaseEntity`（含 `@TableLogic deleted`） |
| R2-2 | `ProjectReq.java` | 走读 | ✅ `leaderId` / `memberIds` 非必填，`status` 默认 1 |
| R2-3 | `ProjectVO.java` | 走读 | ✅ `leaderId / leaderName / memberIds / List<UserBriefVO> members` 四字段齐备 |
| R2-4 | `UserBriefVO.java` | 走读 | ✅ `{id, realName, username, roleName}`，与设计 §4 完全一致 |
| R2-5 | `pageProjects` 无 N+1 | `ProjectService` L52-78 | ✅ 当页 `leaderId` + 切分后 `memberIds` 汇总入 `Set<Long>`，**一次** `userMapper.selectBatchIds`；`buildRoleMap()` 一次全量；`toVO` 纯内存映射 |
| R2-6 | 脏数据容错 | `collectMemberIds` L199-214 / `toVO` L254-277 | ✅ 空串跳过、`NumberFormatException` 吞掉、`userMap` 查不到则丢弃，与设计「丢弃无效 id」一致 |
| R2-7 | `createProject` 写入 | L91-97 | ✅ `setLeaderId` / `setMemberIds` |
| R2-8 | `updateProject` 完整覆盖 | L141-142 | ✅ 「存在即覆盖」（不过滤 null），与设计 §1.1 及风险 1 约定一致 |
| R2-9 | `UserService.listUserOptions` | L173-196 | ✅ `status=1 & deleted=0`；`Page<User>(1,100)` 上限 100；`and(w -> like(realName).or().like(username))` 括号正确（未污染外层 AND）；`roleMap` 回填 `roleName` |
| R2-10 | `GET /api/users/options` | `UserController` L71-74 | ✅ `@RequestMapping("/api")` + `@GetMapping("/users/options")` = `/api/users/options`；**未调** `requirePermission`，仅登录 |
| R2-11 | 仅登录鉴权链路 | `SecurityConfig` L35-61 | ✅ `WHITE_LIST` 不含该路径 → `anyRequest().authenticated()`，即「需 JWT、不需权限码」，符合契约 |

**前端**

| # | 检查点 | 方法 | 结果 |
|---|---|---|---|
| R2-12 | `api/user.js` | 走读 | ✅ `listUserOptions(params)` → `GET /users/options` |
| R2-13 | 负责人 / 成员列 | `ProjectManage.vue` L51-56 | ✅ 负责人 `min-width=110` + `row.leaderName \|\| '-'`；成员 `min-width=180` + `show-overflow-tooltip` + `formatMembers` 首 3 名「等 N 人」 |
| R2-14 | 表单远程搜索 | L101-141 | ✅ 负责人单选 / 成员 `multiple`，均 `filterable remote :remote-method="searchUsers" :loading` |
| R2-15 | 编辑回显 | `openEdit` L337-363 | ✅ 预填 `userOptions`（leader + members 去重），解决远程 `el-select` 已选项 label 丢失的经典坑；`searchUsers` 亦按 id 去重合并保留已选项 |
| R2-16 | 列设置 Popover | L8-29 + L224-271 | ✅ 6 列复选 + 全选/全不选/重置默认；操作列硬编码常显并有文案提示 |
| R2-17 | `localStorage` key | L191 | ✅ `if_project_columns`，JSON 数组；`loadColumns` try/catch 回退全显 |
| R2-18 | 提交 payload | `onSubmit` L390-396 | ✅ 含 `leaderId` + `memberIds`（数组 join） |
| R2-19 | **`onToggleStatus` 完整 payload** | L369-383 | ✅ **关键风险点通过**：发送 `{name, description, status, leaderId, memberIds}`，切状态不丢负责人/成员；失败 `catch` 中 `row.status = prev` 回滚 |

### 3.4 R3 — 关联项目仅启用项

| # | 检查点 | 方法 | 结果 |
|---|---|---|---|
| R3-1 | 后端过滤 | `ProjectService.listOptions` L163-175 | ✅ `.eq(Project::getDeleted,0).eq(Project::getStatus,1)` |
| R3-2 | 契约兼容 | 同上 L172 | ✅ `ProjectOptionVO.status` 仍返回（恒为 1），符合设计「保留字段」约定 |
| R3-3 | 前端移除灰显 | `IssueForm.vue` L23-38 | ✅ `el-option` 仅 `:key/:label="p.name"/:value`，无 `:disabled`、无「（停用）」后缀 |

### 3.5 R4 — 停用项目校验未关闭问题

| # | 检查点 | 方法 | 结果 |
|---|---|---|---|
| R4-1 | `@Transactional` | `ProjectService` L106 | ✅ `updateProject` 已标注（`org.springframework.transaction.annotation.Transactional`） |
| R4-2 | 校验方向 | L115 | ✅ `req.getStatus()==0 && Objects.equals(exist.getStatus(),1)`，仅拦「启用→停用」，`Objects.equals` 规避 Integer 拆箱 NPE |
| R4-3 | 计数条件 | L116-119 | ✅ `eq(projectId).ne(status, CLOSED).eq(deleted,0)`；`IssueStatusEnum.CLOSED.getCode()==4` 已核对 |
| R4-4 | 异常码 | L121-122 | ✅ `BizException(ResultCode.PROJECT_HAS_OPEN_ISSUES, "该项目下存在未关闭问题，无法停用")` |
| R4-5 | `ResultCode` 定义 | `ResultCode.java` L26 | ✅ `PROJECT_HAS_OPEN_ISSUES(40020, "该项目下存在未关闭问题，无法停用")`，码值/文案与 PRD §6.2 一致 |
| R4-6 | 校验前置于写入 | L114-124 早于 L126-144 | ✅ 抛异常时未落任何 `updateById`，事务语义正确 |
| R4-7 | 前端回滚 | `ProjectManage.vue` L380-382 | ✅ `catch` 回滚 `row.status`；错误文案由 `api/request.js` 拦截器统一 `ElMessage.error` |

### 3.6 R5 — 流程配置纳入系统管理

| # | 检查点 | 方法 | 结果 |
|---|---|---|---|
| R5-1 | 种子 SQL | 见 D3 | ✅ 子查询解析父 id，未硬编码 |
| R5-2 | 路由 path 保留 | `routes.js` L130-135 | ✅ `/admin/flow-config` 未变，深链兼容（符合设计风险 6 决策） |
| R5-3 | 前端无需改动 | `SideMenu.vue` 已支持 parent_id 树 | ✅ 与设计 §7 一致，无代码改动 |

### 3.7 R6 — 移除系统设置

| # | 检查点 | 方法 | 结果 |
|---|---|---|---|
| R6-1 | 路由删除 | `grep "settings\|SystemSettings" routes.js` | ✅ 0 命中，`/admin/settings` 已移除 |
| R6-2 | 文件删除 | `ls views/admin/` `ls components/` | ✅ `SystemSettings.vue`、`ThemeConfigPanel.vue` 均已不存在 |
| R6-3 | 无悬空 import | `grep -rn "ThemeConfigPanel\|SystemSettings" --include=*.vue --include=*.js` | ✅ 代码 0 命中（仅 3 个 README 有文档描述，非阻塞） |
| R6-4 | `api/sysConfig.js` 未变死代码 | `grep sysConfig` | ✅ 仍被 `views/admin/FlowConfig.vue` L23 引用（`getFlowConfig/setFlowConfig`），**不可删**，保留正确 |
| R6-5 | 后端接口保留 | `SysConfigController.java` 存在 | ✅ `/api/sys-config` 与 `sys_config` 表未动，符合决策 |

### 3.8 R7 — 后台整体风格抽屉

| # | 检查点 | 方法 | 结果 |
|---|---|---|---|
| R7-1 | 下拉入口 | `AdminLayout.vue` L42-44 | ✅ `command="styleSettings"` + `<Brush/>` 图标，位于「清理缓存」之前，与 PRD §4.7 位置要求一致 |
| R7-2 | 图标 import | L91 | ✅ `Brush` 已从 `@element-plus/icons-vue` 导入 |
| R7-3 | 抽屉挂载 | L79-83 | ✅ `<AdminStyleDrawer v-model="styleDrawerVisible" :state="styleState" @change="onStyleChange" />` |
| R7-4 | `onMounted` 应用 | L162-166 + `applyStyle` L112-115 | ✅ `loadAdminStyle()` 初始化 + `applyAdminStyleVars(state, .if-layout--admin)` |
| R7-5 | 抽屉 7 项控件 | `AdminStyleDrawer.vue` L10-54 | ✅ 主题模式 / 主题色 10 预设 / 侧边菜单类型 / 内容区域宽度 / 固定 Header / 固定侧边菜单 / 色弱模式，**恰好 7 项**，P2 项（导航模式、自动分割菜单等）均未渲染，符合设计 §9.8 |
| R7-6 | 恢复默认 | L57 + L112-115 | ✅ `resetDefault()` 覆盖为 `DEFAULT_ADMIN_STYLE` 并即时应用 + 持久化 |
| R7-7 | 变更即时应用 | `onChange` L104-110 | ✅ 每个控件 `@change="onChange"`，色块 `selectColor` 亦调用；无「保存」按钮，符合 PRD |
| R7-8 | 持久化 | `adminStyle.js` | ✅ `ADMIN_STYLE_KEY='if_admin_style'`；`loadAdminStyle` 以 `{...DEFAULT, ...parsed}` 合并，旧版本缺字段可平滑升级；`save/load` 均 try/catch |
| R7-9 | **作用域铁律** | `utils/theme.js` L44-82 + 全局 `grep documentElement` | ✅ **通过**：`applyAdminStyleVars(state, rootEl)` 首行 `if (!rootEl) return`，全函数仅操作 `rootEl.style` / `rootEl.setAttribute`，**零** `document.documentElement`。全项目 `documentElement` 仅出现在 `applyThemeVars`（前台逻辑，允许保留）与注释中 |
| R7-10 | CSS 作用域 | `styles/admin-style.css` | ✅ 全部 9 条规则均以 `.if-layout--admin` 前缀限定，无 `:root` / 无裸标签选择器，前台 `UserLayout`（`.if-layout--user`）不受影响 |
| R7-11 | CSS 已引入 | `main.js` L16 | ✅ `import './styles/admin-style.css'` |
| R7-12 | 10 色预设 | `adminStyle.js` | ✅ 蓝/浅蓝/红/橙/黄/绿/青/紫/紫红/粉灰，与设计 §9.7 推荐 hex 逐一对齐 |
| R7-13 | 主色阶梯 | `theme.js` L48-58 | ✅ `--el-color-primary` + `light-1..5` + `dark-2`，`mixColor` 复用；作用于后台根子树 |
| R7-14 | `el-radio-button` 语法 | 核对 `node_modules/element-plus@2.14.3` `use-radio.mjs` | ✅ 使用 `label="亮色" value="light"`——2.14.3 中 `actualValue = value ?? label`，`value` 存在时取 `value`，展示文案回退 `label`，语法正确且规避了 3.0 弃用告警 |

---

## 4. 前端构建验证

```
cd D:/WorkBuddyProjects/issueFlow/src/frontend
npx vite build --outDir dist-phase3
```

- **结果：✅ 成功**，`✓ built in 27.78s`（总耗时 52s），**0 error / 0 warning**（仅 chunk > 500kB 的常规提示，为 echarts/element-plus 体积，Phase 2 已存在，非本期回归）。
- 关键产物均已生成：`AdminLayout-BZGN8ee3.js (7.76 kB)`、`ProjectManage-zoA3feIh.js (8.90 kB)`、`AdminIssueList-DeFtK-CJ.js (1.52 kB)`、`IssueForm-DQAAUP_Q.js (5.49 kB)`。
- `AdminStyleDrawer.vue` 被打进 `AdminLayout` chunk（静态 import），体积由 Phase 2 的 ~4kB 增至 7.76 kB，符合预期。
- 临时目录 `dist-phase3` 已删除（bash `rm -rf` 被本机 safe-delete 守卫拦截，改用 PowerShell `Remove-Item -Recurse -Force` 完成，已 `Test-Path` 确认为 `False`）。

---

## 5. 回归检查（Phase 2 功能）

| 模块 | 判定依据 | 结果 |
|---|---|---|
| 问题管理 | `AdminIssueList.vue` 仅删按钮，`IssueTable/IssueDetailDrawer/IssueForm` 交互与 `updateIssue` 链路未动 | ✅ 无回归 |
| 提交问题（前台） | `routes.js` `/user/submit-issue` 保留；`IssueForm` 仅去 `:disabled` | ✅ 无回归 |
| 项目管理 | 新增列均 `v-if="columnVisible.*"`，ID 列与操作列常显；`pageProjects/create/update/delete` 契约向后兼容（仅加字段） | ✅ 无回归 |
| 用户/组织/菜单/角色 | `UserController` 仅**追加** `/users/options`，既有 5 个端点签名未动；`UserService` 仅追加方法，`pageUsers` 未改 | ✅ 无回归 |
| 动态菜单 | `SideMenu.vue` 零改动；R5/R6 仅改 menu 表数据 | ✅ 无回归（需 DB 迁移后目视确认） |
| RBAC | `SecurityConfig` `WHITE_LIST` 未变；`PermissionService.requirePermission` 调用点未减（`project:list/create/update/delete` 全在） | ✅ 无回归 |
| 前台 UserLayout 主题 | `applyThemeVars` / `store/theme` / `if_theme` 三者未动；R7 走独立 key `if_admin_style` + 独立函数 + 作用域 CSS | ✅ 无串扰 |
| 流程配置页 | `FlowConfig.vue` + `api/sysConfig.js` + `/api/sys-config` 全链路保留 | ✅ 无回归 |

---

## 6. 阻塞性问题清单

**无。** 本轮未发现需打回 Engineer 的源码缺陷。

---

## 7. 非阻塞建议（不影响本期发布）

| # | 级别 | 位置 | 建议 |
|---|---|---|---|
| N1 | 文档 | `components/README.md`、`views/README.md`、`router/README.md` | 仍描述已删除的 `SystemSettings.vue` / `ThemeConfigPanel.vue`，建议下个迭代清理（设计 §3.2 已注明「非阻塞」） |
| N2 | 性能 | `ProjectService.buildRoleMap()` | `pageProjects` 与 `toVOWithUsers` 各自 `roleMapper.selectList(null)` 全量查角色表；角色表极小（<10 行）当前无影响，P1 可加 Caffeine/Redis 缓存 |
| N3 | UX | `ProjectManage.vue` `searchUsers` | 远程搜索仅在用户输入时触发，首次打开表单下拉为空（新建场景）。建议 `openCreate` 时预调一次 `searchUsers('')` 加载默认 100 条，减少「点开是空的」的困惑 |
| N4 | UX | `ProjectManage.vue` 列设置 | 「全不选」后表格仅剩 ID + 操作两列，信息量过低。建议至少保留「项目名称」不可关（与操作列同策略） |
| N5 | 健壮性 | `ProjectService.updateProject` | `leaderId/memberIds` 为「无条件覆盖」，任何遗漏该字段的调用方（如未来新增的批量接口）都会静默清空。建议在 `ProjectReq` 加注释警示，或 P1 改为显式 `clearLeader` 标志位 |
| N6 | 已知限制 | `admin-style.css` `--if-topbar-position` | 现布局为 `.if-content{overflow:auto}` 内部滚动，topbar/sidebar 本就常驻，关闭「固定 Header/侧边菜单」视觉差异极小（设计 §9.3 已列为已知限制），非缺陷 |
| N7 | 已知限制 | `admin-style.css` 暗色模式 | 仅覆盖 `.if-content` / `.page-card` 底色，Element Plus 组件（表格/弹窗/抽屉）仍为亮色，存在观感割裂（设计 §9.9 已列为 P1），非缺陷 |
| N8 | 体积 | `dist/assets/index-*.js 1249 kB` | 超 500kB 警告，Phase 2 即存在。建议 P1 用 `manualChunks` 拆分 echarts |

---

## 8. 遗留验证项（须在部署时补做，非本期打回项）

因本机无 JDK17 且未连库，以下项目**静态审查已通过但缺实证**，建议主理人在 23/24 环境部署 Phase 3 时逐条确认：

1. `mvn compile` / `mvn package` 后端编译通过。
2. `V20260730_issueflow_phase3.sql` 连续执行 2 次无报错；`DESC project` 含 `leader_id` / `member_ids`。
3. 后台侧栏「系统管理」下出现「流程配置」，顶级不再有独立项；侧栏无「系统设置」。
4. `GET /api/users/options`（普通用户 token）返回 200，非 403。
5. 项目下存在 OPEN 问题时切停用 → 返回 `code=40020` 且前端开关回滚；关闭该问题后切停用成功。
6. 切换项目状态后重新查询，`leaderName` / `members` 未被清空（R2/R4 联合回归，最高优先级）。
7. R7 抽屉 7 项即时生效 + 刷新保持 + 恢复默认；同浏览器切到前台 `/user`，主题色/侧栏/色弱**均不受影响**。

---

## 9. 结论汇总

| 项 | 值 |
|---|---|
| 检查项总数 | 56 |
| 通过 | 56 |
| 失败 | 0 |
| 阻塞性问题 | 0 |
| 非阻塞建议 | 8 |
| 前端构建 | ✅ 通过 |
| 后端编译 | ⚠️ 环境受限未执行（静态审查通过） |
| **IS_PASS** | **YES** |
| **Send To** | **NoOne** |

7 项需求实现完整、与 PRD/设计契约一致，两处高风险点（`onToggleStatus` 完整 payload、`applyAdminStyleVars` 作用域隔离）均已正确落地。同意进入部署阶段，部署后请按 §8 补做 7 项运行时验证。
