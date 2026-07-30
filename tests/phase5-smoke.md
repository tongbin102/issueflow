# issueFlow Phase 5 — 部署后冒烟清单（curl 可执行）

> QA：严过关（Edward）　适用版本：Phase 5（R1–R7）
> 前置：`scripts/V20260802_issueflow_phase5.sql` 已执行；后端已重启（`StateMachine` 在 `@PostConstruct` 读库重建规则缓存，**必须先跑 SQL 再启动/重启后端**）。
> 约定：`BASE` 为服务地址；所有断言看返回体 `code` 字段（`Result.code`），`0`/`200` 为成功（以本项目 `Result` 约定为准），其余为业务错误码。

```bash
# ---------- 0. 环境变量 ----------
export BASE=http://<server-ip>:8080
```

---

## 1. 登录取 token

```bash
export TOKEN=$(curl -s -X POST "$BASE/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"<admin密码>"}' \
  | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
echo "TOKEN=$TOKEN"
export H="Authorization: Bearer $TOKEN"
```

**期望**：`TOKEN` 非空。

---

## 2. R2 流程配置（/api/flow/definition）

### 2.1 查询流程图 —— 基线 5 节点 + 6 流转

```bash
curl -s "$BASE/api/flow/definition/graph" -H "$H" | python -m json.tool
```

**期望**：`data.nodes` 5 条（statusCode 0/1/2/3/4，含 posX/posY/color）；`data.transitions` 6 条：
`0→1 CLAIM` / `1→2 SUBMIT_FIX` / `2→3 VERIFY_PASS` / `2→1 VERIFY_REJECT(remarkRequired=1, configKey=flow_reject_enabled)` / `3→4 CLOSE` / `4→0 REOPEN(configKey=flow_reopen_enabled)`。
> 记录节点 id 备用：`export N0=<statusCode0的id> N1=... N4=...`

### 2.2 非法 statusCode=5 → **40049**

```bash
curl -s -X POST "$BASE/api/flow/definition/nodes" -H "$H" -H 'Content-Type: application/json' \
  -d '{"name":"非法节点","statusCode":5,"nodeType":2}'
```

**期望**：`code=40049`，message「流程节点状态码非法（仅支持 0-4）」。

### 2.3 重复 statusCode=0 → **40050**

```bash
curl -s -X POST "$BASE/api/flow/definition/nodes" -H "$H" -H 'Content-Type: application/json' \
  -d '{"name":"重复待处理","statusCode":0,"nodeType":1}'
```

**期望**：`code=40050`「该状态已被其他流程节点占用」。

### 2.4 重复流转 0→1 → **40051**

```bash
curl -s -X POST "$BASE/api/flow/definition/transitions" -H "$H" -H 'Content-Type: application/json' \
  -d "{\"fromNodeId\":$N0,\"toNodeId\":$N1,\"actionCode\":\"CLAIM\",\"actionName\":\"认领\",\"allowRoles\":\"DEVELOPER,ADMIN\"}"
```

**期望**：`code=40051`「相同源状态与目标状态的流转规则已存在」。

### 2.5 源=目标 → 参数错误

```bash
curl -s -X POST "$BASE/api/flow/definition/transitions" -H "$H" -H 'Content-Type: application/json' \
  -d "{\"fromNodeId\":$N0,\"toNodeId\":$N0,\"actionCode\":\"X\"}"
```

**期望**：`VALID_ERROR`，message「源节点与目标节点不能相同」。

### 2.6 删除被流转引用的节点 → **40047**

```bash
curl -s -X DELETE "$BASE/api/flow/definition/nodes/$N0" -H "$H"
```

**期望**：`code=40047`「该流程节点仍被流转规则引用，无法删除」。
> ⚠️ 两道防线有**先后顺序**：先判 40047（流转引用），后判 40048（存量问题）。因此基线数据下永远先命中 40047。

### 2.7 【40048 验证，需构造】删除有存量问题的节点

前置：先删掉某状态节点的全部关联流转，再删节点；且该状态下存在问题。
建议在**已有问题数据**的环境上，用状态 3（验证通过）构造：

```bash
# a) 删掉 2→3、3→4 两条流转（记录 id，验证后用 reset-default 恢复）
curl -s -X DELETE "$BASE/api/flow/definition/transitions/<t_2_3_id>" -H "$H"
curl -s -X DELETE "$BASE/api/flow/definition/transitions/<t_3_4_id>" -H "$H"
# b) 再删节点 3（此时该状态下若有问题）
curl -s -X DELETE "$BASE/api/flow/definition/nodes/$N3" -H "$H"
```

**期望**：`code=40048`「该状态下仍有存量问题，无法删除节点」。
（若该状态无问题则会删除成功——属正确行为，随后 2.9 reset-default 恢复。）

### 2.8 保存节点坐标

```bash
# 正常保存
curl -s -X PUT "$BASE/api/flow/definition/nodes/positions" -H "$H" -H 'Content-Type: application/json' \
  -d "{\"positions\":[{\"id\":$N0,\"posX\":150,\"posY\":100},{\"id\":$N1,\"posX\":350,\"posY\":100}]}"
# 空列表
curl -s -X PUT "$BASE/api/flow/definition/nodes/positions" -H "$H" -H 'Content-Type: application/json' \
  -d '{"positions":[]}'
```

**期望**：第一条成功；再 `GET /graph` 确认 posX=150/350 已落库。
第二条被 `@NotEmpty` 拦截，返回参数校验失败「坐标列表不能为空」。

### 2.9 恢复默认流程

```bash
curl -s -X POST "$BASE/api/flow/definition/reset-default" -H "$H" | python -m json.tool
```

**期望**：返回全量 graph，恢复 5 节点 + 6 流转，坐标回到 120/320/520/720/920 × 80；节点 **id 会变**（物理清空后重灌），需重新记录 `$N0..$N4`。

### 2.10 状态机生效回归（配置即时生效）

```bash
# 取一条 status=0 的问题 id
curl -s "$BASE/api/issues?page=1&size=5&status=0" -H "$H"
# 合法流转 0→1（角色 ADMIN 允许）
curl -s -X PUT "$BASE/api/issues/<issueId>/status" -H "$H" -H 'Content-Type: application/json' \
  -d '{"toStatus":1,"remark":"冒烟-认领"}'
# 非法流转 0→3（无此规则）
curl -s -X PUT "$BASE/api/issues/<issueId2>/status" -H "$H" -H 'Content-Type: application/json' \
  -d '{"toStatus":3}'
# 必填备注：2→1 驳回不带 remark
curl -s -X PUT "$BASE/api/issues/<issueId状态2>/status" -H "$H" -H 'Content-Type: application/json' \
  -d '{"toStatus":1}'
```

**期望**：
- 0→1 成功，`data.status=1`，`issue_history` 新增一条 `action_code=CLAIM`；
- 0→3 返回 `STATUS_TRANSITION_DENIED`；
- 2→1 无备注返回 `VALID_ERROR`「该流转必须填写备注」，补 `remark` 后成功。

### 2.11 双开关拦截

```bash
# 关闭驳回 & 重开
curl -s -X PUT "$BASE/api/flow/config" -H "$H" -H 'Content-Type: application/json' \
  -d '{"rejectEnabled":false,"reopenEnabled":false}'
# 再尝试 2→1 驳回（带备注）
curl -s -X PUT "$BASE/api/issues/<issueId状态2>/status" -H "$H" -H 'Content-Type: application/json' \
  -d '{"toStatus":1,"remark":"应被拦截"}'
# 再尝试 4→0 重开
curl -s -X POST "$BASE/api/issues/<issueId状态4>/reopen" -H "$H" -H 'Content-Type: application/json' -d '{}'
# 恢复开关
curl -s -X PUT "$BASE/api/flow/config" -H "$H" -H 'Content-Type: application/json' \
  -d '{"rejectEnabled":true,"reopenEnabled":true}'
```

**期望**：关闭后驳回返回 `STATUS_TRANSITION_DENIED`；重开返回「重开功能未启用」；恢复后两者可用。

---

## 3. R4 组织管理（防成环 40052）

```bash
# 组织树
curl -s "$BASE/api/organizations/tree" -H "$H" | python -m json.tool
# 上级设为自己
curl -s -X PUT "$BASE/api/organizations/<orgId>" -H "$H" -H 'Content-Type: application/json' \
  -d '{"name":"研发部","parentId":<同一个orgId>}'
# 上级设为自己的子孙
curl -s -X PUT "$BASE/api/organizations/<父orgId>" -H "$H" -H 'Content-Type: application/json' \
  -d '{"name":"研发部","parentId":<其子orgId>}'
# 编码重复
curl -s -X POST "$BASE/api/organizations" -H "$H" -H 'Content-Type: application/json' \
  -d '{"name":"重复编码组织","code":"ORG001","parentId":0}'
```

**期望**：两条防环均返回 `40052`「上级组织不能为自身或其子孙组织」；重复 code 返回 `40044`。

---

## 4. R5 用户上级领导（40045）

```bash
# leaderId 指向自己
curl -s -X PUT "$BASE/api/users/<userId>" -H "$H" -H 'Content-Type: application/json' \
  -d '{"username":"dev01","roleId":3,"leaderId":<同一个userId>}'
# 正常设置上级
curl -s -X PUT "$BASE/api/users/<userId>" -H "$H" -H 'Content-Type: application/json' \
  -d '{"username":"dev01","roleId":3,"leaderId":1}'
curl -s "$BASE/api/users?page=1&size=10" -H "$H"
```

**期望**：第一条 `40045`「上级领导不能为自己或形成循环」；第二条成功且列表 `leaderName` 正确回填。

---

## 5. R6 菜单（DB 驱动）

```bash
curl -s "$BASE/api/menus/sidebar?type=2" -H "$H" | python -m json.tool
```

**期望**（后台侧栏树）：
- 顶级「项目管理」(`/admin/project`, permission=null) 下含两个子项：**项目配置**(`/admin/projects`, `project:list`) 与 **模块配置**(`/admin/modules`, `project:update`)；
- 「系统管理」(`/admin/system`) 下含 组织管理/菜单管理/用户管理/角色管理/**系统设置**(`/admin/system/settings`, `system:reset`)；
- 原顶级「项目管理」已改名「项目配置」且 `parent_id` 已指向新分组（不再出现在顶级）。

浏览器验证：`/admin/projects`、`/admin/modules`、`/admin/system/settings` 均可正常打开（路由已存在），侧栏「项目管理」为可展开分组（不可点击跳转）。

---

## 6. R7 数据初始化（/api/system/data/reset）

> ⚠️ **破坏性操作，仅在可重置的测试库执行**。执行前建议 `mysqldump` 备份。

```bash
# 6.1 无权限账号（非 ADMIN，如 dev01）应被拒绝
export TK2=$(curl -s -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"dev01","password":"<密码>"}' | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
curl -s -X POST "$BASE/api/system/data/reset" -H "Authorization: Bearer $TK2" \
  -H 'Content-Type: application/json' -d '{"confirmText":"RESET"}'

# 6.2 未登录应被拒绝
curl -s -o /dev/null -w "%{http_code}\n" -X POST "$BASE/api/system/data/reset" \
  -H 'Content-Type: application/json' -d '{"confirmText":"RESET"}'

# 6.3 ADMIN 但确认文本错误
curl -s -X POST "$BASE/api/system/data/reset" -H "$H" -H 'Content-Type: application/json' \
  -d '{"confirmText":"reset123"}'

# 6.4 ADMIN + 正确确认文本
curl -s -X POST "$BASE/api/system/data/reset" -H "$H" -H 'Content-Type: application/json' \
  -d '{"confirmText":"RESET"}' | python -m json.tool
```

**期望**：
- 6.1 `PERMISSION_DENIED`（403 业务码）；
- 6.2 401；
- 6.3 `VALID_ERROR`「确认文本不正确，请输入 RESET」；
- 6.4 成功，返回 `Map<表名,条数>`，键顺序为
  `issue_attachment → issue_history → issue_relation → issue → tag → module_dependency → module → project → organization → user`。

**重置后必须复验（保留项）**：

```bash
curl -s "$BASE/api/flow/definition/graph" -H "$H"        # 仍为 5 节点 + 6 流转（flow 表保留）
curl -s "$BASE/api/menus/sidebar?type=2" -H "$H"          # 菜单树完整（menu 保留）
curl -s "$BASE/api/roles" -H "$H"                         # 角色/权限保留
curl -s "$BASE/api/flow/config" -H "$H"                   # sys_config 双开关保留
curl -s "$BASE/api/users?page=1&size=10" -H "$H"          # 仅剩 admin，且 leaderId 为 null
curl -s "$BASE/api/issues?page=1&size=10" -H "$H"         # total=0
curl -s "$BASE/api/projects?page=1&size=10" -H "$H"       # total=0
curl -s "$BASE/api/organizations/tree" -H "$H"            # 空
```

**并须人工确认**：
1. **重新登录仍然成功**（admin 未被删、token 仍可签发）；
2. 附件目录（`app.attachment-base-path`）下文件已清空、根目录仍在；
3. 新建一个问题，`issue_no` / `id` 从 1 重新开始（AUTO_INCREMENT 已重置）；
4. 角色权限 Redis 缓存（`perm:role:*`）已失效，权限功能正常。

---

## 7. R1 导航（浏览器）

- 进入任意 `/admin/*` 页面，侧栏**底部固定**显示「返回前台」，点击跳转前台且菜单末项不被遮挡；
- 前台侧栏底部显示「管理后台」（仅 ADMIN 可见）。

---

## 8. R3 抽屉表单（浏览器）

逐页确认新增/编辑均为**右侧抽屉**（无弹窗）：
`用户管理` / `组织管理` / `项目配置` / `菜单管理` / `角色管理` / `流程配置`（节点 + 流转两个抽屉）。

## 9. 流程图画布（浏览器，必须人工验证）

1. `/admin/flow-config` 画布渲染 5 节点 + 6 条带箭头连线，`2→1` 与 `1→2` 反向边呈弧形不重叠；
2. 关闭 `flow_reject_enabled` 后，对应连线变**灰色虚线**；
3. 拖拽节点 → 「保存布局」按钮可用 → 保存成功 → **刷新页面坐标保持**（验证 `mouseup + convertFromPixel` 换算正确性，这是本期唯一无法静态确认的交互点）；
4. 「恢复默认流程」二次确认后画布回到初始布局。

---

## 附：Phase 5 错误码速查

| 码 | 含义 |
|---|---|
| 40044 | 组织编码已存在 |
| 40045 | 上级领导不能为自己或形成循环 |
| 40047 | 该流程节点仍被流转规则引用，无法删除 |
| 40048 | 该状态下仍有存量问题，无法删除节点 |
| 40049 | 流程节点状态码非法（仅支持 0-4） |
| 40050 | 该状态已被其他流程节点占用 |
| 40051 | 相同源状态与目标状态的流转规则已存在 |
| 40052 | 上级组织不能为自身或其子孙组织 |
</content>
</invoke>
