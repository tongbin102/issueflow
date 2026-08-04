# issueFlow 增量 PRD（Phase 5）

> 文档版本：v1.0（增量 PRD · 简单档）
> 角色：产品经理 许清楚
> 关联文档：`docs/prd-phase4.md`、`docs/incremental-design-phase4.md`、`docs/CHANGELOG.md`
> 技术栈（沿用）：后端 Spring Boot 3.2.5 + MyBatis-Plus 3.5.7 + MySQL 8 + Redis + JWT；前端 Vue3 + Element Plus 2.7 + Pinia + Vue Router 4 + Axios + Vite。
> 原则（沿用）：**不引入新第三方依赖**；复用 `BaseEntity` / `Result` / `PageResult` / `BizException` 范式；菜单由 `menu` 表驱动（`SideMenu.vue` 递归渲染）；迁移 SQL 幂等，命名 `V2026xxxx_issueflow_phase5.sql`。

---

## 0. 代码勘察结论（本 PRD 的事实基础）

以下均为**实际读码确证**，架构师可直接引用：

| 勘察点 | 现状结论（文件 / 行为） | 对本期影响 |
|---|---|---|
| 侧栏「返回前台」 | **已存在**。`AdminLayout.vue:17` 渲染 `<LayoutSwitchEntry variant="sidebar">`，靠 `.if-switch-entry--sidebar { margin-top:auto }` 推到底部 | R1 非新增，是**定位加固** |
| 侧栏容器样式 | `theme.css:28` `.if-sidebar{ overflow:hidden }`（**菜单超长会被裁掉**）；`admin-style.css:37` `.if-layout--admin .if-sidebar{ position: var(--if-sidebar-position, sticky); align-self:flex-start }`，该变量可被 R7 风格设置改为 `static` | R1 需处理：菜单区可滚动 + 入口常驻；且要兼容 sticky/static 两种风格、折叠态（220↔64px）、移动端（`@media max-width:768px` 侧栏本身已是 `position:fixed` 抽屉） |
| 流程节点 / 流转表 | **完全不存在**。后端无 `flow_node` / `flow_transition` 类表；流程规则**硬编码**在 `handler/StateMachine.java` 的 `private static final List<Transition> TRANSITIONS`（6 条），状态取 `enums/IssueStatusEnum`（OPEN0/IN_PROGRESS1/PENDING_VERIFY2/VERIFIED3/CLOSED4），仅有 `sys_config` 两个开关 `flow_reject_enabled` / `flow_reopen_enabled` | R2 是本期**最大改动面**：需新建流程节点/流转两张表 + 让 `StateMachine` 由硬编码转为读库 |
| 流程配置页现状 | `views/admin/FlowConfig.vue` 全文仅 63 行，只有 2 个 `el-switch` + 1 个 `el-alert`，走 `api/sysConfig.js` 的 `getFlowConfig/setFlowConfig` | R2 该页需**整体重写**为可视化流程图 + 节点/流转 CRUD |
| 流程图可视化能力 | `package.json` 依赖：vue、vue-router、pinia、element-plus、@element-plus/icons-vue、axios、**echarts 5.5.0**、file-saver。**无任何流程图库**（无 X6/LogicFlow/vue-flow） | R2 可视化在「echarts graph（零新依赖）」与「自绘 SVG」间二选一，见 §5 待确认 Q2 |
| 全站弹窗分布 | `<el-dialog>` 共 **13 处 / 10 个文件**：`ModuleTreeDrawer.vue`×3、`RoleManage.vue`×2、`StatusFlowButtons.vue`、`AdminLayout.vue`（个人设置）、`AdminIssueList.vue`、`MenuManage.vue`、`OrganizationManage.vue`、`ProjectManage.vue`、`UserManage.vue`、`UserIssueList.vue` 各 1 | R3 的量化基线 |
| 已有抽屉范例 | `<el-drawer>` 共 3 处：`AdminStyleDrawer.vue`、`IssueDetailDrawer.vue`、`ModuleTreeDrawer.vue`（`size="620px"`、`append-to-body`、`v-model:visible` + `@closed` 清理） | R3 统一规范直接以 `ModuleTreeDrawer.vue` 为形态基准 |
| `ElMessageBox.confirm` | 10 个文件在用（删除/退出登录等确认），**非 Dialog 组件**，是命令式 API | R3 范围界定的关键点，见 §5 待确认 Q1 |
| organization 表 | `V20250730_issueflow_p0.sql:25` 仅 `id / name / parent_id / sort / created_at / updated_at / deleted`；`entity/Organization.java`、`OrganizationVO`、`OrganizationReq` 三处字段完全一致 | R4 需加 `code` / `leader_id` / `status`（+ `description` 建议） |
| 组织管理页现状 | `OrganizationManage.vue` 为「左 `el-tree`（10 栏）+ 右选中详情（14 栏）」布局；顶部按钮实际文案是**「新建根组织」「新建子组织」**（非需求原文的「新增根组织」「新建组织」）；编辑走 `el-dialog width=460px` | R4 整页重构；按钮文案差异见 §5 待确认 Q5 |
| 组织接口 | `OrganizationController`：`GET/POST /api/organizations`、`PUT/DELETE /api/organizations/{id}`；`GET` 返回**扁平 List**，树由前端 `buildTree()` 拼 | R4 筛选需后端支持 or 前端过滤，见需求池 R4-3 |
| user 表 / 接口 | `entity/User.java` 无 `leaderId`；`UserReq`/`UserVO` 同样无。**但 `GET /api/users/options`（`listUserOptions(params)`，支持 `keyword` 模糊匹配 realName/username）已存在**，`ProjectManage.vue` 的「负责人」远程搜索下拉即用它 | R5 前端选择器可**零成本复用**该接口，只需加字段与回显 |
| 菜单机制 | `menu` 表（`parent_id` 自引用 + `sort` + `type` 1前台/2后台 + `permission`），`SideMenu.vue` 用 `node.path` 作 `el-menu` 的 `index`。Phase4 已确立范式：`/admin/flow`「流程管理」是**纯菜单分组**，`routes.js` 中并**无**该路由，子项 `/admin/flow-config`、`/admin/flow-monitor` 才是真实路由 | R6 可完全照搬：新增 `/admin/project` 分组（无路由），`项目管理→项目配置` 仅改 `name`，`模块配置` 需新路由 + 新页面 |
| 现有后台顶级菜单 | 概览(1) → 问题管理(2) → 项目管理(3,`/admin/projects`,`project:list`) → 流程管理(4,`/admin/flow`) → 系统管理(5)。`系统管理` 下含 用户/组织/菜单/角色 | R6 需重排 sort |
| 权限码 | Phase2 种子 32 条，含 `organization:list/create/update/delete`、`user:*`、`project:*`、`flow:view`、`flow:config`、`settings:update`。Phase4 明确「模块写操作复用 `project:update`，permission 表零变更」 | R2/R4/R5 无需新权限码；R7 需新增 1 条（见 R7-1） |
| 全库业务表 | 共 15 张：`role`/`user`/`issue`/`issue_attachment`/`issue_history`/`tag`/`sys_config`（schema.sql）、`project`/`organization`/`menu`（p0）、`issue_relation`/`permission`/`role_permission`（phase2）、`module`/`module_dependency`（phase4） | R7 清库范围的完整清单 |
| admin 账号来源 | `IssueFlowApplication.initAdminUser` ApplicationRunner：**启动时**若无 admin 则用 BCrypt 建（admin/admin123）。角色字典来自 `db/data.sql` 的 4 条 `INSERT IGNORE` | R7 若清空 user 表，admin 不会立即重建（只在下次启动重建）→ 必须在清库逻辑内显式保留/重建 |
| 附件物理文件 | `application.yml:57` `attachment-base-path: ${ATTACHMENT_BASE_PATH:/data/attachments}` | R7 需决定是否一并清理磁盘文件，见 §5 待确认 Q3 |
| 鉴权方式 | `SecurityConfig` 只做 `WHITE_LIST.permitAll() + anyRequest().authenticated()`，**无 `@PreAuthorize`**；细粒度靠 `PermissionService`（`SecurityUtils.getCurrentRoleCode()`）在 service 层判定 | R2/R7 的权限校验沿用 service 层范式 |

---

## 1. 产品目标

Phase 5 聚焦**管理后台的「一致性」与「可配置性」**：一是把后台的交互语言统一下来——所有编辑态收敛为右侧滑出面板、导航入口固定可见、树形数据统一用「树形表格 + 标准工具栏」呈现，让管理员在任何页面都能用同一套肌肉记忆操作；二是把此前硬编码在 Java 里的问题审核流程「搬到界面上」，让有权限的用户能看懂流程全貌并自行增删改流转规则；三是补齐组织/用户的组织架构属性（组织编码、负责人、状态、上级领导），并提供一键数据初始化能力，让系统具备**可反复演示、可干净交付**的基础运维能力。

---

## 2. 用户故事（按角色 × 7 项需求）

### ADMIN（系统管理员）
- **R1** 作为管理员，我希望不论侧边菜单有多长、页面滚到哪里，「返回前台」按钮都固定在左侧导航栏底部可见，这样我随时能一键切回前台核对效果。
- **R2** 作为管理员，我希望打开「流程配置」就能看到一张问题审核工作流全景图（有哪些审核节点、箭头往哪走、每个状态什么颜色），不用去翻 `StateMachine.java` 才知道流程长什么样。
- **R2** 作为管理员，我希望能新增/编辑/删除流程节点和流转规则（比如加一个「产品验收」节点、把「待验证→处理中」的可操作角色改掉），配置完立即对新的状态流转生效。
- **R3** 作为管理员，我希望所有新建/编辑操作都从屏幕右侧滑出，列表数据始终留在视野里作为上下文参照，不再被居中弹窗遮挡。
- **R4** 作为管理员，我希望组织管理用树形表格展示，一眼看到层级 + 编码 + 负责人 + 状态 + 排序，并能一键展开/收缩全部、按名称与状态筛选、自定义列与显示密度。
- **R4** 作为管理员，我希望新增组织时通过「上级组织」字段决定挂在哪里（留空即为根节点），而不是靠「先选中树节点再点新建子组织」这种隐式操作。
- **R5** 作为管理员，我希望在用户表单里为每个用户指定「上级领导」，为后续按汇报线做问题分派和统计打基础。
- **R6** 作为管理员，我希望「项目管理」成为一个顶级菜单分组，下面清晰地分「项目配置」和「模块配置」，找配置项时不用再回忆模块藏在项目列表的哪个行内按钮里。
- **R7** 作为管理员，我希望有一个「数据初始化」入口，一键清空演示/测试产生的全部业务数据，让系统回到干净的初始状态，方便交付和重新演示。

### 部门负责人（组织负责人 / 项目负责人）
- **R4** 作为部门负责人，我希望在组织树形表格里能看到每个组织的负责人和启用状态，快速确认组织架构是否与实际一致。
- **R5** 作为部门负责人，我希望能看到我的下属列表（通过「上级领导」字段建立），了解自己团队的成员构成。
- **R2** 作为部门负责人，我希望能查看流程图了解一个缺陷从提交到关闭要经过哪些审核环节、每一步由谁负责（**只读**，配置权限仍归管理员）。

### 开发人员（含测试/提交者）
- **R2** 作为开发人员，我希望在流程图上直观看到「我现在这个状态能往哪走、需要什么角色操作」，减少「为什么这个按钮没出现」的困惑。
- **R3** 作为开发人员，我希望后台各页面的交互形式一致（都是右侧抽屉），降低学习成本。
- **R6** 作为开发人员，我希望能通过「模块配置」独立页面维护模块，而不必先进项目列表再点行内按钮。

---

## 3. 需求池

> 优先级定义：**P0 = 本阶段必须交付**；**P1 = 建议本阶段交付**（不阻塞主链路）；**P2 = 可后续迭代**。
> 「关联」列给出**已确证存在**的文件/接口，架构师可直接定位。

### P0（必须交付）

| 编号 | 需求 | 验收标准 | 关联现有页面 / 接口 |
|---|---|---|---|
| **R1-1** | 后台侧栏「返回前台」按钮固定常驻 | 1) 后台任意页面、内容区滚动到任意位置，按钮始终可见于左侧导航栏底部；2) 菜单项数量超过视口高度时，**菜单区自身出现滚动条**，按钮不被裁切、不随菜单滚动；3) 侧栏折叠态（64px）降级为纯图标且仍固定在底部；4) 移动端（≤768px）抽屉打开时按钮位于抽屉底部，不遮挡菜单；5) R7「整体风格设置」把 `--if-sidebar-position` 切成 `static` 时表现不退化 | `layouts/AdminLayout.vue:17`、`components/LayoutSwitchEntry.vue`、`styles/theme.css:28`（需去掉/改造 `.if-sidebar{overflow:hidden}`）、`styles/admin-style.css:37` |
| **R2-1** | 流程节点 / 流转规则数据模型落库 | 1) 新增 `flow_node`（节点）与 `flow_transition`（流转）两张表，含逻辑删除与 `created_at/updated_at`，DDL 幂等；2) 迁移脚本把现有 5 个状态（`IssueStatusEnum`）与 6 条硬编码流转（`StateMachine.TRANSITIONS`）**原样种子化**，保证升级后行为零变化；3) 现有 `flow_reject_enabled` / `flow_reopen_enabled` 两个开关语义在新模型中被等价承接（映射为对应流转的 enabled 标志） | 新表；对齐 `enums/IssueStatusEnum.java`、`handler/StateMachine.java`、`V20250801_issueflow_phase2.sql:192-198` |
| **R2-2** | `StateMachine` 改为数据驱动 | 1) `isAllowed(from,to,roleCode)` / `getAction(from,to)` 改为读 `flow_transition`（可加 Redis/本地缓存，配置变更后失效）；2) 既有 6 条流转的行为与改造前**逐条一致**（回归用例覆盖 6 条正向 + 至少 3 条非法流转）；3) 配置修改后无需重启即生效 | `handler/StateMachine.java`、`service/IssueFlowService.java`、`controller/IssueFlowController.java` |
| **R2-3** | 流程配置页可视化流程图 | 1) 页面以流程图形式展示全部节点与流转箭头；2) 节点显示名称 + 状态色标（复用 `utils/format.js` 的 `statusColor`）；3) 箭头标注动作名与允许角色；4) 被禁用的流转以虚线/灰化区分；5) 点击节点/连线可选中并在右侧抽屉查看-编辑 | 重写 `views/admin/FlowConfig.vue`；echarts 5.5.0 已在依赖内 |
| **R2-4** | 流程节点 / 流转 CRUD | 1) 有 `flow:config` 权限的用户可对节点和流转增删改查；2) 无该权限仅只读（图可看、操作按钮隐藏/禁用）；3) 删除节点时若存在关联流转或存在处于该状态的问题 → 拒绝并给出明确提示（复用 `BizException` + 拦截器统一提示范式）；4) 新增流转禁止 from==to、禁止重复 (from,to) | 新增 `FlowNodeController` / `FlowTransitionController`（或合并为 `FlowConfigController`）；权限码 `flow:config` 已存在 |
| **R3-1** | 建立统一右侧抽屉规范组件 | 1) 新增通用组件（建议 `components/FormDrawer.vue`），封装 `el-drawer direction="rtl"` + 标准尺寸 + 标题 + 底部「取消/保存」操作条 + `loading` + `@closed` 重置；2) 组件 README/注释写明：**后续所有新页面的新增/编辑一律使用该组件**；3) 视觉与 `ModuleTreeDrawer.vue` 保持一致（`append-to-body`、宽度档位 480/620/800） | 新组件；形态基准 `components/ModuleTreeDrawer.vue`、`components/AdminStyleDrawer.vue` |
| **R3-2** | 核心管理页 Dialog → 右侧抽屉迁移 | 覆盖范围（本期口径见 §5 Q1）：`OrganizationManage.vue`、`UserManage.vue`、`ProjectManage.vue`、`MenuManage.vue`、`RoleManage.vue`(×2)、`ModuleTreeDrawer.vue` 内嵌的 3 个 Dialog、新建的模块配置页与流程配置页。验收：以上页面**不再出现 `<el-dialog>`**，全部编辑态从右侧滑出，表单校验/保存/取消行为与迁移前一致 | 上述文件；`grep -c "<el-dialog"` 应降为 0 |
| **R4-1** | 组织实体扩展通用属性 | 1) `organization` 表新增 `code`(组织编码,唯一,非空)、`leader_id`(部门负责人,可空,关联 user.id)、`status`(1启用/0停用,默认1)，`sort` 沿用；建议同时加 `description`；2) 加列 SQL 用 `information_schema` 动态防重复（沿用 phase4 范式）；3) 存量数据 `code` 需有回填策略（如 `ORG` + id 补零），`status` 默认 1；4) `Organization` / `OrganizationVO` / `OrganizationReq` 同步扩展，VO 额外返回 `leaderName` | `entity/Organization.java`、`dto/req/OrganizationReq.java`、`dto/resp/OrganizationVO.java`、`V20250730_issueflow_p0.sql:25` |
| **R4-2** | 组织管理页改为树形表格 | 1) 用 `el-table` + `row-key` + `tree-props={children:'children'}` 展示层级（替换原「左树 + 右详情」双栏）；2) 列：组织名称（带缩进层级）/组织编码/部门负责人/排序/状态/创建时间/操作；3) 操作列含「新增下级」「编辑」「删除」；4) 默认展开全部 | 重写 `views/admin/OrganizationManage.vue` |
| **R4-3** | 组织页工具栏 + 筛选区 | 1) 工具栏**左侧**：「展开全部」「收缩全部」；**右侧依次**：「新增」「刷新」「密度」「列设置」；2) 表格上方独立筛选区：组织名称（模糊）、状态（全部/启用/停用），含「查询」「重置」；3) 密度支持 large/default/small 三档并持久化到 localStorage；4) 列设置可勾选显示列并持久化 | 新工具栏；列设置直接复用 `views/admin/ProjectManage.vue:8-29` 的 `el-popover + localStorage('if_project_columns')` 范式（新 key 如 `if_org_columns`、`if_org_density`） |
| **R4-4** | 组织新增/编辑改为右侧抽屉 + 上级组织字段 | 1) 新增与编辑均通过 R3-1 抽屉打开；2) 表单含：组织名称*、组织编码*、上级组织（`el-tree-select`，**留空/选「顶级」即 parentId=0 根节点**）、部门负责人（复用 `GET /api/users/options` 远程搜索）、排序、状态、描述；3) 编辑时「上级组织」候选需排除自身及其所有子孙（防成环）；4) 组织编码唯一性由后端校验并返回明确错误 | `OrganizationManage.vue`；`api/organization.js`；`api/user.js:listUserOptions` |
| **R4-5** | 移除旧入口按钮 | 页面上不再存在「新建根组织」「新建子组织」按钮（需求原文为「新增根组织」「新建组织」，以实际代码文案为准），新增统一走工具栏右侧「新增」 | `OrganizationManage.vue:8-9` |
| **R5-1** | 用户「上级领导」字段 | 1) `user` 表加 `leader_id BIGINT NULL`（动态防重复 ALTER）；2) `User` / `UserReq` / `UserVO` 同步扩展，`UserVO` 额外返回 `leaderName`；3) 用户列表新增「上级领导」列 | `entity/User.java`、`dto/req/UserReq.java`、`dto/resp/UserVO.java`、`service/UserService.java` |
| **R5-2** | 用户表单上级领导选择器 | 1) 新增/编辑用户表单提供「上级领导」下拉，支持远程搜索、可清空；2) 编辑时正确回显；3) **不能选自己**；4) 保存后列表列同步刷新 | `views/admin/UserManage.vue`（本期同时按 R3-2 迁为抽屉）；直接复用 `GET /api/users/options` |
| **R6-1** | 菜单结构调整 | 1) 新增后台顶级菜单「项目管理」（分组节点，`path=/admin/project`，**无对应路由**，照搬 Phase4 `/admin/flow` 范式）；2) 原顶级「项目管理」(`/admin/projects`) **改名为「项目配置」**并挂到新分组下（sort=1）；3) 新增子菜单「模块配置」(`/admin/modules`，sort=2)；4) 顶级 sort 重排：概览1 / 问题管理2 / 项目管理3 / 流程管理4 / 系统管理5；5) 全部通过幂等 SQL 完成，`SideMenu.vue` 零改动 | `V2026xxxx_issueflow_phase5.sql`；参照 `V20260801_issueflow_phase4.sql:57-81` 的派生表 UPDATE 写法；`views/admin/ProjectManage.vue` 页面内标题「项目管理」需同步改为「项目配置」，`routes.js` 中 `meta.title` 同步 |
| **R6-2** | 新增「模块配置」页面 | 1) 新增路由 `/admin/modules` + 页面 `views/admin/ModuleManage.vue`；2) 页面顶部选择项目（复用 `GET /api/projects/options`），下方展示该项目模块树；3) 支持模块增删改、层级调整、依赖设置（复用 `api/module.js` 现有全部接口，**后端零改动**）；4) 新增/编辑用 R3-1 右侧抽屉 | `router/routes.js`、新页面；复用 `api/module.js`（`listModuleTree/createModule/updateModule/deleteModule/moveModule/batchDeleteModule/batchMoveModule/setModuleDependencies`）与 `components/ModuleTreeDrawer.vue` 的树交互逻辑 |
| **R7-1** | 数据初始化能力（后端） | 1) 新增接口 `POST /api/system/data/reset`，**仅 ADMIN** 可调用（service 层 `SecurityUtils.getCurrentRoleCode()` 校验，沿用现有范式）；2) 在单事务内清空业务表：`issue`、`issue_attachment`、`issue_history`、`issue_relation`、`tag`、`module`、`module_dependency`、`project`、`organization`，并删除**除 admin 外的全部 user**；3) **保留**：`role`、`permission`、`role_permission`、`menu`、`sys_config`、admin 账号（含其密码与角色）；4) 自增 ID 重置（`TRUNCATE` 或 `ALTER TABLE AUTO_INCREMENT=1`）；5) 返回各表清理条数；6) 清理后立即可用（无需重启），登录态不失效；7) 新增权限码 `system:reset`（「数据初始化」）并授予 ADMIN | 新增 `SystemDataController` + `SystemDataService`；表清单见 §0；注意 `IssueFlowApplication.initAdminUser` 只在启动时兜底，**不可依赖它重建 admin** |
| **R7-2** | 数据初始化入口（前端） | 1) 入口置于「系统管理 → 系统设置」页（或新增「数据初始化」卡片），仅 ADMIN 可见；2) 触发时必须二次确认：要求手动输入 `RESET` 字样才可提交（防误触）；3) 明确列出「将被清除」与「将被保留」两份清单；4) 执行中按钮 loading 且禁止重复提交；5) 成功后提示各表清理条数并自动刷新 | 新增页面/卡片；确认交互本身用抽屉承载（符合 R3） |

### P1（建议本阶段）

| 编号 | 需求 | 验收标准 | 关联 |
|---|---|---|---|
| **R2-5** | 流程图交互增强 | 支持缩放/拖拽平移、节点 hover 显示「允许角色 + 是否需填原因」浮层、一键「重置为默认流程」 | `FlowConfig.vue` |
| **R2-6** | 流转规则字段完整化 | 流转支持配置：允许角色（多选）、是否必填备注（对齐现有 `remarkRequired`）、是否启用、动作名（对齐 `HistoryActionEnum`）、排序 | `enums/HistoryActionEnum.java` |
| **R3-3** | 全站抽屉规范文档化 | 在 `src/frontend/src/components/README.md` 追加「统一抽屉规范」章节（尺寸档位、标题格式、按钮顺序、关闭时机） | `components/README.md` |
| **R4-6** | 组织筛选下沉到后端 | `GET /api/organizations` 支持 `name`/`status` 查询参数（命中节点需回带其祖先链以保持树完整） | `OrganizationController` |
| **R5-3** | 上级领导成环校验 | 后端校验汇报链不成环（A→B→A 拒绝） | `UserService` |
| **R6-3** | 模块配置页记住上次项目 | 项目选择持久化到 localStorage，二次进入自动选中 | 新页面 |

### P2（可后续）

| 编号 | 需求 | 说明 |
|---|---|---|
| **R2-7** | 多流程模板 / 按项目绑定不同流程 | 当前为全局单流程；后续可支持项目维度选择流程模板 |
| **R2-8** | 流程配置变更审计日志 | 记录谁在何时改了哪条流转 |
| **R3-4** | 剩余非核心 Dialog 迁移 | `AdminIssueList.vue`、`UserIssueList.vue`、`StatusFlowButtons.vue`、`AdminLayout.vue`（个人设置）等，见 §5 Q1 |
| **R4-7** | 组织成员管理 | 用户归属组织（`user.org_id`）+ 组织下成员列表 |
| **R5-4** | 汇报关系树可视化 | 基于 `leader_id` 渲染组织汇报关系图 |
| **R7-3** | 数据初始化后可选注入演示数据 | 清库后一键灌入一套 demo 数据 |

---

## 4. UI 设计稿

### 4.1 后台侧栏结构（R1）

```
┌──────────────┬──────────────────────────────────────────┐
│ issueFlow 后台│  [☰]  页面标题            [头像] 张三 ▾   │  ← .if-topbar (sticky)
├──────────────┼──────────────────────────────────────────┤
│ ▤ 概览        │                                          │
│ ▤ 问题管理    │                                          │
│ ▾ 项目管理    │        内容区 .if-content (overflow:auto) │
│   · 项目配置  │                                          │
│   · 模块配置  │        ← 内容滚动时，左侧完全不动          │
│ ▾ 流程管理    │                                          │
│   · 流程配置  │                                          │
│   · 流程监控  │                                          │
│ ▾ 系统管理    │                                          │
│   · 用户管理  │      ↕ 菜单项过多时，仅此区域内部滚动       │
│   · 组织管理  │        (.if-sidebar__menu {flex:1;         │
│   · 菜单管理  │         overflow-y:auto})                 │
│   · 角色管理  │                                          │
│   · 系统设置  │                                          │
├──────────────┤                                          │
│ ⌂  返回前台   │  ← 固定底部，永不滚动、永不裁切            │
└──────────────┴──────────────────────────────────────────┘
   220px（折叠态 64px → 仅 ⌂ 图标，居中）
```

**布局要点**
- `.if-sidebar` 改为 `display:flex; flex-direction:column; height:100vh`（或 `100%` + 父级定高），**移除 `overflow:hidden`**，改由内部菜单容器 `overflow-y:auto`。
- 「返回前台」容器：`flex-shrink:0` + `border-top: 1px solid rgba(255,255,255,.08)`，与菜单区视觉分隔。
- 用户明确要求 `position: fixed`：若采用 fixed，必须同步三种情形的定位——
  - 桌面展开：`left:0; bottom:0; width:220px`
  - 桌面折叠：`width:64px`（跟随 `--sidebar-collapsed-width`）
  - 移动端（≤768px）：侧栏本身已是 `position:fixed` 抽屉，此时按钮应相对抽屉定位（`position:absolute; bottom:0`），否则抽屉关闭后按钮会残留在屏幕左下角。
  - **实现建议**：优先「侧栏 `height:100vh` flex 列 + 底部块 `flex-shrink:0`」，视觉效果等同 fixed 且天然规避上述三种边界；若架构师坚持字面 `position: fixed`，需额外处理 R7 风格设置把 `--if-sidebar-position` 改为 `static` 的场景。

### 4.2 组织管理页（R4）

```
┌───────────────────────────────────────────────────────────────────────────┐
│ 【筛选区】                                                                 │
│  组织名称 [____________]   状态 [全部 ▾]        [ 查询 ]  [ 重置 ]         │
├───────────────────────────────────────────────────────────────────────────┤
│ 【工具栏】                                                                 │
│  [展开全部] [收缩全部]                    [+ 新增] [⟳ 刷新] [⇕ 密度▾] [⚙ 列设置▾] │
│   ←── 左侧 ──→                                        ←──── 右侧依次 ────→ │
├───────────────────────────────────────────────────────────────────────────┤
│ 组织名称            │ 组织编码  │ 部门负责人 │ 排序 │ 状态  │ 创建时间   │ 操作 │
├─────────────────────┼──────────┼──────────┼─────┼──────┼──────────┼──────┤
│ ▾ 总公司            │ ORG001   │ 张三      │  1  │ ●启用 │ 2026-... │新增下级 编辑 删除│
│   ▾ 研发中心        │ ORG002   │ 李四      │  1  │ ●启用 │ 2026-... │新增下级 编辑 删除│
│       后端组        │ ORG004   │ 王五      │  1  │ ●启用 │ 2026-... │新增下级 编辑 删除│
│       前端组        │ ORG005   │ -        │  2  │ ○停用 │ 2026-... │新增下级 编辑 删除│
│   ▸ 市场部          │ ORG003   │ 赵六      │  2  │ ●启用 │ 2026-... │新增下级 编辑 删除│
└───────────────────────────────────────────────────────────────────────────┘
```

- 树形表格：`<el-table :data="treeData" row-key="id" :tree-props="{children:'children'}" default-expand-all :size="density">`
- 「展开全部 / 收缩全部」：维护 `expandedKeys` 或对 `el-table` ref 递归 `toggleRowExpansion`。
- 「密度」下拉：large / default / small → 绑定 `el-table` 的 `size`，持久化 `localStorage['if_org_density']`。
- 「列设置」下拉：沿用 `ProjectManage.vue` 的 `el-popover` + 复选框 + 全选/全不选/重置默认 + `localStorage['if_org_columns']`，操作列常显。
- 页面上**不再出现**「新建根组织」「新建子组织」按钮。

### 4.3 通用右侧抽屉结构（R3，全站标准）

```
                          ┌────────────────────────────────────┐
                          │ 新建组织                        [×] │ ← 标题区
   （列表页保持可见，       ├────────────────────────────────────┤
     不被遮挡，作为上下文）  │  组织名称 *  [__________________]   │
                          │  组织编码 *  [__________________]   │
                          │  上级组织    [请选择（留空=根节点)▾] │ ← 表单区
                          │  部门负责人  [搜索用户…           ▾] │   (滚动)
                          │  排序        [  0  ] ▲▼             │
                          │  状态        (●启用) (○停用)        │
                          │  描述        [__________________]   │
                          │              [__________________]   │
                          ├────────────────────────────────────┤
                          │                 [ 取消 ]  [ 保存 ]  │ ← 固定底部操作条
                          └────────────────────────────────────┘
                             direction="rtl"  size=480/620/800px
```

**规范约定（`components/FormDrawer.vue`）**
| 项 | 约定 |
|---|---|
| 方向 | `direction="rtl"`（右侧滑出），`append-to-body` |
| 宽度档位 | `sm=480px`（简单表单）/ `md=620px`（默认，对齐 `ModuleTreeDrawer`）/ `lg=800px`（含表格或流程图） |
| 标题 | `{动作}{对象}`，如「新建组织」「编辑用户」「编辑流转规则」 |
| 底部 | 固定操作条，按钮顺序**统一为 左「取消」右「保存」**，保存带 `loading` |
| 关闭 | `@closed` 时重置表单与校验状态（对齐 `ModuleTreeDrawer.onClosed`） |
| 遮罩 | 保留遮罩，点击遮罩不直接关闭（表单有改动时二次确认） |
| 适用 | 后续**所有**新增/编辑/详情场景一律使用；纯文字确认可继续用 `ElMessageBox`（见 §5 Q1） |

### 4.4 流程配置页（R2）

```
┌───────────────────────────────────────────────────────────────────────────┐
│ 流程配置                        [+ 新增节点] [+ 新增流转] [⟳ 刷新] [重置默认]│
├───────────────────────────────────────────────────────────────────────────┤
│                                                                           │
│   ┌────────┐  认领(D/A)   ┌────────┐  提交修复(D/A)  ┌────────┐            │
│   │ 待处理  │ ───────────▶│ 处理中  │ ──────────────▶│ 待验证  │            │
│   │ ● OPEN │              │ ● IN_P │◀ ─ ─ ─ ─ ─ ─ ─ │● PEND_V│            │
│   └────────┘              └────────┘  验证驳回(T/A)  └────────┘            │
│        ▲                              ※需填原因·可开关       │            │
│        │                                                     │ 验证通过(T/A)│
│        │ 重开(A)                                             ▼            │
│        │ ※可开关                              ┌────────┐  关闭(T/A)  ┌────────┐
│        └──────────────────────────────────────│ 已关闭  │◀───────────│验证通过 │
│                                               │● CLOSED│            │● VERIF │
│                                               └────────┘            └────────┘
│                                                                           │
│   图例： ● 状态色（复用 statusColor）  ── 启用流转   ─ ─ 已禁用流转          │
│         (D)=DEVELOPER (T)=TESTER (A)=ADMIN                                │
├───────────────────────────────────────────────────────────────────────────┤
│ 【节点列表】(可折叠)                    │ 【流转规则列表】(可折叠)            │
│ 名称/编码/状态码/颜色/排序/操作          │ 起点→终点/动作/允许角色/需备注/启用/操作│
└───────────────────────────────────────────────────────────────────────────┘
```

- 点击图上节点 → 右侧抽屉「编辑节点」（名称、编码、状态码、颜色、排序）。
- 点击图上连线 → 右侧抽屉「编辑流转规则」（起点节点、终点节点、动作、允许角色多选、是否必填备注、是否启用）。
- 无 `flow:config` 权限：隐藏顶部所有写操作按钮，图与列表只读。
- 上半部图 + 下半部双列表，图与列表数据同源，任一处修改后整体刷新。

### 4.5 用户表单「上级领导」选择器（R5）

```
┌────────────────────────────────────┐
│ 编辑用户                        [×] │
├────────────────────────────────────┤
│  账号     [zhangsan] (编辑时禁用)   │
│  姓名     [张三_______________]     │
│  邮箱     [___________________]     │
│  手机     [___________________]     │
│  角色 *   [开发人员             ▾]  │
│  上级领导 [李四（lisi）        ▾ ×] │ ← 新增：远程搜索 + 可清空
│           ┌──────────────────────┐  │    filterable / remote / clearable
│           │ 🔍 li                │  │    remote-method → GET /api/users/options?keyword=
│           ├──────────────────────┤  │    禁止选择自己（编辑态过滤当前 id）
│           │ 李四（lisi）          │  │
│           │ 李小明（liming）      │  │
│           └──────────────────────┘  │
│  状态     (●启用) (○禁用)           │
├────────────────────────────────────┤
│                 [ 取消 ]  [ 保存 ]  │
└────────────────────────────────────┘
```
> 交互与 `ProjectManage.vue` 的「负责人」下拉完全一致（同一个 `listUserOptions` 接口 + 已选项合并回显逻辑），可直接复制该范式。

### 4.6 菜单结构树（R6）

```
后台菜单（menu.type = 2）
├── 概览            /admin/index            sort=1
├── 问题管理        /admin/issues           sort=2   [issue:list]
├── 项目管理  ★新增分组  /admin/project     sort=3   （纯分组，无路由）
│   ├── 项目配置  ★改名  /admin/projects    sort=1   [project:list]  ← 原顶级「项目管理」
│   └── 模块配置  ★新增  /admin/modules     sort=2   [project:list]  ← 新路由 + 新页面
├── 流程管理        /admin/flow             sort=4   （Phase4 已有分组）
│   ├── 流程配置    /admin/flow-config      sort=1   [flow:config]
│   └── 流程监控    /admin/flow-monitor     sort=2   [flow:view]
└── 系统管理        /admin/system           sort=5
    ├── 用户管理    /admin/system/users     sort=1   [user:list]
    ├── 组织管理    /admin/system/organizations sort=2 [organization:list]
    ├── 菜单管理    /admin/system/menus     sort=3   [menu:list]
    ├── 角色管理    /admin/system/roles     sort=4   [role:list]
    └── 系统设置    /admin/settings         sort=5   [settings:update]  ← R7 入口所在
```
> 全部通过幂等 SQL 完成：`INSERT ... WHERE NOT EXISTS` 建分组；`UPDATE menu SET name='项目配置', parent_id=(派生表子查询), sort=1 WHERE path='/admin/projects' AND type=2`。前端 `SideMenu.vue` **零改动**（Phase4 已验证该范式）。

### 4.7 数据初始化确认（R7）

```
┌────────────────────────────────────────────┐
│ 数据初始化                              [×] │
├────────────────────────────────────────────┤
│ ⚠ 高危操作 · 不可撤销                       │
│                                            │
│ 将被【清除】：                              │
│   · 全部问题 / 附件 / 流转历史 / 关联关系    │
│   · 全部标签                                │
│   · 全部项目 / 模块 / 模块依赖               │
│   · 全部组织                                │
│   · 除 admin 外的全部用户                    │
│                                            │
│ 将被【保留】：                              │
│   · admin 账号（含密码）                     │
│   · 角色 / 权限 / 角色权限映射                │
│   · 菜单结构                                │
│   · 系统配置（主题、风格等）                  │
│                                            │
│ 请输入 RESET 以确认：  [__________]         │
├────────────────────────────────────────────┤
│                 [ 取消 ]  [ 确认清空 ](禁用) │
└────────────────────────────────────────────┘
```

---

## 5. 待确认问题

| # | 问题 | 我的推荐 | 影响 |
|---|---|---|---|
| **Q1** | 需求3「替换所有现有 Modal/Dialog」的**范围**：是「建立统一右侧抽屉标准 + 迁移核心管理页」，还是字面替换站内每一个 dialog（含极小确认框）？ | **推荐前者（有界迁移）**。实测全站 13 处 `<el-dialog>`（10 文件）+ 10 个文件使用 `ElMessageBox.confirm`。建议本期 P0 覆盖：组织/用户/项目/角色/菜单 + 模块配置页 + 流程配置页 + `ModuleTreeDrawer` 内嵌 3 个 Dialog（共 10 处）；**`ElMessageBox.confirm` 这类纯文字确认保持不动**——它是命令式 API 而非 Dialog 组件，强行改抽屉反而破坏「破坏性操作需要视觉打断」的可用性原则，且改造成本极高、回归面极大。剩余 `AdminIssueList` / `UserIssueList` / `StatusFlowButtons` / `AdminLayout(个人设置)` 4 处降为 P2。**请拍板是否接受这个范围。** | 决定 R3-2 工作量（10 处 vs 13 处 + 全站 confirm 改造） |
| **Q2** | 需求2 的**数据模型与可视化实现**：后端确认**无**流程节点/流转表（规则硬编码在 `StateMachine.java`）。①两表如何设计？②流程图用什么画？ | **①数据模型推荐**：<br>`flow_node`：`id / name(名称) / code(编码) / status_code(对应 IssueStatusEnum 的 0-4，保证与存量 issue.status 兼容) / node_type(1开始 2审核 3结束) / color / sort / enabled / 审计列`<br>`flow_transition`：`id / from_node_id / to_node_id / action_code(对齐 HistoryActionEnum) / action_name / allow_roles(逗号分隔角色码) / remark_required(0/1) / enabled(0/1) / sort / 审计列`<br>**关键约束**：`status_code` 必须与现有 `IssueStatusEnum` 一一对应，否则存量 issue 数据全部失配；迁移脚本把现有 6 条 TRANSITIONS 原样种子化。<br>**②可视化推荐 echarts `graph` 系列**（`layout:'none'` + 显式 x/y 坐标 + `edgeSymbol:['none','arrow']` + `edgeLabel`）：echarts 5.5.0 **已在依赖里**，零新增依赖，符合项目「不引入新第三方依赖」原则，且自带缩放/拖拽/tooltip。自绘 SVG 虽更可控但要自己实现布局/箭头/自环，成本更高。**不建议**引入 LogicFlow/X6（新依赖 + 体积）。<br>**请确认：是否接受 echarts graph + 是否接受节点坐标由后端 `pos_x/pos_y` 字段持久化（否则每次布局靠前端算，用户拖动后不保存）。** | 决定 R2 全部工作量与是否新增依赖 |
| **Q3** | 需求7 数据初始化后：admin 账号 / 角色 / 权限 / 菜单种子 / 内置角色**是否保留**？是否**仅 ADMIN** 可操作？入口放哪？ | **推荐**：①**全部保留** `role`、`permission`、`role_permission`、`menu`、`sys_config` 与 admin 账号——这些是「系统骨架」不是「业务数据」，清掉会导致系统直接不可用（注意：`IssueFlowApplication.initAdminUser` 只在**启动时**兜底建 admin，运行时删掉不会自动恢复）；②**仅 ADMIN**，service 层用 `SecurityUtils.getCurrentRoleCode()` 校验并新增权限码 `system:reset`；③入口放「系统管理 → 系统设置」页的独立卡片，配二次输入 `RESET` 确认。<br>**另需拍板**：清库时是否**一并删除磁盘附件文件**（`ATTACHMENT_BASE_PATH` 默认 `/data/attachments`）？我倾向**删除**（否则残留孤儿文件占盘），但这是不可逆的文件系统操作，需明确授权。 | 决定 R7 的清理清单与安全边界 |
| **Q4** | 需求6「模块配置」页与 Phase4 已有的「项目配置 → 行内『模块』按钮抽屉」是**并存**还是二选一？ | **推荐并存**，理由：两者场景不同——行内抽屉是「我正在看项目，顺手改一下模块」（上下文内操作），独立页是「我要集中维护模块」（批量/跨项目切换）；且 Phase4 的 `ModuleTreeDrawer.vue` 已上线验证，删掉是净损失。**实现上应把树的交互逻辑抽成共享组件**，避免两份代码各自演化。若主理人希望收敛入口，次优方案是保留独立页、移除行内按钮（但会降低项目页的操作效率）。 | 决定 R6-2 是否需要同步改 `ProjectManage.vue` |
| **Q5** | 需求4 原文要求移除「**新增根组织**」和「**新建组织**」按钮，但代码实际文案是「**新建根组织**」和「**新建子组织**」 | 判断为同一指代（用户凭记忆描述）。**推荐按语义执行：两个旧按钮全部移除**，新增统一收敛到工具栏右侧「新增」+ 行内「新增下级」。如与用户本意不符请指正。 | 低风险，但需确认 |
| **Q6** | 需求4 组织新增 `code`（组织编码）为必填唯一字段，但**存量组织数据没有编码** | **推荐**迁移脚本自动回填 `CONCAT('ORG', LPAD(id, 3, '0'))`，并加唯一索引。若线上已有真实组织数据且有既定编码规则，请提供规则。 | 影响迁移脚本 |
| **Q7** | R1 用户明确要求用 `position: fixed`，但当前侧栏在移动端已是 fixed 抽屉、且 R7 风格设置可把侧栏改为 `static` | **推荐**用「侧栏 flex 列 + 菜单区 `overflow-y:auto` + 底部块 `flex-shrink:0`」实现，视觉效果与 fixed 完全一致，但天然兼容折叠态/移动端/风格切换三种边界。若必须字面 `position:fixed`，需接受额外的三套媒体查询与风格联动成本。**请确认是否接受等效实现。** | 决定 R1 实现方式与回归范围 |

---

## 6. 交付边界与风险提示

| 风险 | 说明 | 缓解 |
|---|---|---|
| **R2 改动状态机核心** | `StateMachine` 由硬编码改为读库，是问题流转主链路，改错会导致全站无法流转 | 迁移脚本必须原样种子化现有 6 条流转；测试用例必须覆盖 6 条正向 + 非法流转 + 两个开关关闭态；建议保留「配置为空时回退到硬编码默认」的兜底 |
| **R3 大面积改动 UI** | 涉及 6+ 个已上线页面的表单容器 | 逐页迁移、逐页回归；表单校验与保存逻辑**不动**，只换容器 |
| **R4 组织表加唯一索引** | `code` 唯一索引在存量数据未回填时会建失败 | SQL 顺序：先加列 → 再回填 → 最后加唯一索引 |
| **R7 不可逆操作** | 误触将清空全部业务数据 | 仅 ADMIN + 输入 `RESET` 二次确认 + 明确清单展示；生产环境建议增加配置开关 `system.data-reset.enabled` 默认关闭 |
| **迁移脚本幂等性** | 需支持重复执行 | 沿用 phase4 范式：建表 `IF NOT EXISTS`；加列用 `information_schema` 动态判断；菜单用 `INSERT...WHERE NOT EXISTS` + 派生表子查询 UPDATE |

---

*文档结束 · 待 §5 待确认问题拍板后，交由架构师（高见远）产出增量设计与任务分解。*
