# issueFlow Phase 5 — QA 验证报告

> QA 工程师：严过关（Edward）
> 验证对象：Phase 5「管理后台界面优化」R1–R7 全部交付物
> 后端验证方式：**静态代码审查**（本机无 JDK17，无法 `mvn compile/test`）+ 契约对齐
> 前端验证方式：**实测 `npm run build`** + 静态抽查
> SQL 验证方式：DDL/实体字段对齐 + 幂等性 + 种子父子关系人工审查
> 部署后可执行冒烟清单：`tests/phase5-smoke.md`

---

## 1. 总体结论

**IS_PASS: YES**　　**智能路由判定：NoOne**

- 未发现**阻塞交付的源码 Bug**：R1–R7 七项需求均有正确、可工作的实现。
- 前端生产构建通过（`✓ built in 28.33s`，0 error）。
- 前端 `api/flow.js` 与 `FlowDefinitionController` 路径 / 方法 / 载荷**逐条一致**。
- 5 项非阻塞改进建议（P2/P3）列于第 5 节，不阻断本期交付。

---

## 2. 分项检查结果

### 2.1 后端静态审查

| # | 检查项 | 结论 | 证据 |
|---|---|---|---|
| a1 | `StateMachine.reload()` 库空回退 | ✅ PASS | `StateMachine.java:75-79` `built.isEmpty()` → `DEFAULT_TRANSITIONS`；节点/流转均按 `enabled=1 AND deleted=0` 过滤（L56-62），孤儿边（node 已删）在 L68-70 跳过，不会构造出错误规则 |
| a2 | DEFAULT 6 条与库种子一一对齐 | ✅ PASS | `StateMachine.java:133-146` vs `FlowDefinitionService.resetDefault` 种子 `:247-254` vs SQL `V20260802:80-114`，三处 **from/to/actionCode/allowRoles/remarkRequired/configKey 完全一致**：0→1 CLAIM、1→2 SUBMIT_FIX、2→3 VERIFY_PASS、2→1 VERIFY_REJECT(必填备注+reject开关)、3→4 CLOSE、4→0 REOPEN(reopen开关) |
| a3 | `isAllowed` 拒绝非法流转（如 0→3） | ✅ PASS | `StateMachine.java:93-105`：`from==to` 直接 false；`find()` 未命中返回 null → false；再叠加开关与角色判定，0→3 无规则必然被拒 |
| a4 | 双开关关闭时驳回/重开被拦 | ✅ PASS | `StateMachine.java:101-103` `configKey != null && !sysConfigService.isEnabled(configKey)` → false；`reopen()` 另有独立开关校验 `IssueFlowService.java:79-81` |
| b | `changeStatus` 调用链 | ✅ PASS | `IssueFlowService.java:53`(isAllowed) → `:57`(isRemarkRequired，缺备注抛 VALID_ERROR「该流转必须填写备注」) → `:66`(getActionCode 写历史)。顺序正确，先鉴权后校验 |
| c1 | 删节点两道防线顺序 | ✅ PASS | `FlowDefinitionService.java:125-139`：先查流转引用 → **40047**；再查存量问题 → **40048**。顺序与设计一致 |
| c2 | 删除走物理 DELETE | ✅ PASS | `:141` `physicalDeleteById`（`FlowNodeMapper.java:18` `DELETE FROM flow_node WHERE id=#{id}`），绕开 `@TableLogic`，与 `uk_flow_node_status`（含逻辑删除行）唯一索引约束匹配；流转同理 `:210` |
| c3 | `resetDefault` 物理清空→重灌→reload | ✅ PASS | `:220-221` 先删子表 `flow_transition` 再删父表 `flow_node`（顺序安全）→ `:231-267` 重灌 5 节点 + 6 流转（节点 id 用 `idByStatus` 回填，无硬编码 id）→ `:268` `reload()` |
| c4 | `updateNodePositions` 空列表校验 | ✅ PASS | 校验在 DTO 层：`FlowNodePositionReq.java:21` `@NotEmpty`、`:33` `@NotNull(id)`，Controller `:52` `@Valid` 生效。服务层 `:152-155` 对不存在节点 `continue` 跳过，不会整批失败 |
| c5 | 写操作后 reload | ✅ PASS | createNode/updateNode/deleteNode/create·update·deleteTransition/resetDefault 共 7 处均调用 `stateMachine.reload()`；`updateNodePositions` 有意不 reload（仅坐标，注释已说明）✔ |
| d1 | `resetData` 删表顺序（先子后父） | ✅ PASS | `SystemDataService.java:82-91`：issue_attachment → issue_history → issue_relation → issue → tag → module_dependency → module → project → organization → user。且全库**无 FOREIGN KEY 约束**（grep 全部 scripts 无 `FOREIGN KEY/REFERENCES`），外键安全无风险 |
| d2 | 保留表清单 | ✅ PASS | 只 DELETE 上述 10 张业务表；`role`/`permission`/`role_permission`/`menu`/`sys_config`/`flow_node`/`flow_transition` **均未出现在删除列表**；admin 账号由 `clearUsersExceptAdmin`（`username <> 'admin'`）保留，并 `resetAdminLeader()` 置空悬挂的 leader_id（`SystemDataMapper.java:51-56`）✔ |
| d3 | 单事务 | ✅ PASS | `:80-94` 用 `TransactionTemplate.execute` 包住全部 DELETE（未用 `@Transactional`，因为后续 `ALTER TABLE` 是 DDL 隐式提交，此写法**更正确**）；AUTO_INCREMENT 重置在事务外、失败仅告警 `:101-107` |
| d4 | 附件磁盘清理 | ✅ PASS | `:110` → `deleteAttachmentFiles()` `:128-146`：`Files.walk` + `reverseOrder()` 逆序删（先文件后目录），`filter(p -> !p.equals(base))` 保留根目录，异常仅 warn 不影响业务 |
| d5 | Redis perm 缓存失效 | ✅ PASS | `:113-120` 遍历全部 role 调 `permissionService.invalidate(roleId)`，try/catch 兜底 |
| d6 | 权限双保险 | ✅ PASS | `:71-74` 角色码必须 `ADMIN` **且** `requirePermission("system:reset")`；`:75-77` 确认文本必须 `RESET`（trim 后精确匹配）；`:67-69` 另有 yml 总开关 `system.data-reset.enabled` |
| e1 | 组织防成环 | ✅ PASS | `OrganizationService.java:170-199`：`newParentId==null/0` 放行 → 等于自身抛 **40052** → 构建 childrenMap 后 **BFS 收集全部子孙**（`descendants.add` 去重，天然防御脏数据成环导致的死循环）→ 命中抛 40052。仅在 `req.getParentId()!=null` 时校验（`:115-118`），语义正确 |
| e2 | 用户 leaderId 防自指 | ✅ PASS（有间隙，见 5.2） | `UserService.java:151-153` 更新时 `leaderId==id` 抛 **40045**；`:126-129` 新建后若自指则置空修正 |

### 2.2 前端实测

| 检查项 | 结论 | 证据 |
|---|---|---|
| `npm run build` | ✅ **PASS** | `✓ built in 28.33s`，0 error / 0 warning（除常规 chunk size 提示）。产出 40+ chunk，含 `FlowConfig-BV19C8Z3.js 15.67 kB`、`OrganizationManage-WghCOE57.js 11.18 kB`、`SystemSettings-D30V25GT.js 3.94 kB`、`FormDrawer-xprYFBX-.js 1.52 kB`。验证目录已移出仓库 |
| FlowConfig 含 echarts graph | ✅ PASS | `FlowConfig.vue:219` `import * as echarts`、`:359` `type: 'graph'`、`:376` `echarts.init`；`layout:'none'` + `draggable` + `edgeSymbol:['none','arrow']`，反向边 `curveness:0.25` 防重叠，禁用流转灰色虚线 |
| FormDrawer 被 6 页引用 | ✅ PASS | UserManage`:54`、OrganizationManage`:122`、ProjectManage`:84`、MenuManage`:41`、RoleManage`:42/:70`（两个）、FlowConfig`:105/:151`（节点+流转两个） |
| 6 个目标文件无 el-dialog 残留 | ✅ PASS | 对 6 文件 grep `el-dialog` → **0 命中** |
| R1「返回前台」底部固定 | ✅ PASS | `AdminLayout.vue:20` `<LayoutSwitchEntry variant="sidebar" />`、`:187` 底部预留高度 CSS；`LayoutSwitchEntry.vue:73` label 后台侧为「返回前台」 |
| api/flow.js ↔ Controller 契约 | ✅ PASS | 9 个方法逐条对齐：`GET /flow/definition/graph`、`POST/PUT/DELETE /nodes[/{id}]`、`PUT /nodes/positions`（载荷 `{positions:[{id,posX,posY}]}` 与 DTO 完全一致）、`POST/PUT/DELETE /transitions[/{id}]`、`POST /reset-default`。`/nodes/positions` 字面量路径优先于 `/nodes/{id}` 模板，Spring 匹配无歧义 |
| 新增路由存在 | ✅ PASS | `routes.js:93-97` `/admin/modules`(ModuleManage)、`:134-139` `/admin/system/settings`(SystemSettings) |

### 2.3 SQL 审查（`scripts/V20260802_issueflow_phase5.sql`）

| 检查项 | 结论 | 证据 |
|---|---|---|
| `flow_node` DDL ↔ 实体 | ✅ PASS | L13-30 字段 id/name/code/status_code/node_type/color/pos_x/pos_y/sort/description/enabled/created_at/updated_at/deleted，与 `FlowNode.java` 及 `FlowNodeVO` 全字段对应；`uk_flow_node_status(status_code)` 唯一索引与服务层「必须物理删除」的设计自洽 |
| `flow_transition` DDL ↔ 实体 | ✅ PASS | L35-52 含 from_node_id/to_node_id/action_code/action_name/allow_roles/remark_required/config_key/enabled/sort；`uk_flow_transition(from,to)` + `idx_flow_transition_to` |
| 种子幂等 | ✅ PASS | 节点 5 条 `INSERT...SELECT...WHERE NOT EXISTS`（L57-75）；流转 6 条用 `FROM flow_node fn, flow_node tn WHERE fn.status_code=x AND tn.status_code=y AND NOT EXISTS(...)` 动态解析 id（L80-114），**不硬编码 id**，可重复执行 |
| 加列幂等 | ✅ PASS | organization 的 code/leader_id/status/description 与 user.leader_id 均用 `information_schema.COLUMNS` 计数 + `PREPARE/EXECUTE` 动态 DDL（L119-146）；唯一索引 `uk_org_code` 用 `information_schema.STATISTICS` 判重（L137-139），且**先回填 code 再建索引**（L136）顺序正确 |
| 菜单父子关系 | ✅ PASS | L152 旧「项目管理」改名「项目配置」；L154-156 新建顶级「项目管理」`/admin/project`(parent_id=0,sort=3)；L158-162 把 `/admin/projects` 挂到新父下（派生表 `_p` 规避 MySQL 1093 限制，`parent_id` 列 NOT NULL DEFAULT 0 故 `<>` 比较不会因 NULL 失效）；L164-168「模块配置」`/admin/modules` 挂同一父；L187-191「系统设置」挂 `/admin/system` 下 |
| 权限种子 | ✅ PASS | L173-175 `system:reset` 幂等插入（permission 表列 code/name/module/action/type/sort 与 phase2 DDL 一致）；L177-182 授予 ADMIN 且 `NOT EXISTS` 去重。`flow:view`/`flow:config` 已由 phase2 L103-104 提供，本期无需重复 |
| 可重复执行 | ✅ PASS | 全部语句为 `CREATE TABLE IF NOT EXISTS` / 动态 DDL / `INSERT...WHERE NOT EXISTS` / 幂等 `UPDATE`；`SELECT ... WHERE NOT EXISTS`（无 FROM）写法与 p0/phase2 已上线脚本一致，MySQL 8 支持 |

---

## 3. 前端构建结果

```
✓ built in 28.33s
dist-verify-qa/assets/FlowConfig-BV19C8Z3.js        15.67 kB │ gzip:  5.57 kB
dist-verify-qa/assets/OrganizationManage-WghCOE57.js 11.18 kB │ gzip:  4.38 kB
dist-verify-qa/assets/SystemSettings-D30V25GT.js      3.94 kB │ gzip:  2.28 kB
dist-verify-qa/assets/FormDrawer-xprYFBX-.js          1.52 kB │ gzip:  0.83 kB
... 共 40+ chunk，0 error
```

验证产物已移出仓库（移至系统 TEMP）。

---

## 4. 交付物

- ✅ `tests/phase5-smoke.md` —— 部署后可直接执行的 curl 冒烟清单（9 节 30+ 用例，含全部错误码断言与破坏性操作的备份提示）。
- ✅ `tests/QA_phase5_report.md` —— 本报告。

---

## 5. 非阻塞改进建议（不修，仅记录）

### 5.1 【P2】`reopen()` 绕过了数据驱动状态机
`IssueFlowService.java:75-96` 的 `/reopen` 端点硬编码「ADMIN + reopen 开关 + 4→0」，**不经过 `StateMachine.isAllowed`**。管理员若在新的流程配置页删除或改造 `4→0 REOPEN` 流转，`/reopen` 仍可执行，与 R2「读库驱动状态机」的设计目标不一致。
建议：`reopen()` 内改为 `stateMachine.isAllowed(CLOSED, OPEN, roleCode)` + `getActionCode` 取动作码，保留「仅已关闭可重开」的业务前置判断。

### 5.2 【P3】用户上级领导仅防「直接自指」，未防多级环
`UserService.java:151-153` 只拦 `leaderId == 自身 id`；A→B、B→A 这类两级环可以建立，而 `ResultCode.USER_LEADER_CYCLE(40045)` 的文案是「不能为自己或**形成循环**」，实现与文案不符。
当前无任何代码递归遍历 leader 链，**不会造成死循环**，故风险低。建议参考 `OrganizationService.assertNotSelfOrDescendant` 补一次有界链路上溯（深度上限 20）。

### 5.3 【P3】菜单分组子项被权限全部过滤时会退化为可点击项
`SideMenu.vue:75-77 / 93` 先按权限过滤 children，再用过滤后的 children 判断 `hasChildren`。若某用户对「项目管理」下 `project:list` 与 `project:update` 都无权限，该分组会退化成 `el-menu-item index="/admin/project"`，而 `routes.js` 中**没有 `/admin/project` 路由**，点击落到 404。
`/admin` 整体仅 ADMIN 可进且 ADMIN 拥有全量权限，实际不可达。建议：过滤后 children 为空时整体隐藏该分组。

### 5.4 【P3】`SystemDataMapper.selectAllAttachmentPaths()` 为死代码
`SystemDataMapper.java:20-21` 声明但服务层从未调用；附件清理改为遍历 `app.attachment-base-path` 目录。若历史 `issue_attachment.file_path` 存在**指向该根目录之外**的绝对路径，这些文件不会被清理。当前上传统一落在 base-path 下，无实际影响。

### 5.5 【P3】其他
- `OrganizationService.create/update/delete` 未加 `@Transactional`（均为单条写，风险极低）。
- `FlowConfig.vue` `saveLayout` 的 `catch (e) {}` 为空块，依赖 axios 拦截器提示错误，建议至少留注释。
- `.gitignore` 未包含 `dist-verify-qa/`（也缺 `dist-verify4/`、`dist-verify5-final/`、`dist-verify5-v2/`）。建议统一改为 `dist*/`。

---

## 6. 部署注意事项（务必按序）

1. **先执行** `scripts/V20260802_issueflow_phase5.sql`，**再重启后端**。
   `StateMachine.init()` 带 `@PostConstruct`（`StateMachine.java:47-50`）会在启动时读 `flow_node` / `flow_transition`；若表尚未创建，**Spring 上下文启动会直接失败**。
2. 数据初始化（R7）为破坏性操作，生产/演示库执行前先 `mysqldump` 备份。
3. 重置后请立刻复验「重新登录成功 + 流程图仍为 5 节点 6 流转 + 菜单树完整」（见冒烟清单第 6 节）。

---

## 7. 遗留问题（Known Issues）

**无阻塞性遗留问题。** 以下 3 项属于**静态审查无法覆盖、必须部署后人工验证**的交互点，已写入冒烟清单第 9 节：

1. echarts 节点拖拽 `mouseup + convertFromPixel` 的像素→图坐标换算精度（`FlowConfig.vue:373-388`），需实测「拖拽→保存→刷新坐标保持」。
2. `resetData` 事务外的 `ALTER TABLE ... AUTO_INCREMENT = 1` 在实际 MySQL8 权限下能否成功（失败仅告警，不影响清库结果）。
3. 附件磁盘目录在容器内的实际挂载路径与 `app.attachment-base-path` 是否一致。
</content>
</invoke>
