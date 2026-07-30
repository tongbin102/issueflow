# issueFlow Phase 2 — QA 验证报告

> QA 工程师：严过关（Edward）
> 验证对象：Phase 2 全部交付物（6 项需求 R1–R6）
> 后端验证方式：静态代码走读 + 契约对齐（本机无 JDK17 / Maven 损坏，无法 `mvn compile`）
> 前端验证方式：静态走读 + `npx vite build` 本地构建
> SQL 验证方式：语法 / 幂等性 / 种子完整性人工审查

---

## 1. 总体结论

**IS_PASS: YES**

6 项需求均有对应且可工作的实现；前端生产构建通过（2297 模块全部编译）；前端 `api/*.js` 与后端 Controller 的 URL/Method/DTO 完全一致；关键算法（BFS 防环、RBAC OR 语义、Redis 缓存失效、内置角色保护）经代码走读确认正确；P0 功能未被破坏（`requireAdmin`→`requirePermission` 过渡完整，无"裸写接口"）。

## 2. 智能路由判定

**NoOne**

- 未发现必须修复后才能交付的源码 Bug（无阻塞性问题清单）。
- 验证方法本身（构建/静态审查）无误，无需 QA 自查修正。
- 仅 1 处非阻塞性功能间隙（`focus` 跳转未接管），列为改进建议，不阻断交付。

---

## 3. 详细检查结果

### 3.1 功能覆盖度（R1–R6）

| 需求 | 实现证据 | 结果 |
|---|---|---|
| **R1** 后台顶栏切换入口收敛 | `layouts/AdminLayout.vue`：顶栏 `topbar-right` 已无 `LayoutSwitchEntry variant="topbar"`；侧栏改为 `<SideMenu :type="2" />`，保留 `variant="sidebar"`；`clearCache` 下拉保留。 | ✅ PASS |
| **R2** 前台缓存清理 | `layouts/UserLayout.vue`：侧栏 `<SideMenu :type="1" />`；头像下拉含 `clearCache`（command=`clearCache`，`localStorage.clear()`+`ElMessage`+600ms `reload`），逻辑与后台一致；前台 `topbar` 的 `LayoutSwitchEntry variant="topbar"` 按设计保留。 | ✅ PASS |
| **R3** 问题关联 | 后端 `IssueController` `/{id}/relations`(GET/PUT)、`/options`(GET)；`IssueRelationService.saveRelations` 整体替换 + BFS 防环；前端 `IssueRelationPanel.vue`（前后置多选、可点击跳转）+ `IssueDetailDrawer.vue` 嵌入；`api/issue.js` 三个方法齐备。 | ✅ PASS |
| **R4** 菜单按端 + 动态渲染 | 后端 `Menu.type`、`MenuService.listByType`/`listSidebarTree`、`MenuController` `/menus?type` 与 `/menus/sidebar`；前端 `MenuManage.vue` 端切换（Radio 前台/后台）、`SideMenu.vue` 递归动态渲染；`api/menu.js` 齐备。 | ✅ PASS |
| **R5** 角色管理 + RBAC | 后端 `RoleController`(CRUD+权限)、`PermissionController`、`PermissionService.requirePermission`、`RoleService`(内置保护/码重复/分配失效)；前端 `RoleManage.vue`、`store/user.js`(permissions+hasPerm)、`utils/permission.js`(v-perm)、`main.js` 注册、`routes.js` 新增 `/admin/system/roles`。 | ✅ PASS |
| **R6** 测试数据 | `scripts/V20250801_issueflow_phase2.sql`：3 新表 + `menu.type` 列 + 32 条权限目录 + 角色权限映射(ADMIN 全量/职能角色最小集) + 按端菜单(含 `/admin/system/roles`) + sys_config + 组织层级 + 3 项目 + 6 用户 + 13 问题 + 链式关联（含防环验证用例）。幂等。 | ✅ PASS |

### 3.2 接口契约一致性（前端 api ↔ 后端 Controller）

| 前端方法 | URL/Method | 后端端点 | 结果 |
|---|---|---|---|
| `role.js` listRoles/createRole/updateRole/deleteRole/getRolePermissions/assignRolePermissions/refreshPermissions | `/roles` GET/POST/PUT/DELETE，`/roles/{id}/permissions` GET/PUT，`/roles/permissions/refresh` POST | `RoleController` 全部对应 | ✅ |
| `permission.js` listPermissions | `/permissions` GET | `PermissionController.list`（登录即可） | ✅ |
| `menu.js` listMenus/getSidebarMenus/createMenu/updateMenu/deleteMenu | `/menus?type`、`/menus/sidebar?type`、CRUD | `MenuController` 全部对应 | ✅ |
| `issue.js` getRelations/saveRelations/listIssueOptions | `/issues/{id}/relations` GET/PUT、`/issues/options?excludeId` | `IssueController` 全部对应（`IssueRelationReq{predecessorIds,successorIds}` 对齐） | ✅ |

> `request.js` baseURL = `/api`，故相对路径 `/roles` 等正确映射为 `/api/roles`。

### 3.3 关键逻辑正确性

- **`PermissionService.requirePermission`**：ADMIN 首判放行 ✅；OR 语义（遍历 `owned.contains(perm)` 等价于 `!Collections.disjoint`）✅；`@PostConstruct init()→refreshAll()` 预热 ✅；`getPermissions` Redis 读→DB 兜底→写回 ✅；`invalidate(roleId)` 删 `perm:role:{id}` ✅；`REDIS_PERM_ROLE_PREFIX="perm:role:"` 与 `Constants` 一致 ✅。
- **`IssueRelationService.saveRelations`**：仅存前置边（`issue_id=A, related_id=P`），后置由反向 `selectIssueIdsByRelatedId` 推导 ✅；`wouldCreateCycle` BFS 与设计 §6 伪代码逐行一致（自环 `A==Y` 直接拒绝；沿后继方向 `related_id=n` 的 `issue_id` 扩展，命中 Y 即环）✅；整体替换前先 `deleteByIssueId`+`deleteByRelatedId` 再批量插 ✅；权限校验（ADMIN 或提交人）✅。
- **`RoleService`**：`create` 校验内置码重复 + 码唯一（`ROLE_CODE_DUPLICATE`）✅；`update` 仅改 name/description，**码不可改**（内置保护等效生效）✅；`delete` 内置角色 `ROLE_BUILTIN_PROTECTED` ✅；`assignPermissions` 先删后插 + `permissionService.invalidate(id)` ✅；`refreshAll` 在创建/删除后调用，保证内存 `roleCode→roleId` 映射即时刷新 ✅。
- **`MenuService`**：`listByType` 按 `type` 过滤 ✅；`listSidebarTree` 按 `type`+`sort,id` 组装树 ✅；`delete` 子节点校验 `NODE_HAS_CHILDREN` ✅；写操作均 `requirePermission` ✅。

### 3.4 文件一致性（对照设计 §8 文件清单）

- 后端新增实体 `IssueRelation`/`Permission`/`RolePermission` 与 Mapper 一一对应，字段与 SQL DDL、自定义 `@Select` 一致 ✅
- `ResultCode` 含 `RELATION_CYCLE`/`ROLE_BUILTIN_PROTECTED`/`ROLE_CODE_DUPLICATE`（另 `NODE_HAS_CHILDREN`/`PERMISSION_DENIED` 等）✅
- `Constants` 含 `BUILTIN_ROLE_CODES`/`REDIS_PERM_ROLE_PREFIX`/`MENU_TYPE_FRONT(1)`/`MENU_TYPE_ADMIN(2)` ✅
- `UserController` 已移除 `GET /roles`（迁移至 `RoleController`，无重复映射）✅
- 前端 `SideMenu.vue`/`IssueRelationPanel.vue`/`RoleManage.vue`/`api/role.js`/`api/permission.js` 均存在；`AdminLayout`/`UserLayout`/`MenuManage`/`IssueDetailDrawer`/`api/menu.js`/`api/issue.js`/`store/user.js`/`utils/permission.js`/`routes.js` 均按要求修改 ✅
- **路由 ↔ 菜单 path 全对应**：`/admin/system/roles` 路由已加，且菜单种子写入 `path='/admin/system/roles'`（type=2）；全部 11 个后台路由 + 4 个前台路由均有对应菜单种子，无遗漏 ✅

### 3.5 前端构建

- 首次 `npx vite build --outDir dist-verify`：2297 模块全部 transform 成功，仅在本机 `safe-delete` 守卫清理**既有** `dist-verify`（65 文件 > 阈值 50）时中断（非代码错误，与设计预警一致）。
- 改用全新目录 `npx vite build --outDir dist-qa-verify`：**✓ built in 13.65s** 成功，产物含 `SideMenu`/`RoleManage`/`MenuManage`/`IssueDetailDrawer`/`AdminLayout`/`UserLayout` 等 Phase 2 组件。
- **结论：前端生产构建通过。** ✅

### 3.6 回归检查（P0 不被破坏）

- `grep requireAdmin|requirePermission`：6 模块（Issue/Project/User/Organization/Menu/SysConfig）已全部从 `requireAdmin` 过渡到 `requirePermission`，**无残留 `requireAdmin`**，且**无既无 `requireAdmin` 也无 `requirePermission` 的裸写接口**（设计 §12.1 风险 2 已规避，ADMIN 始终放行）✅
- 问题/项目/用户/组织/菜单管理路由与页面完好；登录与路由守卫（`meta.roles`）保持 P0 行为 ✅
- 权限加载链路闭环：`AuthService` → `LoginVO.userInfo.roleId`（`UserVO.roleId` 已填充）→ 前端 `store.loadPermissions()` → `GET /roles/{id}/permissions` ✅

---

## 4. 阻塞性问题清单

**无。** 未发现必须修复才能交付的源码 Bug。

---

## 5. 非阻塞性建议（改进项 / 一致性清理）

1. **【R3 体验间隙】关联问题点击跳转未自动打开详情（低-中）**
   - 现象：`IssueRelationPanel.goIssue` 以 `router.push({ path:'/admin/issues', query:{ focus: ref.id } })` 跳转，但 `AdminIssueList.vue` 与 `IssueTable.vue` 均未消费 `route.query.focus`，落地后仅停留在列表页，需手动再点开详情。
   - 影响：R3 用户故事"点击关联问题直接跳转其详情"未 100% 达成（核心查看/编辑/防环功能正常）。
   - 建议：在 `AdminIssueList` 的 `onMounted`/`watch(route.query.focus)` 中，定位对应行并 `openDetail(row)` 自动打开抽屉；或改为 `query:{ issueId }` 与既有列表详情逻辑统一。属 P1 范畴，不阻断本期交付。

2. **【一致性】`permission` 表 DDL 含 `deleted` 列，但 `Permission` 实体未建模该字段（低）**
   - 设计 §4.1 明确 Permission"无逻辑删除"，但 `V20250801…sql` §2.2 DDL 仍写了 `deleted INT DEFAULT 0`；`RolePermissionMapper.selectPermissionCodesByRoleId` 的 JOIN 也带 `p.deleted=0`。
   - 现状无功能影响（无删除权限的 API，种子恒为 `deleted=0`），但实体与表结构存在轻微不一致。
   - 建议：二选一统一——要么实体加 `@TableLogic deleted` 并让 DDL 与逻辑删除一致，要么 DDL 去掉该列（与"无逻辑删除"约定一致）。纯清理项。

3. **【文档】SQL 关联种子注释措辞与数据方向相反（极低）**
   - 第 331 行注释"0001 前置 0002"实际对应 `edge(issue_id=0001, related_id=0002)` 即"0002 是 0001 的前置"；而末段防环验证注释（3→2→1）与数据/算法一致。
   - 仅为注释误导，数据本身自洽、防环可验证。建议修正注释文字，避免后续维护误解。

4. **【优化】`v-perm` 按钮级权限指令已注册但本期页面使用较少（P1）**
   - 设计将按钮级权限控制列为 P1；当前 `RoleManage`/`MenuManage` 等仍主要依赖路由级 `meta.roles:['ADMIN']` 守卫。指令可用，建议后续在管理页按钮上按 `v-perm` 细化（如非 ADMIN 职能角色进入 admin 页的路由级放行一并规划）。

---

## 6. 交付判定说明（针对 IS_PASS: YES）

- 全部 6 项需求具备可工作实现，构建通过，契约对齐，核心算法经走读确认正确，P0 未回归。
- 唯一功能性间隙（建议 1）不破坏任何核心能力，且设计 §7.4 允许"跳转列表 OR 调起详情"两种实现，故判定为"非阻塞"，交付结论为 **IS_PASS: YES**。
- 若产品方将"点击关联问题必须自动打开详情"列为 R3 强制验收点，则需工程师补 1 处前端跳转接管（建议 1），属一轮快速修复，不影响整体通过结论。
