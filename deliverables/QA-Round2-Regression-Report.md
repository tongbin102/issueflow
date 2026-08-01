# issueFlow 第 2 轮回归验证报告（QA / Edward）

- 工作目录：`D:/WorkBuddyProjects/issueFlow`
- 验证时间：2026-08-01
- 环境：Node v22.22.2 / vite 5.4.21；**本机无 JDK17，后端仅静态审查**
- 未执行：git push、部署

---

## 一、回归结论：A / B / C

| 缺陷 | 等级 | 结论 | 证据 |
|---|---|---|---|
| A 「查看我的」裸 @click → status=NaN → 400 | P0 | **已修复** | `UserDashboard.vue` L27 `@click="goList()"`；L64-70 守卫 |
| B Dashboard 与列表 reporterId 口径 | P1 | **已修复（附 3 项残留风险）** | `DashboardService.java` L31；`IssueMapper` 全 6 条 SQL |
| C 趋势图卡片 v-loading | P2 | **已修复** | `UserDashboard.vue` L23 |

### A 详析：`status=0` 边界未被守卫误吞（重点验证项）

```js
// UserDashboard.vue L64-70
function goList(status) {
  if (status == null || Number.isNaN(Number(status))) {
    router.push({ path: '/user/my-issues' }); return;
  }
  router.push({ path: '/user/my-issues', query: { status: Number(status) } });
}
```

逐分支求值：

| 入参 | `status == null` | `Number.isNaN(Number(status))` | 结果 |
|---|---|---|---|
| `0`（待处理） | `0 == null` → **false** | `Number.isNaN(0)` → **false** | 不进守卫 → `?status=0` **正确** |
| `1..4` | false | false | `?status=N` 正确 |
| `undefined`（`goList()`） | **true** | 短路 | 无参跳转，正确 |
| `MouseEvent`（旧裸 click） | false | `Number(evt)` → NaN → **true** | 兜底无参跳转，不再 400 |

**结论：`status=0` 正常带参跳转，守卫未误吞。** 修复为「双保险」——模板加括号根治传参，守卫防御未来调用方误用。

### B 详析

**① reporterId 确实用作过滤条件 —— 是。**

`overview()` 共 6 次 mapper 调用，全部以 `reporterId` 为首参：
`statusDistribution`(L35)、`trendByDay`(L36)、`severityRatio`(L37)、`avgResolveCycle`(L39)、`countTotal`(L42)、`countClosed`(L43)。

`IssueMapper` 对应 6 条 SQL 均含 `<if test='reporterId != null'> AND reporter_id = #{reporterId} </if>`（行号 42 / 60 / 76 / 91 / 106 / 123）。→ 生效，无遗漏。

**② 与 `IssueService.pageQuery` 口径对称性 —— 在「实际可达路径」上对称，PASS。**

| 角色 | Dashboard overview | `/user/my-issues`（scope=mine） | `/admin/issues`（scope=all） | admin 是否可达 |
|---|---|---|---|---|
| ADMIN | 全站 | 全站（ADMIN 不过滤） | 全站 | 可达，三者一致 |
| SUBMITTER | 自己 | 自己（L286 硬兜底） | 自己 | **403 不可达** |
| DEVELOPER | 自己 | 自己（L293 scope 分支） | 全站 | **403 不可达** |
| TESTER | 自己 | 自己（L293 scope 分支） | 全站 | **403 不可达** |

`routes.js` 中 `/admin/**` 为 `meta:{roles:['ADMIN']}`，`router/index.js` L48-50 强制校验。DEVELOPER/TESTER **无法到达任何 scope=all 列表**，故不存在「工作台数字 ≠ 列表条数」的可复现场景。

**但必须指出 3 项残留风险：**

- **B-R1 设计脆弱性（P2）**：两处判定**镜像但不同源**。Dashboard 是「非 ADMIN → 过滤」；pageQuery 是「scope=mine 且非 ADMIN 且非 SUBMITTER → 过滤」。对称性依赖「DEVELOPER/TESTER 进不了 scope=all 页面」这一**路由约束**，而非服务层同源口径。一旦（a）给 TESTER 开放 `/admin/issues`，或（b）前端某处漏传 `scope`（DTO 默认 `all`），立即产生数字不一致。**建议**：抽公共方法 `resolveReporterFilter(roleCode, scope)` 供两处共用。
- **B-R2 语义误导（P2，非本轮引入）**：ADMIN 访问 `/user` 时，文案为「我提交总计」(`zh-CN/dashboard.js:33`)、「查看我的问题 →」(:35)、列表标题「我的问题」(`zh-CN/issue.js:78`)，但数据全为**全站**。数字自洽，标签误导。系 BUG-03「保留管理员全局排障能力」决策的副作用。
- **B-R3 产品口径待确认（P2，影响面最大）**：本轮把 DEVELOPER/TESTER 从「全站」收窄为「仅**自己提交**的」。但开发/测试的日常工作对象是**指派给自己**（`assignee_id`）的问题，不是自己提单的。**一个从不提单的 DEVELOPER 打开工作台会看到全 0。** 这与 `/user/my-issues` 一致（都 0），因此不算 bug，但**需 PM 确认**：DEVELOPER 工作台是否应按 `assignee_id = me` 统计。**部署后请重点观察此项用户反馈。**

---

## 二、8 项核查清单

| # | 检查项 | 结果 | 证据 |
|---|---|---|---|
| 1 | `npx vite build` 构建成功 | **PASS** | 见下方说明 |
| 2 | `src/` 下 `.cnt` 残留为 0 | **PASS** | 命中数 0 |
| 3 | `pageQuery` scope 接线仍在 | **PASS** | L293-297，位于 L286 SUBMITTER 兜底之后、L299 `orderByDesc` 之前 |
| 4 | `UserDashboard.vue` 四项 | **PASS** | L5 / L27 / L64-70 / L3+L23 |
| 5 | `UserIssueList.vue` 四项 | **PASS** | L68 / L100 / L17 / L102-107 |
| 6 | `IssueTable.vue` deep watch | **PASS** | L459-481 |
| 7 | `CHANGELOG.md` 含本轮 A/B/C 条目 | **FAIL** | 见下方 |
| 8 | 类型一致性 | **PASS** | 全链路 Number |

### 1. 构建（硬门禁）—— PASS

首次执行失败，但 **failure 与源码无关**，是本机 sandbox 批量删除守卫拦截 vite 清空 `dist/assets`：

```
[safe-delete][SAFE_DELETE_BULK_CONFIRM_REQUIRED] {"count":92,"threshold":50,...}
    at emptyDir (.../vite/dist/node/chunks/dep-BK3b2jBa.js:17082:19)
    at prepareOutDir (...)
```

注意该轮 `✓ 2414 modules transformed.` 已完成，失败发生在写盘前清目录阶段。改用全新输出目录复验，**构建通过**：

```
$ npx vite build --outDir dist-qa-r2-final
✓ 2414 modules transformed.
dist-qa-r2-final/assets/index-Bb6yjXMn.js   1,034.91 kB │ gzip: 343.41 kB
dist-qa-r2-final/assets/index-Dh5rNAEQ.js   1,376.65 kB │ gzip: 449.98 kB
✓ built in 13.78s
=== EXIT PIPE STATUS: 0 ===
```

无源码报错，仅 2 条 `@vueuse/core` 的 PURE 注释位置警告（第三方库既有，非本轮引入）。

### 2. `.cnt` 残留 —— PASS

严格 `\.cnt\b`（点号属性访问）命中 **0**。

裸 `cnt` 另有 19 处，均为**无关且自洽**的用法，不跨端：

- `DictItemMapper.java:23`、`DictService.java:464/470/484`、`IssueTypeService.java:223/229` —— 别名 `cnt` 在同文件内以 `row.get("cnt")` 读取，纯后端内部闭环；
- `ModuleTreePanel.vue:410` —— 局部变量；
- `DashboardVO.java:12`、`IssueMapper.java:36/56/119` —— 注释中说明「历史别名 cnt 已废弃」。

看板三条聚合 SQL 均已为 ``COUNT(*) AS `count` ``（`IssueMapper` L41 / L59 / L122），与前端 `Number(d.count)` 对齐。

### 7. CHANGELOG —— FAIL（唯一 FAIL 项）

`docs/CHANGELOG.md` L16-34 的 `### Fixed` 段**仅有第 1 轮**的 BUG-02/03/06/07 条目。关键词 `NaN` / `MouseEvent` / `PointerEvent` / `回归` / `DashboardService` / `reporterId` 检索**全部 0 命中**。逐项缺失：

- **A 缺失**：L22-26 的 BUG-02 条目只写了「卡片加 `@click="goList(card.status)"`」，**未记录**「『查看我的』按钮裸 `@click="goList"` 把 MouseEvent 当 status 传入 → `?status=[object PointerEvent]` → 后端 400」这个 **P0 回归**及其修复（加括号 + `Number.isNaN` 守卫）。
- **B 缺失（最严重）**：`DashboardService.java` L31 由 `ROLE_SUBMITTER.equals(roleCode) ? currentUser : null` 改为 `ROLE_ADMIN.equals(roleCode) ? null : currentUser`，**完全未记录**。这是**用户可见的行为变更** —— DEVELOPER/TESTER 工作台数字将从「全站」变为「仅自己提交」，数字会明显变小甚至归零。若不写入 CHANGELOG，运维/PM 极可能在 #130 上线后误判为**数据丢失**（尤其刚经历 8/1 整库丢失事故，误判代价极高）。
- **C 缺失**：L27-28 的 BUG-06 条目只写了「`<el-row>` 加 `v-loading`」，未提趋势图卡片（L23）这第二处。

**修复位置**：`docs/CHANGELOG.md` L16 `### Fixed` 段下追加 3 条；并更新 L33-34 的「本期修复日期」脚注，注明为第 2 轮回归修复。

### 8. 类型一致性 —— PASS（确定结论：全链路 Number，一致）

`useStatusOptions()` 的 value 是**数字**：

```js
// i18nEnum.js L14
export const STATUS_CODES = [0, 1, 2, 3, 4]
// i18nEnum.js L99-103
STATUS_CODES.map((code) => ({ value: code, label: t('enum.status.' + code) }))
```

全链路追踪：

| 环节 | 位置 | 值 | 类型 |
|---|---|---|---|
| 卡片 status | `UserDashboard.vue` L52 `status: s.value` | `0` | Number |
| 跳转 query | L69 `query:{status: Number(status)}` | `0` | Number → URL `?status=0` |
| 读回 | `UserIssueList.vue` L100 `Number(route.query.status)` | `"0"` → `0` | **Number** |
| 下传 | L17 `:filters="listFilters"` | `{status: 0}` | Number |
| 表格本地 | `IssueTable.vue` L307 `props.filters.status ?? ''` | `0`（用 `??` 而非 `\|\|`，0 不被吞，正确） | Number |
| 下拉 v-model | L8 `v-model="filters.status"` | `0` | Number |
| 下拉 option | L17 `:value="s.value"` | `0` | **Number** |
| 提参 | L353-354 `!== '' && !== null && !== undefined` | `p.status = 0` | 0 未被误排除 |

**`v-model` 的 `0`(Number) 与 `el-option :value` 的 `0`(Number) 严格相等 → 跳转后下拉正确预选「待处理」，筛选生效。**

关键点：若 `UserIssueList.vue` L100 漏掉 `Number()`，则为 `"0"`(String) ≠ `0`(Number)，下拉不预选。该转换**存在且正确**。

**附带发现（P3，非本轮范围，不计 FAIL）**：`UserIssueList.vue` L100/L105 对**手工输入 / 收藏夹**的异常 URL 无防护 —— `?status=abc`，或重复参数 `?status=0&status=1`（此时 `route.query.status` 为数组）→ `Number()` 得 `NaN` → `NaN !== '' / null / undefined` 三条均成立 → `p.status=NaN` 发给后端 → 400。与 A 同类，但入口在 URL 侧。**建议加固**：L100/L105 改为 `const n = Number(nv); listFilters.status = Number.isNaN(n) ? '' : n`。UI 正常导航不触发，故本轮不阻断。

---

## 三、部署冒烟核查清单（供主理人在 #130 执行）

服务器：`http://10.55.3.23:18082/api`　鉴权：`Authorization: Bearer <token>`
响应统一包 `Result{code,message,data,timestamp}`，业务数据在 `.data`。

### S0 取 token

```bash
BASE=http://10.55.3.23:18082/api

# 普通用户（SUBMITTER / DEVELOPER / TESTER 各取一个分别跑）
TOKEN_USER=$(curl -s -X POST "$BASE/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"<普通用户名>","password":"<密码>"}' | jq -r '.data.token')

# 管理员
TOKEN_ADMIN=$(curl -s -X POST "$BASE/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"<密码>"}' | jq -r '.data.token')

echo "USER=${TOKEN_USER:0:20}...  ADMIN=${TOKEN_ADMIN:0:20}..."
```

### S1【B 核心】overview 键名必须为 `count`，不得为 `cnt`

```bash
curl -s "$BASE/dashboard/overview" -H "Authorization: Bearer $TOKEN_USER" \
  | jq '.data.statusDistribution, .data.trendByDay, .data.severityRatio'
```

- **通过**：三个数组每个元素都含 `"count"` 键。
- **失败**：出现 `"cnt"` → 打回 Engineer（`IssueMapper` 别名回退）。

```bash
# 断言式：输出 0 即通过
curl -s "$BASE/dashboard/overview" -H "Authorization: Bearer $TOKEN_USER" \
  | grep -o '"cnt"' | wc -l
```

### S2【B 核心】普通用户与 ADMIN 的数字差异

```bash
echo "--- USER  ---"
curl -s "$BASE/dashboard/overview" -H "Authorization: Bearer $TOKEN_USER" \
  | jq '{total:.data.total, closed:.data.closedTotal, dist:.data.statusDistribution}'

echo "--- ADMIN ---"
curl -s "$BASE/dashboard/overview" -H "Authorization: Bearer $TOKEN_ADMIN" \
  | jq '{total:.data.total, closed:.data.closedTotal, dist:.data.statusDistribution}'
```

- **通过**：`ADMIN.total >= USER.total`；库中存在他人提单时应**严格大于**。
- **失败**：两者完全相等且库中确有多人提单 → L31 口径未生效。
- **注意（B-R3）**：DEVELOPER/TESTER 的 `total` 可能为 **0**（从不提单）。这是**预期行为**、不是 bug，但请截图留档并同步 PM 确认产品口径。

### S3【B 对称性】工作台数字 == 列表 total

```bash
# 工作台「我提交总计」
curl -s "$BASE/dashboard/overview" -H "Authorization: Bearer $TOKEN_USER" | jq '.data.total'

# 列表 scope=mine 的 total
curl -s "$BASE/issues?page=1&size=1&scope=mine" -H "Authorization: Bearer $TOKEN_USER" | jq '.data.total'
```

- **通过**：两数**完全相等**（同一角色、无其它筛选）。这是 B 的核心验收点。
- ADMIN 同样跑一遍，两数也应相等（都是全站）。

### S4【A 核心】`status=0` 不再 400

```bash
for s in 0 1 2 3 4; do
  echo -n "status=$s -> HTTP "
  curl -s -o /dev/null -w '%{http_code}\n' \
    "$BASE/issues?page=1&size=10&scope=mine&status=$s" \
    -H "Authorization: Bearer $TOKEN_USER"
done
```

- **通过**：五个全部 `200`，**尤其 `status=0`**。
- **失败**：`status=0` 返回 400 → A 未修复。

```bash
# 复现旧 P0 脏值：预期 400，证明守卫在前端、后端未放水
curl -s -o /dev/null -w 'dirty status -> HTTP %{http_code}\n' \
  "$BASE/issues?page=1&size=10&scope=mine&status=%5Bobject%20PointerEvent%5D" \
  -H "Authorization: Bearer $TOKEN_USER"
```

### S5【A / C】浏览器人工验证

访问前端入口，普通用户登录：

1. **A-1（本轮最关键人工项）**：进 `/user` 工作台 → 点「**待处理**」卡片 → URL 应为 `/user/my-issues?status=0`；页面**不报 400**；筛选区「状态」下拉**已预选「待处理」**；列表仅含待处理项。（覆盖 status=0 边界 + 类型一致性）
2. **A-2**：其余 4 张卡片同理，URL `?status=1..4`，下拉均正确预选。
3. **A-3（A 的直接回归点）**：点趋势卡片右上「**查看我的问题 →**」按钮 → 跳 `/user/my-issues`，**URL 不带任何 query**；F12 Network 中该请求**不得出现 `status=NaN` 或 `status=[object PointerEvent]`，且不得有 400**。
4. **C**：刷新 `/user`，观察加载瞬间 —— **统计卡片区与趋势图卡片两处同时出现 loading 遮罩**。
5. **BUG-07 护栏**：停留在 `/user/my-issues`，手动把地址栏 status 从 0 改到 2 回车 → 列表**自动重拉**且**回到第 1 页**。
6. **B-R2 留档**：用 ADMIN 登录访问 `/user` → 卡片文案「我提交总计」但显示全站数字。截图报 PM，确认是否需改文案。

### S6 回归护栏（确认本轮未破坏既有能力）

```bash
# 不带 scope（DTO 默认 all）
curl -s "$BASE/issues?page=1&size=5" -H "Authorization: Bearer $TOKEN_ADMIN" | jq '.code, .data.total'

# 组合筛选（status=0 + priority=0 双 0 值边界）
curl -s "$BASE/issues?page=1&size=5&scope=mine&status=0&priority=0" \
  -H "Authorization: Bearer $TOKEN_USER" | jq '.code, .data.total'

# 看板导出
curl -s -o /tmp/dash.xlsx -w 'export HTTP %{http_code}, bytes=%{size_download}\n' \
  "$BASE/dashboard/export" -H "Authorization: Bearer $TOKEN_USER"
```

- **通过**：`code` 均为 `200`；xlsx 字节数 > 0。

---

## 四、ROUTE 判定

> ## ROUTE: Engineer

**理由**：核查清单**第 7 项 FAIL**（`docs/CHANGELOG.md` 缺本轮 A/B/C 回归条目）。按既定门禁「任一 FAIL → ROUTE: Engineer」。

### 需 Engineer 处理（唯一项，纯文档，不涉代码、不影响构建）

- **文件**：`docs/CHANGELOG.md`
- **位置**：L16 `### Fixed` 段下追加 3 条；并修订 L33-34 脚注为第 2 轮回归修复
- **必须写明**：
  1. **A（P0）**：`UserDashboard.vue` **L27** —— 「查看我的」按钮原 `@click="goList"` 导致 MouseEvent 被当作 status 传入 → `?status=[object PointerEvent]` → 后端 400；已改 `@click="goList()"`，并在 **L64-70** 为 `goList(status)` 增加 `status == null || Number.isNaN(Number(status))` 守卫（双保险；`status=0` 边界已验证不受影响）。
  2. **B（P1，行为变更，务必醒目标注）**：`DashboardService.java` **L31** —— `ROLE_SUBMITTER.equals(roleCode) ? currentUser : null` 改为 `ROLE_ADMIN.equals(roleCode) ? null : currentUser`。**DEVELOPER/TESTER 工作台统计口径由「全站」收窄为「仅自己提交」，上线后数字会明显变小、甚至为 0，属预期行为，非数据丢失。**
  3. **C（P2）**：`UserDashboard.vue` **L23** —— 趋势图卡片补 `v-loading="loading"`，与 **L3** `<el-row>` 共两处。

**代码侧无需改动**：A/B/C 三项实现均已确认修复，前端构建通过，其余 7 项核查全部 PASS。CHANGELOG 补齐后即可放行 #130。

### 建议纳入 backlog（本轮不阻断）

- **B-R1**：抽 `resolveReporterFilter(roleCode, scope)` 统一 Dashboard 与 pageQuery 口径
- **B-R3**：DEVELOPER/TESTER 工作台是否应按 `assignee_id` 统计 —— 待 PM 决策
- **第 8 项附带发现**：`UserIssueList.vue` L100/L105 对异常 URL 的 `NaN` 加固
- **B-R2**：ADMIN 视角「我的」系列文案与全站数据语义不符

---

## 五、环境备注

- 后端未本地编译（无 JDK17），**编译门禁由 23 号机 `mvn clean package` 把关**。静态审查未发现语法 / 类型 / import 问题：`DashboardService` 已 import `Constants`（L3），`Constants.ROLE_ADMIN` 存在（`Constants.java` L17）。
- 本机 sandbox 批量删除守卫（阈值 50）会拦截 vite 清空 `dist/assets`。**23 号机 CI 无此限制，不影响部署。** 本地绕过方式：先重命名 `dist`，或使用 `--outDir <新目录>`。
- 验证过程新增临时产物 `src/frontend/dist_prev_1785566207/`、`src/frontend/dist-qa-r2-final/`，连同既有 `dist-qa-*` 历史目录，因超守卫阈值未删除，**建议在 23 号机或放开守卫后统一清理**（均不在 git 跟踪内）。
