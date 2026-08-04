# issueFlow 增量 PRD（Phase 3）

> 文档版本：v0.1（增量 PRD）
> 角色：产品经理 许清楚
> 关联文档：`docs/prd.md`（基线 PRD）、`docs/prd-phase2.md`（Phase 2 增量）、`docs/incremental-design-phase2.md`
> 技术栈（沿用）：后端 Spring Boot 3.2.5 + MyBatis-Plus + MySQL 8 + Redis 7 + JWT；前端 Vue3 + Element Plus + Pinia + Vue Router + Vite。
> 原则（沿用 Phase 2）：不引入新第三方依赖；复用 `BaseEntity` / `Result` / `PermissionService.requirePermission` 范式；菜单动态渲染沿用 Phase 2 的 `SideMenu` + `menu.type`。

---

## 0. 增量范围与现状对齐（供架构师/工程师速读）

本期在 Phase 2 已上线能力之上交付 7 项增量。下表对齐现状与本期改动面，便于理解「最小改动」边界。

| 维度 | 现状（Phase 2 已上线） | 本期改动 |
|---|---|---|
| 提交问题入口 | 后台 `AdminIssueList.vue` 头部有「提交问题」按钮（跳 `/user/submit-issue`）；前台 `IssueCreate.vue` 同样可用 | **R1** 移除后台问题管理头部按钮，仅保留前台入口 |
| 项目字段 | `project`：name / description / status | **R2** 加 `leader_id`（单选用户）、`member_ids`（多选用户，逗号分隔） |
| 项目列表列 | 固定展示 ID / 名称 / 描述 / 状态 / 创建时间 / 操作 | **R2** 列可见性可设置（localStorage 持久化） |
| 提交问题-项目下拉 | `GET /api/projects/options` 返回全部启用/停用项目；前端 `IssueForm` 已用 `:disabled` 灰显停用项目 | **R3** 后端接口过滤只返启用项目；前端移除灰显逻辑（前置条件已天然满足） |
| 停用项目 | 任意 `el-switch` 切换 | **R4** 后端校验：`status` 由 1→0 时，若该项目下存在 `issue.status != CLOSED` 的问题，禁止停用 |
| 流程配置菜单 | 后台侧栏顶级菜单「流程配置」（`/admin/flow-config`） | **R5** 移到「系统管理」子菜单（`menu.parent_id` 指向系统管理；侧栏随之归属；路由 path 保留 `/admin/flow-config` 不变） |
| 系统设置菜单 | 后台侧栏顶级菜单「系统设置」（`/admin/settings`），下挂 `ThemeConfigPanel` | **R6** 移除侧栏入口；`ThemeConfigPanel` 内的能力整体迁至 R7 抽屉；保留 `sys_config` 表与 `/api/sys-config` 接口不变 |
| 整体风格配置 | 仅「主题色」色块（顶栏 el-color-picker，admin/user 都有），保存到 `sys_config` | **R7** 改为后台顶栏头像下拉「整体风格设置」右侧抽屉（ElDrawer），覆盖：主题模式 / 主题色预设 / 侧边菜单类型 / 内容区域宽度 / 固定 Header / 固定侧边菜单 / 色弱模式；**仅影响 AdminLayout**，前台不变；存 localStorage |

> **既有的 `components/ThemeConfigPanel.vue`** 当前用于「系统设置」页（功能：主题色、布局模式 side/top/mix、菜单 JSON 配置 + 存 sys_config）。本期 R6 将其从系统设置页移除，R7 抽屉仅复用其「主题色」逻辑并新增其余项；其余能力（菜单 JSON 等）随系统设置整体下线。导航模式/混合导航等本期 P2（见 §3、§4.7）。

---

## 1. 产品目标

1. **收口入口、统一提交路径**：把「新建/提交问题」入口从后台剥离到前台，避免管理后台做内容侧操作，降低管理员与提交者的角色混淆。
2. **项目维度更立体**：让项目具备「负责人 + 成员」两个核心维度，便于在项目列表/详情中快速定位责任人与协作范围；同时把列表的展示权力交还给用户（列设置）。
3. **数据一致性守护**：停用项目前必须闭环所有未关闭问题，避免「项目已停用但仍存在 OPEN/IN_PROGRESS/PENDING_VERIFY/VERIFIED 问题」的脏数据。
4. **导航结构清晰化**：将「流程配置」纳入「系统管理」家族，把「系统设置」从侧栏移除，让管理后台侧栏更聚焦。
5. **个人化的界面风格**：在不影响其他用户与前台的前提下，让管理员可在右侧抽屉内一键切换后台主题与若干外观选项（含色弱友好），降低长时间使用的视觉负担。

---

## 2. 用户故事（按 7 项需求）

### R1 — 仅前台端可提交问题
- **作为提交者/开发/测试**，我希望在「我的工作台 → 提交问题」一键提交，避免误从后台页面绕一圈。
- **作为管理员**，我希望后台「问题管理」只承担"审阅与处理"职责，不再出现「新建问题」「提交问题」入口。
- **作为系统**，提交路径唯一收敛到 `/user/submit-issue`，便于后续统计与埋点。

### R2 — 项目负责人 + 项目成员 + 列设置
- **作为管理员**，我希望为项目指定 1 名负责人与若干项目成员，以便在项目列表/详情直接看到"谁负责、谁协作"。
- **作为项目成员**，我希望快速查看自己被加入的所有项目（依赖前端列表筛选，P1）。
- **作为管理员**，我希望自由勾选项目列表显示哪些列（项目名称/描述/状态/负责人/项目成员/创建时间），并让浏览器记住我的偏好。

### R3 — 关联项目仅启用状态
- **作为提交者**，我希望在「提交问题」页面的「关联项目」下拉中看不到已停用项目，避免误关联后再被回退。
- **作为管理员**，我希望停用项目后，问题提交侧不会再带出该项目。

### R4 — 停用项目校验未关闭问题
- **作为管理员**，我希望在把项目状态由启用改为停用时，若该项目下仍有未关闭问题，UI 立即提示并阻止变更，以维持数据闭合。
- **作为测试人员**，我希望"已关闭 (CLOSED)"的旧问题不会阻止项目的停用，让历史问题不绑定项目状态。

### R5 — 流程配置纳入系统管理
- **作为管理员**，我希望流程配置与用户/组织/菜单/角色管理并列在「系统管理」下，便于系统级配置集中入口。
- **作为管理员**，我不希望后台侧栏出现大量顶级菜单项，希望结构更扁平。

### R6 — 去掉系统设置菜单
- **作为管理员**，我希望在后台侧栏不再看到「系统设置」入口；主题相关的设置迁移至更便捷的抽屉。
- **作为系统**，保留 `sys_config` 表与 `/api/sys-config` 接口，供后续扩展（不删除）。

### R7 — 后台头像下拉新增「整体风格设置」抽屉
- **作为管理员**，我希望点击头像下拉的「整体风格设置」后，从右侧滑出抽屉，快速调整后台外观（含主题模式、主题色、侧边菜单类型、内容宽度、固定 Header/侧边菜单、色弱模式等），并让浏览器记住偏好。
- **作为管理员**，我希望此项设置仅作用于管理后台，不影响前台用户与他人。
- **作为色弱用户**，我希望开启色弱模式后主色与状态色更柔和，减小视觉负担。

---

## 3. 需求池（优先级）

> P0 本期必做；P1 重要（本期或紧接）；P2 增强（后续迭代，避免过度设计）。

### P0 — 核心必做
- **R1** 后台 `AdminIssueList.vue` 头部删除「提交问题」按钮，保留列表与详情/编辑能力。
- **R2**
  - `project` 表新增 `leader_id`（BIGINT，用户）、`member_ids`（VARCHAR(500)，逗号分隔用户 id）；配套 `ProjectReq`/`ProjectVO` 同步；`ProjectController` 写接口支持；新增 `GET /api/users/options` 用于负责人/成员下拉（仅登录）。
  - 项目列表页加「显示列设置」弹层（Popover + Checkbox），列至少含：项目名称 / 描述 / 状态 / 负责人 / 项目成员 / 创建时间；列显隐存 `localStorage`（key：`if_project_columns`）。
- **R3** 后端 `ProjectService.listOptions()` 仅返回 `status=1` 的项目；前端 `IssueForm` 移除"停用项目灰显"逻辑（因为已天然过滤）；接口契约保持兼容（`status` 字段保留返回，值恒为 1，便于 P1/P2 扩展）。
- **R4** 后端 `ProjectService.updateProject` 中加停用校验：若 `req.getStatus() == 0` 且存在 `issue.project_id = id AND issue.status != CLOSED`（CLOSED=4），抛 `BizException(PROJECT_HAS_OPEN_ISSUES, "该项目下存在未关闭问题，无法停用")`；前端 `ProjectManage.vue` 切换失败时已有 catch 提示，保持。
- **R5** `menu` 表种子数据调整：「流程配置」(`path=/admin/flow-config`) 的 `parent_id` 改为「系统管理」菜单 id（菜单表自引用，仍为 1 棵端树；SideMenu 自动渲染为系统管理子项）。
- **R6** `menu` 表种子数据删除「系统设置」项；`routes.js` 同步删除 `/admin/settings` 路由（或保留路由、侧栏不再渲染——推荐保留路由兼容深链）；`views/admin/SystemSettings.vue` 文件保留但不再被任何菜单引用。
- **R7** 后台 `AdminLayout.vue` 头像下拉新增「整体风格设置」项（`command="styleSettings"`）；点击后右侧滑出 `<el-drawer title="整体风格设置" direction="rtl" size="360px">`。
  - **本期必做项**：主题模式（亮色/暗色）、主题色（10 色预设）、侧边菜单类型（深色/浅色，仅后台）、内容区域宽度（流式/固定）、固定 Header、固定侧边菜单、色弱模式。**全部存 localStorage**，key：`if_admin_style`。
  - 风格应用范围：仅 `AdminLayout`（通过 `document.documentElement.dataset.ifAdminStyle` 或局部 CSS 变量驱动）。

### P1 — 重要
- **R2** 项目列表加"按负责人/成员筛选"；项目详情展示负责人与成员头像/姓名。
- **R4** 项目详情页新增"未关闭问题数量"角标；列表加该字段列（与列设置联动）。
- **R5** 「流程配置」移位后，原 `/admin/flow-config` 深链 301 到新位置（如 `/admin/system/flow-config`），避免 404；侧栏点击行为不变（由 SideMenu 驱动）。
- **R7**
  - 抽屉底部加"恢复默认"按钮（清空 `if_admin_style` localStorage 并应用默认值）。
  - 提供"复制外观"链接（开发体验，P2 暂缓）。
- 前台 `UserLayout` 顶栏的 `el-color-picker` 与本期 R7 抽屉并存：本期**仅影响后台**；前台主题色入口是否收敛留待 P1（避免本次改动面扩散）。

### P2 — 增强（避免一次性过度设计，明确放弃）
- **R7** 「导航模式（侧边/顶部/混合）」+「自动分割菜单」：当前后台为固定侧边导航（Phase 2 已落地），改顶部/混合需大改布局结构、路由与移动端适配；本期**不做**。
- **R7** 内容区域/顶栏/页脚/菜单/菜单头 5 个开关：当前布局骨架已稳定，开放这些开关会引入大量边界 case；本期**不做**。
- **R2** 项目成员头像气泡展示、按成员聚合项目列表。
- **R4** 停用项目时一次性列出未关闭问题编号供管理员快速定位。
- **R5** 流程配置独立权限码 `flow:config`（当前共用管理员鉴权）。
- **R7** 设置项同步到 `sys_config` 实现服务端持久化（多端同步），本期仅 localStorage。

---

## 4. 页面 / 功能说明（字段、交互、校验）

### 4.1 R1 — 移除后台提交问题入口（纯前端）
- **改动点**：`src/frontend/src/views/admin/AdminIssueList.vue` 头部 `el-button type="primary"`（约 7 行）整段删除；`Plus` 图标与 `goCreate` 函数可一并移除。
- **校验**：后台 `/admin/issues` 头部无任何「新建/提交」按钮；前台 `/user/submit-issue` 不受影响。
- **无需后端改动。**

### 4.2 R2 — 项目负责人 + 项目成员 + 列设置

**后端**
- `entity/Project.java` 新增：
  ```java
  /** 负责人 id（user.id） */
  private Long leaderId;
  /** 项目成员 id 列表（逗号分隔；最大 ~500 字符，~80 人） */
  private String memberIds;
  ```
- 迁移 SQL（写在 `scripts/V202508XX_issueflow_phase3.sql`，与 Phase 2 同库，幂等）：
  ```sql
  -- 加列（动态 ALTER 防重复）
  SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='project' AND COLUMN_NAME='leader_id');
  SET @sql := IF(@c1=0,
    'ALTER TABLE `project` ADD COLUMN `leader_id` BIGINT DEFAULT NULL COMMENT \'负责人 id\' AFTER `status`',
    'SELECT 1');
  PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

  SET @c2 := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='project' AND COLUMN_NAME='member_ids');
  SET @sql := IF(@c2=0,
    'ALTER TABLE `project` ADD COLUMN `member_ids` VARCHAR(500) DEFAULT NULL COMMENT \'项目成员 id，逗号分隔\' AFTER `leader_id`',
    'SELECT 1');
  PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
  ```
- `dto/req/ProjectReq.java` 新增 `Long leaderId`、`String memberIds`（非必填）。
- `dto/resp/ProjectVO.java` 新增 `Long leaderId`、`String leaderName`（来自 UserService 回查）、`String memberIds`、`List<UserBriefVO> members`（id + realName）。
- `service/ProjectService.java`
  - `createProject`/`updateProject`：写入 `leaderId` 与 `memberIds`（不做"用户是否存在"校验——后台信任管理员输入；如失败在编辑时再暴露）。
  - 新增 `GET /api/users/options`（**仅登录**，挂在 `UserController`，避免新建 controller）：
    - 入参：`keyword`（可选，模糊匹配 `real_name` / `username`）；出参：`List<UserBriefVO>{id, realName, username, roleName}`。
    - 仅查 `status=1` 且 `deleted=0` 的用户；上限 100 条（前端 `el-select` 远程搜索）。
  - `toVO` 阶段：批量查 `userNameMap()` 补 `leaderName`；`memberIds` 按逗号切分后批量查 user，封装为 `members: List<UserBriefVO>`。
- `controller/ProjectController.java`
  - 已有 `/api/projects`、`/api/projects/{id}`、`/api/projects/options` 不变。
  - `/api/projects/options` 在 R3 后只返回启用项目；接口契约字段不变（保留 `status`，恒为 1）。
- `common/ResultCode.java` 新增（按需）：`PROJECT_HAS_OPEN_ISSUES`（R4 用）。

**前端**
- `views/admin/ProjectManage.vue`
  - 列表新增列：
    - 「负责人」`min-width=110`，`{{ row.leaderName || '-' }}`。
    - 「项目成员」`min-width=180`，`show-overflow-tooltip`，展示首 3 名 + "...等 N 人"（用 `row.members` 截断）。
  - 表单（Dialog）新增：
    - `el-form-item label="负责人"`：`el-select v-model="form.leaderId" filterable remote :remote-method="searchUsers" :loading="userLoading" placeholder="搜索并选择负责人" clearable`，选项来自 `/api/users/options?keyword=`。
    - `el-form-item label="项目成员"`：`el-select v-model="form.memberIdsArray" multiple filterable remote :remote-method="searchUsers" :loading="userLoading" placeholder="搜索并选择成员" clearable`，提交前 `form.memberIds = form.memberIdsArray.join(',')`。
  - 列表头部右侧加「列设置」按钮（`el-button text :icon="Setting"`）；点击弹 `<el-popover>`：
    - 复选：`项目名称 / 描述 / 状态 / 负责人 / 项目成员 / 创建时间 / 操作`（操作列恒显不可关）。
    - 「全选 / 全不选 / 重置默认」快捷按钮。
    - 显隐状态存 `localStorage['if_project_columns']`（JSON 数组：`['name','description','status','leader','members','createdAt']`）；读取失败回退默认全显。
  - 表单提交：`form` 增加 `leaderId`、`memberIds`（字符串）；保存接口已透传。

**接口契约（增量）**
| Method | URL | 入参 | 出参 | 权限 |
|---|---|---|---|---|
| GET | `/api/users/options` | `keyword`(可选) | `List<UserBriefVO{id, realName, username, roleName}>` | 登录 |
| GET | `/api/projects` | `page,size` | `PageResult<ProjectVO>` | `PERM:project:list` |
| POST | `/api/projects` | `ProjectReq` | `ProjectVO` | `PERM:project:create` |
| PUT | `/api/projects/{id}` | `ProjectReq` | `ProjectVO` | `PERM:project:update` |
| DELETE | `/api/projects/{id}` | path | `void` | `PERM:project:delete` |
| GET | `/api/projects/options` | — | `List<ProjectOptionVO>`（**R3 后仅 status=1**） | 登录 |

### 4.3 R3 — 关联项目仅启用状态
- **后端**：`ProjectService.listOptions()` 中追加 `.eq(Project::getStatus, 1)`；现有 `ProjectOptionVO.status` 字段保留（恒 1）。
- **前端**：`components/IssueForm.vue`「关联项目」下拉可去掉 `:disabled="p.status !== 1"` 与"（停用）"后缀文案（因已天然过滤）；`status` 字段保留用于 P1 扩展。
- **校验**：停用项目后，再次进入「提交问题」页下拉不再含该项目；列表 `/api/projects/options` 长度与启用项目数一致。
- **无需新表 / 新权限。**

### 4.4 R4 — 停用项目校验未关闭问题
- **后端** `ProjectService.updateProject`：
  ```java
  if (req.getStatus() != null && req.getStatus() == 0 && exist.getStatus() == 1) {
      long openCount = issueMapper.selectCount(new LambdaQueryWrapper<Issue>()
          .eq(Issue::getProjectId, id)
          .ne(Issue::getStatus, IssueStatusEnum.CLOSED.getCode())  // CLOSED=4
          .eq(Issue::getDeleted, 0));
      if (openCount > 0) {
          throw new BizException(ResultCode.PROJECT_HAS_OPEN_ISSUES,
              "该项目下存在未关闭问题，无法停用");
      }
  }
  exist.setStatus(req.getStatus());
  ```
- 需注入 `IssueMapper`；统计走 MP `selectCount`（性能可接受；P1 可走缓存）。
- 仅校验 **启用 → 停用** 的方向（启用方向、放行"已停用 → 启用"）。
- **前端**：`ProjectManage.vue` `onToggleStatus` 已 try/catch；失败时把 `row.status` 回滚到原值，`ElMessage.error` 由响应拦截器统一提示"该项目下存在未关闭问题，无法停用"。
- **新增业务码**：`ResultCode.PROJECT_HAS_OPEN_ISSUES`。

### 4.5 R5 — 流程配置纳入系统管理
- **改动点（仅种子数据 + 路由小调整）**：
  - `menu` 表种子数据：`UPDATE menu SET parent_id = <系统管理 id> WHERE path = '/admin/flow-config'`（菜单表 `parent_id` 自引用；侧栏由 SideMenu 动态渲染）。
  - `routes.js`：**保留** `/admin/flow-config` 路由 path 不变（深链兼容）；若希望菜单点击激活态更准确，可在 `path` 同步改为 `/admin/system/flow-config` 并在 `routes.js` 增加新路径（推荐保留旧路径 + 302 跳新路径，留待 P1）。
  - 实际导航靠 SideMenu 的菜单项 `path` 跳转；菜单项的 `path` 字段值与路由表一致即可。

- **校验**：后台侧栏点击「系统管理 → 流程配置」可正常打开 `/admin/flow-config`（或对应新路径）；旧的 `/admin/flow-config` 仍可访问（深链）；旧顶级「流程配置」菜单不再出现。

### 4.6 R6 — 去掉系统设置菜单
- **改动点**：
  - `menu` 表种子数据：`DELETE FROM menu WHERE path = '/admin/settings'`（逻辑删：`deleted=1`）。
  - `routes.js`：删除 `/admin/settings` 路由条目（或保留但无菜单可达——推荐删除，避免无主路由）。
  - `views/admin/SystemSettings.vue`：文件保留，导出无引用，**不在菜单渲染**；Phase 2 中 `ThemeConfigPanel.vue` 内容整体迁至 R7 抽屉后，该文件可删除（推荐作为 P1 收尾）。
  - `common/ResultCode`、`/api/sys-config` 接口与 `sys_config` 表保留，不删。

### 4.7 R7 — 后台头像下拉新增「整体风格设置」抽屉

**入口与结构**
- 后台 `AdminLayout.vue` 头像 `el-dropdown-menu` 新增：
  ```html
  <el-dropdown-item command="styleSettings">
    <el-icon><Brush /></el-icon><span class="dd-text">整体风格设置</span>
  </el-dropdown-item>
  ```
  位置：紧邻「清理缓存」之前。
- 新组件 `components/AdminStyleDrawer.vue`（ElDrawer，`direction="rtl"`，`size="360px"`，`title="整体风格设置"`），由 `AdminLayout` 引用，受 `styleDrawerVisible` ref 控制。
- 抽屉内字段（自上而下，分组）：
  | 分组 | 字段 | 控件 | 默认 | 说明 |
  |---|---|---|---|---|
  | — | 主题模式 | `el-radio-group` `亮色 / 暗色` | 亮色 | 二选一（仅后台；前台维持浅色） |
  | — | 主题色 | 10 色预设圆点（与原色板对齐 Element Plus 主色），点击选中 | `#409EFF` | 不再保留"任意取色"，收敛为预设 |
  | — | 侧边菜单类型 | `el-radio-group` `深色菜单 / 浅色菜单`（图标预览） | 深色菜单 | 仅后台；前台维持浅色 |
  | — | 内容区域宽度 | `el-select` `流式 / 固定 1200px` | 流式 | 流式即 `--if-content-max: none`；固定即 `1200px` |
  | — | 固定 Header | `el-switch` | 开 | 顶部 header 随滚动消失/常驻 |
  | — | 固定侧边菜单 | `el-switch` | 开 | 侧栏随滚动消失/常驻 |
  | — | 色弱模式 | `el-switch` | 关 | 启用后主色降饱和 + 状态色微调 |
  | 底部 | 恢复默认 | `el-button text` | — | 清 localStorage 重置 |
- **作用域**：抽屉中**任何字段变更**即应用（无需"保存"按钮）；应用方式：写 `document.documentElement.dataset.ifAdminStyle = JSON.stringify(state)`，由 CSS 选择器 `[data-if-admin-style*=...]` 或局部 CSS 变量驱动 `AdminLayout`。前台 `UserLayout` 不读取该 dataset。
- **持久化**：每次变更同步写 `localStorage['if_admin_style']`（JSON）。`AdminLayout` `onMounted` 读取并应用；解析失败回退默认。
- **本期不做**（P2 明确放弃）：导航模式（侧边/顶部/混合）、自动分割菜单、内容区域/顶栏/页脚/菜单/菜单头 5 个开关——侧栏以截图文字呈现但抽屉内不暴露，避免误改布局。
- **复用既有能力**：现 `components/ThemeConfigPanel.vue` 的「主题色 + 主题色阶梯」生成逻辑（`utils/theme.js` 中 `applyThemeVars`）整体抽出为新函数 `applyAdminStyleVars(state)`，由 `AdminStyleDrawer.vue` 调用；不再写入 `sys_config`。
- **数据模型（仅前端）**
  ```ts
  interface AdminStyle {
    themeMode: 'light' | 'dark'                 // 主题模式
    themeColor: string                          // 主题色 hex（10 预设）
    sidebarType: 'dark' | 'light'               // 侧边菜单类型
    contentWidth: 'fluid' | 'fixed'             // 内容区域宽度
    fixedHeader: boolean                        // 固定 Header
    fixedSidebar: boolean                       // 固定侧边菜单
    colorWeak: boolean                          // 色弱模式
  }
  const DEFAULT_ADMIN_STYLE: AdminStyle = {
    themeMode: 'light', themeColor: '#409EFF', sidebarType: 'dark',
    contentWidth: 'fluid', fixedHeader: true, fixedSidebar: true, colorWeak: false
  }
  ```
- **CSS 变量约定（落在 `styles/variables.css` 或 `styles/admin.css`）**
  ```css
  /* 写入 :root 或 [data-if-admin-style] 选择器 */
  --admin-sidebar-bg: #1f2d3d;        /* sidebarType=dark */
  --admin-sidebar-bg: #ffffff;        /* sidebarType=light */
  --admin-sidebar-text: #c0c4cc;
  --admin-content-max: none;          /* contentWidth=fluid */
  --admin-content-max: 1200px;        /* contentWidth=fixed */
  --if-topbar-position: sticky;       /* fixedHeader=true */
  --if-sidebar-position: sticky;      /* fixedSidebar=true */
  --if-color-weak-filter: none;       /* colorWeak=false */
  --if-color-weak-filter: saturate(0.7); /* colorWeak=true */
  ```

- **既有 `ThemeConfigPanel.vue` 处理**：保留"主题色"逻辑被 `AdminStyleDrawer` 内部复用；移除"布局模式 / 菜单 JSON"逻辑；`SystemSettings.vue` 引用拆除后该组件文件删除（P1 收尾）。

---

## 5. 权限与角色设计要点（RBAC 沿用 Phase 2）

| 变更 | 权限码 | 角色范围 | 备注 |
|---|---|---|---|
| R2 项目负责人/成员维护 | `project:create` / `project:update`（沿用） | ADMIN | 编辑字段属"更新项目"动作，不新增权限码 |
| R2 `/api/users/options` | 无（仅登录） | 任意登录用户 | 与 `/api/projects/options` 等仅登录下拉保持一致 |
| R3 `/api/projects/options` 过滤 | 无 | 任意登录用户 | 收紧可见性而非收紧权限 |
| R4 停用校验 | 沿用 `project:update` | ADMIN | 业务校验在 service 层 |
| R5/R6 菜单迁移/移除 | 沿用 `menu:update` | ADMIN | 调整种子数据 + 路由 |

> 无需新增权限码；无菜单可见性切换影响；既有 `Role.Permission` 种子与 `ADMIN` 始终放行规则不变。

---

## 6. 数据需求（新字段 / 新表 / 测试数据）

### 6.1 新字段（写在 `scripts/V202508XX_issueflow_phase3.sql`，幂等）
```sql
-- project 加 leader_id / member_ids（动态 ALTER 防重复）
-- 见 §4.2 SQL 片段

-- menu 调整（UPDATE 种子，非结构变更）
-- 5. 流程配置 parent_id 改为「系统管理」id
UPDATE menu SET parent_id = (SELECT id FROM (SELECT id FROM menu WHERE path = '/admin/system') t)
WHERE path = '/admin/flow-config';
-- 6. 系统设置逻辑删除
UPDATE menu SET deleted = 1 WHERE path = '/admin/settings';
```

### 6.2 新增 ResultCode
```java
PROJECT_HAS_OPEN_ISSUES(40020, "该项目下存在未关闭问题，无法停用")
```

### 6.3 新增 / 修改 DTO
| 类 | 新增字段 |
|---|---|
| `ProjectReq` | `Long leaderId; String memberIds` |
| `ProjectVO` | `Long leaderId; String leaderName; String memberIds; List<UserBriefVO> members` |
| `UserBriefVO` (新建) | `Long id; String realName; String username; String roleName` |
| `ResultCode` | `PROJECT_HAS_OPEN_ISSUES(40020, ...)` |

### 6.4 测试数据（建议在 Phase 3 种子追加）
- 项目表：把"默认项目"补 1 名负责人 + 2 名成员；新增"前端重构"项目并预置负责人/成员。
- `menu` 表：依 §6.1 调整。
- 验证用例（手工或自动化）：
  1. R1：管理员访问 `/admin/issues` → 头部无「提交问题」按钮；`/user/submit-issue` 仍可。
  2. R2：新建项目 → 选负责人 + 2 名成员 → 列设置勾掉"项目成员" → 刷新页面 → 列仍隐藏。
  3. R3：停用 1 个项目 → 提交问题 → 下拉不包含该项目。
  4. R4：项目下有 1 条 OPEN 问题 → 切停用 → 报错"该项目下存在未关闭问题，无法停用"；关闭该问题后再切停用 → 成功。
  5. R5：后台侧栏"系统管理"下出现"流程配置"；顶级不再有独立项。
  6. R6：后台侧栏无"系统设置"。
  7. R7：抽屉内 7 项均能即时生效；刷新页面后保持；点"恢复默认"回到默认；前台 `UserLayout` 不受任何项影响。

---

## 7. 权限与依赖检查清单

- 沿用 Phase 2 的 `PermissionService.requirePermission(...)` 鉴权助手，本期无新权限码。
- `menu.type` 沿用：流程配置 / 系统设置均为 `type=2`（后台端），无需变更。
- `SideMenu.vue` 已支持 parent_id 自引用树形渲染，R5 仅需改种子数据，无需前端代码改动。
- `UserController` 增加 `GET /users/options`；该接口 `permissionService` 不调用（仅登录）。
- R7 抽屉新增 `AdminStyleDrawer.vue`；前端无新依赖。

---

## 8. 待确认问题（Open Questions）

1. **R2 负责人/成员下拉数据源**：本期推荐复用既有用户表，新增 `GET /api/users/options`（仅登录）。是否需要支持「按组织（organization）过滤」？——本期不做默认全量 100 条上限；P1 视反馈增加组织过滤。
2. **R4 是否需要「停用前一次性列出未关闭问题编号」**：本期只阻止 + 提示文案；P2 再扩展"展开问题列表"。请确认范围。
3. **R5 路由 path 是否同步迁移**：`/admin/flow-config` 保留（旧深链兼容）vs 改为 `/admin/system/flow-config`（与 SideMenu 父级路径一致）。本期推荐保留旧 path，菜单 `path` 字段同步写 `/admin/flow-config` 即可；P1 再优化。
4. **R6 是否同步删除 `/admin/settings` 路由与 `SystemSettings.vue` 文件**：本期推荐"删除路由 + 保留文件被 P1 清理"；若希望保留文件作为占位文档，请告知。
5. **R7 应用范围**：本期仅影响 `AdminLayout`，前台 `UserLayout` 顶栏 `el-color-picker` 维持现状（仅改前台主题色）。是否希望 P1 一并收敛？——建议本期不动，避免双端耦合。
6. **R7 主题色预设色板**：截图中有 10 个颜色（蓝/浅蓝/红/橙/黄/绿/青/紫/紫红/粉灰），是否完全沿用截图？还是使用 Element Plus 默认 10 色？——本期推荐沿用截图预设。
7. **R7 抽屉字段 P2 项（导航模式/混合/内容区域/顶栏/页脚/菜单/菜单头 5 开关）确认不在本期**：避免一次性过度设计。若主理人希望其中某项提前，请指明优先级。
8. **R2 成员数上限与字段类型**：成员用 `VARCHAR(500)` 存 id 列表，约可容纳 ~80 人；如需更大规模或更复杂查询（如"按成员筛选项目"），P1 改为独立 `project_member(project_id, user_id)` 表。本期推荐保持字符串存储以减小改动面。

---

## 9. 给架构师 / 工程师重点关注的技术难点

1. **R4 停用校验的并发一致性**：与 Phase 2 的关联防环类似，存在"两个管理员同时点停用"的窄竞态。推荐在 `ProjectService.updateProject` 上加 `@Transactional`，校验通过后 `selectCount` + `updateById` 在同一事务；高并发场景由 MyBatis-Plus `optimistic-locker`（`@Version`）或唯一索引兜底。建议本期与既有"项目名唯一"行为保持一致即可。
2. **R7 抽屉作用域仅 AdminLayout**：`applyThemeVars` 当前写 `document.documentElement`，会污染前台；新写 `applyAdminStyleVars` 必须改为「仅作用于 `AdminLayout` 根元素」或在根元素加 `data-if-admin-style` 属性以 CSS 选择器限定（推荐后者）。`topbar` 与 `sidebar` 的 `position: sticky` 需要 `position: relative` 的祖先容器配合，注意与现有 `if-main` flex 布局兼容。
3. **R2 成员字段的批量查询与回显**：`ProjectVO.members` 列表由 `memberIds` 字符串切分后批量查 user；项目分页接口需在 `toVO` 阶段对全页记录批量补齐，避免 N+1。建议在 `ProjectService.pageProjects` 中把当页所有 `leaderId` 与 `memberIds` 汇总后**一次** `userMapper.selectBatchIds(...)`，再回填到 VO。
4. **R5 菜单种子 UPDATE 的幂等性**：种子 SQL 用子查询解析「系统管理」菜单 id（`SELECT id FROM menu WHERE path = '/admin/system'`），避免硬编码 id；重跑时需先 `deleted=0`。可参照 Phase 2 菜单种子的写法（`incremental-design-phase2.md` §2.5）。
5. **R7 `data-if-admin-style` 与 Pinia 持久化**：抽屉变更频繁但仅写 localStorage，无需进 Pinia（避免 store 膨胀）；直接由 `AdminStyleDrawer.vue` 持有状态、`AdminLayout.vue` `onMounted` 读取一次即可。
6. **既有 `ThemeConfigPanel.vue`/`SystemSettings.vue` 收尾**：本期建议保留 `ThemeConfigPanel.vue` 中的"主题色阶梯生成"工具方法（提取到 `utils/theme.js` 为 `applyAdminStyleVars`），删除原组件文件、`SystemSettings.vue` 文件、`/api/sys-config` 中仅供主题用的 `theme_color`/`layout`/`menu_config` 三个 key（保留 `flow_*` 等其他配置）；具体取舍请架构师评估。
7. **R3 与现有 `IssueForm` 的契合**：前端 `IssueForm` 已有 `:disabled` 灰显逻辑，本期后端收紧后建议**移除**前端灰显逻辑（避免重复且降复杂度）；但要确保搜索/筛选时不会因前端 disabled 导致某些用户看不到已停用项目（已停用项目被后端过滤，无此问题）。
8. **R7 截图中的 P2 项必须本期不暴露控件**：抽屉模板中**不要**渲染 "导航模式 / 自动分割菜单 / 内容区域 / 顶栏 / 页脚 / 菜单 / 菜单头" 等控件，避免后续被错误使用；待 P1/P2 再开启。

---

> 配套说明：本 PRD 聚焦产品层面（目标/故事/需求池/页面功能/权限要点/数据需求/待确认），具体任务分解与类图由架构师在 `incremental-design-phase3.md` 范式下产出。