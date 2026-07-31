# issueFlow Phase 6 部署后冒烟清单

> 适用环境：部署完成（已执行 `scripts/V20260803_issueflow_phase6.sql` 并重启后端）后执行。
> 变量约定：`BASE=http://<host>:<port>`（后端网关地址）；`TOKEN` 为登录返回的 JWT。
> 所有接口统一返回 `{code, message, data}`，`code=200` 为成功（下述断言以此为准）。
> 前端交互项无法 curl，标注【手动】。

---

## 0. 登录（前置，取 TOKEN）

```bash
curl -s -X POST "$BASE/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"<管理员密码>"}'
# 断言：code=200，data.token 非空
# 导出：TOKEN=<data.token>
```

普通提交者账号（验证权限阻断用）：

```bash
curl -s -X POST "$BASE/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"<submitter用户>","password":"<密码>"}'
# 导出：TOKEN_SUBMITTER=<data.token>
```

---

## 1. R10 网站设置

### 1.1 GET 公开（不带 token）

```bash
curl -s "$BASE/api/site/config"
# 断言：code=200；data 恰好 7 键：
#   site.name / site.short_name / site.subtitle / site.default_theme
#   site.default_locale / site.copyright / site.icp
# 默认值：site.name=issueFlow, site.default_theme=light, site.default_locale=zh-CN
```

### 1.2 PUT 需鉴权（无 token → 401/403）

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X PUT "$BASE/api/admin/site/config" \
  -H "Content-Type: application/json" \
  -d '{"name":"x","shortName":"x","defaultTheme":"light","defaultLocale":"zh-CN"}'
# 断言：HTTP 401 或 403（匿名不可写）
```

### 1.3 PUT 管理员正常保存（site:config:update）

```bash
curl -s -X PUT "$BASE/api/admin/site/config" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"issueFlow","shortName":"IF","subtitle":"问题跟踪与流程管理平台","defaultTheme":"blue","defaultLocale":"zh-CN","copyright":"(c) 2026 issueFlow","icp":""}'
# 断言：code=200；复查 GET /api/site/config 中 site.default_theme=blue
# 回滚：再 PUT 一次把 defaultTheme 改回 light
```

### 1.4 PUT 枚举校验

```bash
curl -s -X PUT "$BASE/api/admin/site/config" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"issueFlow","shortName":"IF","defaultTheme":"pink","defaultLocale":"zh-CN"}'
# 断言：code!=200，提示「默认主题仅支持 light/dark/blue/green」
```

---

## 2. R4 问题类型 CRUD

### 2.1 管理列表（含停用项 + 引用计数 issueCount）

```bash
curl -s "$BASE/api/issue-types" -H "Authorization: Bearer $TOKEN"
# 断言：code=200；data 含 6 条种子（BUG/FEATURE/PERFORMANCE/UI/QUESTION/OTHER）
#       按 sort 升序；每条带 issueCount 字段（OTHER 因存量回填应 >0，若库中原有问题）
```

### 2.2 options 默认仅启用项

```bash
curl -s "$BASE/api/issue-types/options" -H "Authorization: Bearer $TOKEN"
# 断言：code=200；所有条目 enabled=true（不含停用项）
```

### 2.3 options 全量（筛选场景，停用置底）

```bash
curl -s "$BASE/api/issue-types/options?includeDisabled=true" -H "Authorization: Bearer $TOKEN"
# 断言：code=200；含停用项且停用项排在启用项之后
#      （「(已停用)」后缀由前端拼接，接口只回 enabled 布尔）
```

### 2.4 新增类型

```bash
curl -s -X POST "$BASE/api/issue-types" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"冒烟临时类型","code":"SMOKE_TMP","description":"smoke","sort":99,"enabled":true}'
# 断言：code=200，data 为新 id → 导出 TYPE_ID
```

### 2.5 编辑类型 + code 重复校验

```bash
curl -s -X PUT "$BASE/api/issue-types/$TYPE_ID" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"冒烟临时类型2","code":"SMOKE_TMP","sort":99,"enabled":true}'
# 断言：code=200

curl -s -X POST "$BASE/api/issue-types" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"重复code","code":"BUG","sort":98}'
# 断言：code=40061（类型编码已存在）
```

### 2.6 删除被引用类型 → 阻断（核心验收）

```bash
# 取 OTHER 的 id（存量问题已回填 OTHER，必有引用）
curl -s "$BASE/api/issue-types" -H "Authorization: Bearer $TOKEN" | grep -o '"code":"OTHER"[^}]*'
curl -s -X DELETE "$BASE/api/issue-types/<OTHER_ID>" -H "Authorization: Bearer $TOKEN"
# 断言：code=40062（ISSUE_TYPE_HAS_USAGE），message 含「无法删除，可改为停用」
#       且再查列表 OTHER 仍在（未被物理/逻辑删除）
```

### 2.7 停用/启用切换 + 删除无引用类型

```bash
curl -s -X PUT "$BASE/api/issue-types/$TYPE_ID/status" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"enabled":false}'
# 断言：code=200；GET /options（默认）不再含 SMOKE_TMP；?includeDisabled=true 含且置底

curl -s -X DELETE "$BASE/api/issue-types/$TYPE_ID" -H "Authorization: Bearer $TOKEN"
# 断言：code=200（无引用可删，测试数据清理完成）
```

### 2.8 权限阻断（提交者无 issue:type:create）

```bash
curl -s -X POST "$BASE/api/issue-types" \
  -H "Authorization: Bearer $TOKEN_SUBMITTER" -H "Content-Type: application/json" \
  -d '{"name":"越权","code":"HACK","sort":1}'
# 断言：code=403 类权限错误（非 200）
```

---

## 3. R4 Issue 带 typeId 贯通

### 3.1 创建问题必带 typeId（multipart 表单）

```bash
BUG_ID=$(curl -s "$BASE/api/issue-types/options" -H "Authorization: Bearer $TOKEN" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
curl -s -X POST "$BASE/api/issues" \
  -H "Authorization: Bearer $TOKEN" \
  -F "title=Phase6冒烟-类型贯通" -F "typeId=$BUG_ID" -F "severity=2" \
  -F "description=smoke test"
# 断言：code=200 → 导出 ISSUE_ID
```

### 3.2 不带 typeId 创建 → 拒绝

```bash
curl -s -X POST "$BASE/api/issues" \
  -H "Authorization: Bearer $TOKEN" \
  -F "title=无类型应失败" -F "severity=2" -F "description=x"
# 断言：code!=200（typeId 必填校验或 40060「请选择问题类型」）
```

### 3.3 详情回显 typeId/typeName

```bash
curl -s "$BASE/api/issues/$ISSUE_ID" -H "Authorization: Bearer $TOKEN"
# 断言：data.typeId=$BUG_ID 且 typeName 非空（如「缺陷」）
```

### 3.4 列表按 typeId 筛选

```bash
curl -s "$BASE/api/issues?page=1&size=10&typeId=$BUG_ID" -H "Authorization: Bearer $TOKEN"
# 断言：code=200；records 全部 typeId=$BUG_ID，且含刚建的 ISSUE_ID
```

### 3.5 编辑改 typeId + 停用类型不可选

```bash
curl -s -X PUT "$BASE/api/issues/$ISSUE_ID" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"typeId":<某启用类型id>}'
# 断言：code=200

curl -s -X PUT "$BASE/api/issues/$ISSUE_ID" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"typeId":<某停用类型id>}'
# 断言：code=40063（该问题类型已停用，不可选择）
```

---

## 4. R1 前台「提交问题」菜单已逻辑删除（查库/查接口）

```bash
curl -s "$BASE/api/menus/sidebar?type=1" -H "Authorization: Bearer $TOKEN"
# 断言：返回树中【无】path=/user/submit-issue 的节点；
#       含「问题管理」父节点（path=/user/issue, icon=Tickets），其 children 含「我的问题」(/user/my-issues)
```

SQL 复核（可选，在 MySQL 上）：

```sql
SELECT path, deleted FROM menu WHERE path='/user/submit-issue';   -- 期望 deleted=1
SELECT name, path, icon, parent_id FROM menu WHERE path IN ('/user/issue','/user/my-issues') AND deleted=0;
```

## 5. R8 icon 合法性（查库）

```sql
SELECT id, name, icon FROM menu WHERE deleted=0 AND icon='Tree';   -- 期望 0 行
SELECT id, name, icon FROM menu WHERE deleted=0 AND (icon IS NULL OR icon='');  -- 记录并人工确认
SELECT path FROM menu WHERE path='/admin/settings' AND deleted=0;  -- 期望 0 行（僵尸菜单已清理）
SELECT name, path, icon FROM menu WHERE path='/admin/issue-types' AND deleted=0; -- 期望 1 行 icon=CollectionTag
SELECT name, path FROM menu WHERE path='/admin/system/site' AND deleted=0;       -- 期望 1 行（网站设置）
```

## 6. 权限种子复核（查库）

```sql
SELECT code FROM permission WHERE code IN
 ('issue:type:list','issue:type:create','issue:type:update','issue:type:delete','site:config:update');
-- 期望 5 行
SELECT COUNT(*) FROM role_permission rp JOIN permission p ON rp.permission_id=p.id
 JOIN role r ON rp.role_id=r.id
WHERE r.code='ADMIN' AND p.code IN
 ('issue:type:list','issue:type:create','issue:type:update','issue:type:delete','site:config:update');
-- 期望 5
```

---

## 7. 前端手动项（无法 curl）

| # | 项目 | 步骤 | 期望 |
|---|------|------|------|
| M1 | R2 提交抽屉 | 前台「我的问题」→「提交新问题」 | 右侧 lg(800px) 抽屉打开；头部有纯图标全屏按钮，点击后 100% 宽，再点还原 |
| M2 | R2 分区折叠 | 打开提交抽屉观察 4 分区（基本信息/详细描述/环境信息/附件） | ⚠️ 当前实现为全部默认展开（与设计「仅基本信息展开」有偏差，见测试报告 Known Issue）；点击标题可折叠/展开 |
| M3 | R2 校验定位 | 折叠「详细描述」区后直接点保存 | 出错分区自动展开并滚动定位到首个错误字段 |
| M4 | R3 前台菜单 | 观察前台侧栏 | 「问题管理」(Tickets 图标) 父菜单下挂「我的问题」；无「提交问题」 |
| M5 | R5 抽屉化 | 我的问题编辑 / 后台问题编辑 / 状态流转备注 / 后台头像→个人设置 | 全部为右侧抽屉，无居中 el-dialog |
| M6 | R6 i18n | 顶栏语言切换 zh-CN ↔ en-US | 全站文案 + Element Plus 组件（分页/日期）联动切换；刷新后保持（localStorage `if_locale`） |
| M7 | R9 主题 | 前台顶栏主题切换 light/dark/blue/green | body[data-if-theme] 变化、配色即时生效；刷新保持；进入后台 /admin 不受前台主题影响；返回前台主题恢复 |
| M8 | R7 吸底 | 后台侧栏（含菜单较多时） | 侧栏 100vh 撑满，菜单区独立滚动，「返回前台」始终吸底；折叠 64px 态正常 |
| M9 | R8 图标 | 后台「项目管理→模块配置」 | 显示 Grid 图标（不再空白） |
| M10 | R10 联动 | 后台网站设置改站点名/默认主题→保存→登出→登录页 | 登录页标题/副标题变化；未手动选过主题的新浏览器（隐身窗）进前台默认主题=配置值 |
| M11 | R4 筛选停用后缀 | 后台停用某类型→问题列表类型筛选下拉 | 停用项带「（已停用）」后缀且置底；表单新建下拉不含停用项 |

---

## 执行顺序建议

0 → 1.1~1.4 → 2.1~2.8 → 3.1~3.5 → 4 → 5 → 6 → 前端手动 M1~M11。
测试数据清理：2.7 已删 SMOKE_TMP；3.1 创建的问题可在后台删除（DELETE /api/issues/$ISSUE_ID）。
