# issueFlow 增量 PRD（Phase 2）

> 文档版本：v0.1（增量 PRD）
> 角色：产品经理 许清楚
> 关联文档：`docs/prd.md`（基线 PRD）、`docs/incremental-design.md`（P0 技术设计）、`docs/architecture.md`
> 技术栈（沿用）：后端 Spring Boot 3.2.5 + MyBatis-Plus + MySQL 8 + Redis 7 + JWT；前端 Vue3 + Element Plus + Pinia + Vue Router + Vite。

---

## 0. 增量范围与现状对齐（供架构师/工程师速读）

本期在 P0 已上线能力之上，交付 6 项增量需求。下表为现状与本期改动面的对照，便于理解"最小改动"边界。

| 维度 | 现状（P0 已上线） | 本期改动 |
|---|---|---|
| 布局切换入口 | 后台顶栏右上 + 左下侧栏**各一个** `LayoutSwitchEntry`；前台顶栏 + 左下侧栏各一个 | 移除后台顶栏入口，仅保留左下侧栏（需求 1） |
| 缓存清理 | 仅后台 `AdminLayout` 头像下拉有「清理缓存」 | 前台 `UserLayout` 下拉镜像增加（需求 2） |
| 问题关联 | 无关联能力；`issue` 表无关联字段 | 新增 `issue_relation` 表，支持前置/后置任务多选 + 防环（需求 3） |
| 菜单 | `menu` 表无"端"维度；前端侧栏菜单**硬编码**在 `AdminLayout`/`UserLayout` 模板 | `menu` 加 `type` 字段；菜单管理支持按端配置；前端**动态渲染**菜单（需求 4） |
| 权限 | `User.roleId` 单角色；仅 `requireAdmin()` 一刀切；`menu.permission` 存而未用 | 新增权限目录 + 角色权限映射；角色管理 CRUD + 授权；后端过渡到按权限鉴权（需求 5） |
| 测试数据 | 仅 `data.sql` 的 4 条角色 + 默认项目 | 新增覆盖全模块的种子数据 SQL（需求 6） |

> 设计原则（沿用 P0）：不引入新第三方依赖；复用 `BaseEntity`/`Result`/`requireAdmin` 范式；前端零新依赖；逻辑删除沿用 `deleted`；排序 `sort ASC, id ASC`。

---

## 1. 产品目标

1. **统一前后台导航与体验一致性**：收敛冗余的布局切换入口，并让前台具备与后台同质的缓存清理能力，降低用户认知负担。
2. **建立缺陷间的依赖关系**：支持为问题设置前置/后置任务，暴露依赖链路，避免"前置未解就推进后置"的协作错乱。
3. **菜单可配置化（按端）**：将前后台菜单从硬编码改为数据驱动，按"前台端/后台端"维度管理，支持动态增删与侧栏可见性控制。
4. **从"ADMIN 一刀切"过渡到 RBAC**：提供角色管理与细粒度操作权限分配，为后续多团队、多角色协作奠定权限底座（本期聚焦功能级权限，行级数据权限保持现状）。

---

## 2. 用户故事（按 6 项需求拆分）

### 需求 1：移除后台顶栏布局切换按钮
- **作为管理员**，我希望后台只在左下角侧栏保留「返回前台」入口，以便顶栏不被多余按钮占用、与产品规范一致。

### 需求 2：前台用户增加缓存清理功能
- **作为前台用户**，我希望在头像下拉里也能「清理缓存」，以便遇到界面/数据不同步时像后台一样一键清理并刷新。

### 需求 3：问题之间的关联关系
- **作为开发人员**，我希望给问题 A 指定前置任务（如 B、C），以便团队清楚"哪些问题必须先解决"。
- **作为测试人员**，我希望在问题详情里看到某问题的后置任务（谁依赖它），以便评估改动影响面。
- **作为任意用户**，我希望在列表/详情点击关联问题直接跳转其详情，以便快速上下文切换。
- **作为系统**，我必须在建立关联时阻止循环依赖（A 前置 B 且 B 前置 A），以保证依赖图无环。

### 需求 4：菜单管理改造（前台/后台可配置）
- **作为管理员**，我希望在菜单管理里区分"前台端/后台端"分别维护菜单树，以便两端菜单独立配置。
- **作为管理员**，我希望通过菜单的增删改控制前端侧栏出现哪些项，以便灵活调整导航而不发版。
- **作为前台/后台用户**，我希望侧栏菜单与后台配置一致（动态渲染），以便看到的是管理员配置后的真实结构。

### 需求 5：系统管理新增"角色管理"（过渡到 RBAC）
- **作为管理员**，我希望对角色进行增删改查，以便管理团队角色字典。
- **作为管理员**，我希望为角色勾选操作权限（如问题管理的查看/新增/编辑/删除/导出），以便按职责分配能力。
- **作为系统**，后端接口需按角色权限鉴权而非仅 ADMIN，以便非管理员也能在授权范围内操作。

### 需求 6：创建覆盖各模块核心功能的测试数据
- **作为测试/演示人员**，我希望部署后库内已有项目、用户/角色、问题（含状态/优先级/标签/关联）、组织层级、菜单配置、流程配置，以便无需手工造数即可验证新功能。

---

## 3. 需求池（优先级）

> P0 必做（本期交付）；P1 重要（建议本期或紧接）；P2 增强（后续迭代）。

### P0 — 核心必做
- **R1** 后台 `AdminLayout` 移除顶栏 `LayoutSwitchEntry variant="topbar"`，保留左下侧栏入口。
- **R2** 前台 `UserLayout` 头像下拉新增「清理缓存」（逻辑同后台：`localStorage.clear()` + 提示 + 刷新）。
- **R3** 新增 `issue_relation` 表；问题详情/列表支持设置前置任务（多选）、后置任务（多选）；列表/详情可点击跳转关联问题详情；建立关联时**防止循环依赖**。
- **R4** `menu` 表新增 `type` 字段（1=前台端 / 2=后台端）；菜单管理页支持按端筛选与配置；`AdminLayout`/`UserLayout` 侧栏菜单改为**从接口按端动态渲染**（替代硬编码）。
- **R5 机制** 新增权限目录 `permission` 与 `role_permission` 映射表；后端提供 `requirePermission(...)` 鉴权助手（ADMIN 放行全部）；`requireAdmin()` 逐步被权限校验替换；至少覆盖 6 个管理模块 + 流程配置 + 系统设置。
- **R5 管理** 新增角色管理页：角色 CRUD + 权限分配（勾选树/列表）。
- **R6** 新增种子数据 SQL，覆盖项目、用户/角色（含权限）、问题（状态/严重等级/标签/关联）、组织层级、菜单配置（按端）、流程配置。

### P1 — 重要
- **R4** 前端按用户权限**过滤侧栏菜单**：菜单带 `permission` 且用户无该权限则隐藏（ADMIN 全显；无 permission 的菜单对登录用户可见）。
- **R5** 管理页按钮级权限控制（新增/编辑/删除/导出按权限显隐）。
- **R5** 角色权限变更**无需重新登录即生效**（权限集走 Redis 缓存，提供刷新/失效机制）。
- **R3** 问题列表增加"有前置/后置"标记列或筛选；关联编辑支持一键解除。
- **R2** 前台 `UserLayout` 头像下拉同步增加「个人设置」（与后台对称的只读信息 Dialog），保持两端一致。

### P2 — 增强
- **R5** 用户多角色（`user_role`  junction，突破单 `roleId`）；JWT 携带权限集。
- **R4** 菜单拖拽排序（替代数字 `sort` 输入）。
- **R3** 关联依赖图可视化（DAG 展示）。
- **R5** 行级数据权限（提交者仅见自己数据）的精细化配置（目前行为保持）。

---

## 4. 页面 / 功能说明（字段、交互、校验）

### 4.1 需求 1：布局切换入口收敛（纯前端）
- **改动点**：`src/frontend/src/layouts/AdminLayout.vue` 顶栏 `topbar-right` 删除 `<LayoutSwitchEntry variant="topbar" />`（当前为第一个子元素，约第 84–85 行），保留侧栏底部 `variant="sidebar"`。
- **校验**：后台顶栏不再出现「返回前台」；左下侧栏入口功能不变；前台端不受影响。
- **无需后端改动。**

### 4.2 需求 2：前台缓存清理（纯前端）
- **改动点**：`src/frontend/src/layouts/UserLayout.vue` 头像 `el-dropdown-menu` 内，在「退出登录」前新增「清理缓存」项（command=`clearCache`，图标 `Refresh`），逻辑复用后台：
  ```js
  else if (cmd === 'clearCache') {
    localStorage.clear()
    ElMessage.success('缓存已清理，即将刷新页面')
    setTimeout(() => window.location.reload(), 600)
  }
  ```
- **交互**：点击 → 清 `localStorage` → 提示 → 600ms 后整页刷新（与后台一致）。
- **无需后端改动。**

### 4.3 需求 3：问题关联关系
**后端**
- 新增接口（建议挂在 `IssueController` 或独立 `IssueRelationController`，均需登录）：
  - `GET /api/issues/{id}/relations` → 返回该问题的前置任务列表、后置任务列表（含 `id/issueNo/title/status`）。
  - `PUT /api/issues/{id}/relations` → 入参 `{ predecessorIds: Long[], successorIds: Long[] }`，整体替换该问题的前置/后置。
  - `GET /api/issues/options` → 关联问题选择器下拉（返回 `id/issueNo/title`，排除自身与已存在环的候选可前端灰显）。
- **防环规则（核心）**：关系以"前置边"建模——`issue_relation(issue_id, related_id, rel_type=1)` 表示 `related_id` 是 `issue_id` 的前置任务。后置任务由反向查询推导（见 §7）。
  - 拒绝自关联：`issue_id == related_id`。
  - 拒绝成环：新增边 `A→Y`（Y 为 A 前置）时，若从 Y 沿前置边可到达 A（即 Y 传递依赖于 A），则拒绝并返回 `RELATION_CYCLE` 业务异常。算法：以 Y 为起点做 BFS/DFS 沿 `issue_relation(issue_id=当前, rel_type=1)` 遍历，命中 A 即存在环。（建议用递归 CTE 或内存遍历，数据量小内存遍历即可。）

**前端**
- 问题详情页（`AdminIssueList` 详情 Dialog / 独立详情页）新增「关联问题」区：
  - 两个 `el-select multiple`：「前置任务」「后置任务」，选项来自 `/api/issues/options`。
  - 列表展示已关联问题（issueNo + 标题 + 状态标签），每项可点击跳转 `/admin/issues?issueId=xxx` 或详情。
  - 保存时调用 `PUT /relations`，失败（成环）提示具体冲突对。
- 问题列表（`UserIssueList`/`AdminIssueList`）：关联问题 issueNo 可点击跳转（P1 增加"有依赖"标记列）。

### 4.4 需求 4：菜单按端可配置 + 动态渲染
**后端**
- `Menu` 实体新增 `private Integer type;`（1=前台端 / 2=后台端，默认 2）。`MenuReq`/`MenuVO` 同步增加 `type`。
- `MenuService.listAll()` 支持按 `type` 过滤；`MenuController` 新增 `GET /api/menus?type=1|2`（仍 ADMIN）；新增 `GET /api/menus/sidebar?type=1|2` 供前端侧栏动态渲染（**仅需登录**，返回该端菜单树，按 `sort,id` 排序）。
- 菜单管理页接口入参/出参带 `type`。

**前端 - 菜单管理页（`MenuManage.vue`）**
- 顶部增加"端"切换（Tabs/Radio：前台端 / 后台端），列表与表单仅操作当前端。
- 新建/编辑表单新增「端」字段（`el-radio` 1前台/2后台）。
- 树形列表（`treeData`）按当前端构建；`parentTreeOptions` 仅含同端节点（跨端不可做父子）。
- **校验**：名称必填；同端内 `path` 建议唯一（非强约束）；删除有子节点时返回 `NODE_HAS_CHILDREN`。

**前端 - 动态渲染（AdminLayout / UserLayout）**
- 新增递归菜单组件 `components/SideMenu.vue`（或 `MenuTree.vue`）：根据 `sidebar?type=2|1` 返回的树渲染 `el-menu`/`el-sub-menu`，支持父子（如"系统管理"子菜单）。
- `AdminLayout.vue`/`UserLayout.vue` 移除模板内硬编码 `el-menu-item`，改为 `<SideMenu :type="2|1" />`。
- 激活态、图标（`icon` 字段→Element Plus 动态组件）、折叠态保持现状体验。

### 4.5 需求 5：角色管理 + RBAC
**后端**
- 新增 `permission` 表（权限目录）、`role_permission` 表（角色-权限映射）。见 §7 DDL。
- 新增 `RoleController`（`/api/roles` 现有 `GET /api/roles` 列表保留并扩展）：
  - `POST /api/roles`（ADMIN 新建角色）
  - `PUT /api/roles/{id}`（ADMIN 编辑）
  - `DELETE /api/roles/{id}`（ADMIN 删除；内置角色 ADMIN/SUBMITTER/DEVELOPER/TESTER 禁止删，返回 `ROLE_BUILTIN_PROTECTED`）
  - `GET /api/roles/{id}/permissions`（该角色已分配权限码集合）
  - `PUT /api/roles/{id}/permissions`（入参 `{ permissionCodes: String[] }` 整体替换）
  - `GET /api/permissions`（权限目录树/列表，供授权页渲染）
- 鉴权助手（替代 `requireAdmin`）：
  ```java
  void requirePermission(String... perms) {
    String role = SecurityUtils.getCurrentRoleCode();
    if (Constants.ROLE_ADMIN.equals(role)) return;      // 管理员放行全部
    Set<String> owned = permissionCache.get(userId/role); // 见下
    if (!Collections.disjoint(owned, Set.of(perms))) return;
    throw new BizException(ResultCode.PERMISSION_DENIED);
  }
  ```
- 权限解析：登录/`info` 时按 `user.roleId → role_permission → permission` 解析权限集，**缓存至 Redis**（key 如 `perm:role:{roleId}`，TTL 可配）；权限变更时剔除该缓存（或提供 `POST /api/roles/permissions/refresh`）。JWT **仍仅携带 roleCode**（不改动 token 结构，避免前端改动）。
- 逐步将 6 个管理模块的 `requireAdmin()` 替换为对应 `requirePermission("xxx:list/create/update/delete/export")`；`/options` 等只读下拉保持仅登录。

**前端 - 角色管理页（`RoleManage.vue`，置于"系统管理"下）**
- 列表：`el-table` 展示角色码/名称/描述/权限数/操作（编辑、分配权限、删除）。
- 新建/编辑 Dialog：角色码（新建可填，内置角色不可改码）、名称、描述。
- 分配权限 Dialog：左侧模块树 + 右侧操作复选（查看/新增/编辑/删除/导出），保存调用 `PUT /roles/{id}/permissions`。
- **系统管理**子菜单新增「角色管理」项（写入 `menu` 表，type=2）。

**前端 - 权限消费（P1）**
- 侧栏菜单按 `permission` 过滤（§4.4）；管理页按钮按权限 `v-if` 显隐。

### 4.6 需求 6：测试数据
- 新增 `scripts/V20250731_issueflow_phase2.sql`（幂等 `INSERT IGNORE` / `IF NOT EXISTS`），内容见 §7。
- 目标：部署后无需手工造数即可验收 R1–R5。

---

## 5. 权限与角色设计要点（RBAC）

### 5.1 模型选择（本期推荐）
- **单角色 + 权限集**：沿用 `User.roleId` 单角色不动；角色通过 `role_permission` 绑定权限集。理由：改动面最小（不碰 JWT 结构、不碰 `AuthService` 的 `singletonList`、不碰前端 `userStore.roles`）；先建立"功能级 RBAC"底座。
- **多角色**（P2）：若未来需一个用户兼具多角色，再引入 `user_role` junction + JWT 携带权限集，本期**不做**。

### 5.2 权限码命名
- 规范：`module:resource:action`。`action ∈ {list, create, update, delete, export}`。
- 模块建议：`issue / project / user / organization / menu / role / settings / flow / dashboard`。
- 例：`issue:list`、`issue:create`、`project:delete`、`role:assign`、`menu:update`。

### 5.3 过渡策略
1. ADMIN 始终放行（`requirePermission` 内首判），保证既有管理功能不回退。
2. 6 个管理模块 + 流程配置 + 系统设置逐步从 `requireAdmin()` 切到 `requirePermission(...)`。
3. 前台提交问题等保持"仅登录 + 数据作用域（自己）"的现状逻辑不变。
4. 权限变更经 Redis 缓存失效即时生效，无需重新登录。

### 5.4 内置角色与默认权限（种子建议）
| 角色 | 默认权限范围 |
|---|---|
| ADMIN | 全部（含 role:*、settings:*、flow:*） |
| SUBMITTER | `issue:list/create/update`（自身）、`dashboard:view` |
| DEVELOPER | `issue:list/create/update`、`dashboard:view` |
| TESTER | `issue:list/create/update`、`dashboard:view` |
> 管理类模块（project/user/organization/menu/role/settings/flow）默认仅 ADMIN 拥有；如需放开给职能角色，在角色管理页分配即可。

---

## 6. 数据需求（新表 / 新字段 / 测试数据）

### 6.1 新字段
- `menu.type`：`INT`（1=前台端 / 2=后台端，默认 2）。

### 6.2 新表
```sql
-- 问题关联表（前置边建模，后置由反向推导）
CREATE TABLE IF NOT EXISTS `issue_relation` (
  `id`         BIGINT NOT NULL AUTO_INCREMENT,
  `issue_id`   BIGINT NOT NULL COMMENT '当前问题',
  `related_id` BIGINT NOT NULL COMMENT '关联问题',
  `rel_type`   TINYINT NOT NULL DEFAULT 1 COMMENT '1=related_id 是 issue_id 的前置任务',
  `created_at` DATETIME DEFAULT NULL,
  `updated_at` DATETIME DEFAULT NULL,
  `deleted`    INT DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ir` (`issue_id`,`related_id`,`rel_type`),
  KEY `idx_ir_related` (`related_id`,`rel_type`),
  KEY `idx_ir_issue` (`issue_id`,`rel_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问题关联表';

-- 权限目录
CREATE TABLE IF NOT EXISTS `permission` (
  `id`         BIGINT NOT NULL AUTO_INCREMENT,
  `code`       VARCHAR(100) NOT NULL COMMENT 'module:resource:action',
  `name`       VARCHAR(100) NOT NULL COMMENT '权限名称',
  `module`     VARCHAR(50)  DEFAULT NULL COMMENT '模块',
  `action`     VARCHAR(30)  DEFAULT NULL COMMENT '动作',
  `type`       TINYINT DEFAULT 2 COMMENT '1=前台端 2=后台端（用于授权页分组）',
  `sort`       INT DEFAULT 0,
  `created_at` DATETIME DEFAULT NULL,
  `updated_at` DATETIME DEFAULT NULL,
  `deleted`    INT DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_perm_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限目录';

-- 角色-权限映射
CREATE TABLE IF NOT EXISTS `role_permission` (
  `id`           BIGINT NOT NULL AUTO_INCREMENT,
  `role_id`      BIGINT NOT NULL,
  `permission_id` BIGINT NOT NULL,
  `created_at`   DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rp` (`role_id`,`permission_id`),
  KEY `idx_rp_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限映射';
```

### 6.3 `menu` 加端维度（迁移）
```sql
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='menu' AND COLUMN_NAME='type');
SET @sql := IF(@c=0,
  'ALTER TABLE `menu` ADD COLUMN `type` TINYINT NOT NULL DEFAULT 2 COMMENT \'1前台端 2后台端\' AFTER `icon`',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
```

### 6.4 测试数据（需求 6，节选要点）
- **组织层级**：根"某某科技" → 子"研发部"（→"平台组"/"应用组"）、"测试部"、"产品部"。
- **项目**：默认项目（已有）+ "核心交易系统""移动 App""数据中台"（status 1）。
- **用户/角色**：admin（ADMIN，全权限）；dev_zhang（DEVELOPER）；test_li（TESTER）；submit_wang（SUBMITTER）；各 2~3 名。
- **问题**：≥ 12 条，覆盖 status(0~4)、severity(0~3)、标签（如 崩溃/UI/性能/兼容）、分属不同项目；其中构造 3~4 组关联（如 A 前置 B、B 前置 C 的链式依赖；D 前置 E），确保能验证防环（尝试 E 前置 A 应被拒）。
- **菜单配置（按端）**：
  - 后台端(type=2)：概览/问题管理/项目管理/流程监控/系统管理(用户管理,组织管理,菜单管理,角色管理)/流程配置/系统设置（含 `permission` 码）。
  - 前台端(type=1)：工作台/我的问题/提交问题/个人看板。
- **流程配置**：沿用现有 `sys_config` 的 `flow_reopen_enabled`/`flow_reject_enabled`（置 1，开启重开/驳回）。
- **权限目录 + 角色权限**：写入 §5.4 全部权限码及 ADMIN 全量、职能角色最小集。

---

## 7. 待确认问题（Open Questions）

1. **单角色 vs 多角色（需求 5）**：本期是否接受"单角色 + 权限集"方案（推荐，改动最小），还是必须一步到位支持用户多角色？→ 影响是否改动 JWT 与 `AuthService`。
2. **防环存储建模（需求 3）**：接受"仅存前置边、后置由反向推导"的建模吗？还是希望前后置都显式落库（双写、需维护一致性）？→ 影响 `issue_relation` 写入与 UI 编辑逻辑。
3. **菜单动态渲染的"系统管理"子菜单（需求 4）**：动态渲染后，"系统管理"作为父级是否也来自 `menu` 表（parent_id 自引用），即整棵后台树完全数据驱动？→ 影响 `SideMenu` 递归组件与种子数据。
4. **权限变更生效方式（需求 5）**：接受"Redis 缓存 + 变更时失效/提供刷新接口"（推荐），还是每次请求实时查库（简单但稍慢）？
5. **内置角色保护（需求 5）**：ADMIN/SUBMITTER/DEVELOPER/TESTER 是否禁止删除/改码？新建角色是否允许与内置同码？→ 影响 `RoleController` 校验。
6. **前台"个人设置"（需求 2 延伸）**：是否同步补齐前台「个人设置」Dialog（与后台对称）？还是本期仅加「清理缓存」？

---

## 8. 附录：权限码目录建议（供架构师落地）

```
dashboard:view
issue:list  issue:create  issue:update  issue:delete  issue:export
project:list project:create project:update project:delete project:export
user:list user:create user:update user:delete
organization:list organization:create organization:update organization:delete
menu:list menu:create menu:update menu:delete
role:list role:create role:update role:delete role:assign
settings:view settings:update
flow:view flow:config
```

> 说明：本 PRD 聚焦产品层面（目标/故事/需求池/页面功能/权限要点/数据需求/待确认），具体任务分解与类图由架构师在 `incremental-design` 范式下产出。
