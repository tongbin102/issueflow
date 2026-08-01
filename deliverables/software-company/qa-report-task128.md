# QA 报告 — 任务 #128「用户前台工作台统计与点击跳转」7 Bug 修复验证

- **QA**：Edward（QA Engineer）
- **日期**：2026-08-01
- **工作目录**：`D:/WorkBuddyProjects/issueFlow`
- **验证范围**：前端构建门禁 + 静态一致性核查 + 边界风险分析（后端编译/运行期门禁移交 #130）
- **环境限制**：本机无 JDK17，后端**未编译、未运行**。后端 SQL 别名生效性、scope 过滤运行期行为由 #130（23 号机 `mvn clean package` + 冒烟）把关。
- **未执行**：git push、部署（按约束）

## 路由判定

> ## ROUTE: **Engineer**
>
> 构建通过、6 项静态核查全 PASS，但**新发现 2 个源码缺陷**，其中 1 个为本轮 BUG-02 修复引入的**确定性回归**，直接打断被测特性主链路。

---

## 一、前端构建门禁

| 项 | 结果 |
|---|---|
| Node / npm | v22.22.2 / 10.9.7 |
| `node_modules` | 已存在，无需 `npm install` |
| 构建命令 | `npx vite build --outDir dist-qa-t128` |
| **结果** | ✅ **PASS** — `✓ 2414 modules transformed`，`✓ built in 24.58s`，**0 error** |

非阻断告警（历史存量，与本轮改动无关）：
1. `@vueuse/core` 的 `/* #__PURE__ */` 注释位置无法被 Rollup 解析 —— 三方库问题。
2. `src/router/index.js` 同时被静态与动态导入 —— 历史结构问题，不影响产物。

产物已生成于 `src/frontend/dist-qa-t128/`（`dist-*/` 已被 `.gitignore` 忽略，不污染仓库）。

**改动面复核**：`git status` 显示的 10 个 modified 文件与工程师回传清单**逐一吻合**，无额外/意外文件改动。

---

## 二、静态一致性核查（6 项）

| # | 核查项 | 结果 | 证据 |
|---|---|---|---|
| 1 | 全仓 `src` 下 `.cnt\b` 残留为 0 | ✅ **PASS** | Grep 全仓 0 命中 |
| 2 | `IssueService.pageQuery` scope 接线 | ✅ **PASS** | `IssueService.java:293-297` |
| 3 | `UserDashboard` 卡片点击 + loading | ✅ **PASS**（清单项）<br>⚠️ 另见缺陷 A/C | `UserDashboard.vue:3,5,62` |
| 4 | `UserIssueList` 读 query 并下传 | ✅ **PASS** | `UserIssueList.vue:68,79,100,105,17` |
| 5 | `IssueTable` watch props.filters | ✅ **PASS** | `IssueTable.vue:459-481` |
| 6 | `CHANGELOG` 含 2026-08-01 条目 | ✅ **PASS** | `docs/CHANGELOG.md:17-34` |

### 逐项明细

**1. `.cnt` 残留 = 0 → PASS**

`IssueMapper.java` 三处聚合已全部改为反引号包裹的 `count`：
- `:41` `SELECT status AS status, COUNT(*) AS \`count\`` （statusDistribution）
- `:59` `SELECT DATE(created_at) AS day, COUNT(*) AS \`count\`` （trendByDay）
- `:122` `SELECT severity AS severity, COUNT(*) AS \`count\`` （severityRatio）

前端消费端口径已对齐：`TrendChart.vue:23`、`DistributionChart.vue:34,57` 均读 `Number(d.count)`；`ExcelExportUtil.java:116,121,126` 读 `m.get("count")`。

> **补充说明（非 FAIL）**：全仓仍存在 3 处 `AS cnt`，但均为**自洽闭环**、与 BUG-01 的看板链路无关，**不应改动**：
> - `IssueTypeService.java:223` 写 `AS cnt` ↔ `:229` 读 `row.get("cnt")`
> - `DictService.java:464` 写 ↔ `:470` 读
> - `DictItemMapper.java:23` 写 ↔ `DictService.java:484` 读

**2. `pageQuery` scope 接线 → PASS**

位置与顺序完全符合要求：
- `:286-288` SUBMITTER 兜底（`eq(reporter_id, currentUser)`）
- `:293-297` scope=mine 接线：`SCOPE_MINE.equals(req.getScope()) && !ROLE_ADMIN && !ROLE_SUBMITTER` → `eq(reporter_id, currentUser)`
- `:299` `orderByDesc(createdAt)`

即：**位于 SUBMITTER 兜底之后、`orderByDesc` 之前** ✅。`Constants.SCOPE_MINE/SCOPE_ALL` 于 `Constants.java:50,53` 定义；`IssuePageReq.scope` 默认 `"all"`（`:73`）。

**3. `UserDashboard` → 清单项 PASS，但存在缺陷 A / C**

- `:5` `@click="goList(card.status)"` + `style="cursor: pointer"` ✅
- `:62` `function goList(status)` 带参 ✅
- `:3` `<el-row :gutter="16" v-loading="loading">` ✅

**4. `UserIssueList` → PASS**

- `:68` `import { useRoute } from 'vue-router'`，`:79` `const route = useRoute()` ✅
- `:100` `route.query.status != null ? Number(route.query.status) : ''` ✅
- `:102-107` `watch(() => route.query.status, ...)` 同路由 query 变更亦生效 ✅
- `:17` `:filters="listFilters"` 传入 `IssueTable` ✅（`:16` 同时下发 `scope="mine"`）

**5. `IssueTable` watch → PASS**

`:459-481` `watch(() => props.filters, (nv) => {...}, { deep: true })`，回调内含 `Object.assign(filters, {...})`（`:463`）+ `page.value = 1`（`:477`）+ `fetchData()`（`:478`）✅。
无 `immediate`，`onMounted` 已单独 `fetchData()`（`:501`），**首屏不会重复请求** ✅。用户手改本地筛选不触发该 watch，**无死循环** ✅。

**6. CHANGELOG → PASS**

`Unreleased / Fixed` 段含 BUG-03(scope 接线) / BUG-02 / BUG-06 / BUG-07 四条，并注明「本期修复日期：2026-08-01」。

---

## 三、发现的缺陷（→ Engineer）

### 🔴 缺陷 A（P0，回归，阻断被测特性）— `UserDashboard.vue:27` 「查看我的」按钮把 MouseEvent 当 status 传出

**文件行号**：`src/frontend/src/views/user/UserDashboard.vue:27`

```html
<el-button text type="primary" @click="goList">{{ t('dashboard.user.viewMy') }}</el-button>
```

**根因**：Vue 3 模板中 `@click="fn"`（方法引用形式）会把原生事件对象作为**第一个实参**传入。本轮 BUG-02 把 `goList()` 改成了 `goList(status)` 带参签名（`:62`），于是该按钮实际调用的是 `goList(PointerEvent)`。

`:63` 的判空 `status != null` 对事件对象恒为 **true**，因此走了「带 status 跳转」分支。

**编译产物佐证**（`dist-qa-t128/assets/UserDashboard-73dr5OHF.js`）：
- 卡片：`onClick: X => b(l.status)` ← 箭头函数包裹，**正确**
- 按钮：`onClick: b` ← **裸函数引用，事件对象直灌**

**故障链路**：
1. `router.push({ path:'/user/my-issues', query:{ status: PointerEvent } })`
2. vue-router 序列化 query → URL 变为 `?status=%5Bobject%20PointerEvent%5D`
3. `UserIssueList.vue:100` `Number("[object PointerEvent]")` → **NaN**
4. `IssueTable.vue:307/464` `NaN ?? ''` → 仍为 **NaN**（`??` 不拦截 NaN）
5. `IssueTable.vue:353` `NaN !== '' && NaN !== null && NaN !== undefined` → 三项全 true → **`p.status = NaN` 被发出**
6. 后端 `IssuePageReq.status` 为 `Integer`，`"NaN"` → Integer 绑定失败 → **400 / MethodArgumentTypeMismatchException**
7. 同时 `el-select` 的 `v-model` = NaN → 状态下拉**空白不预选**

**影响**：工作台「查看我的」按钮 —— 正是本任务「统计与点击跳转」特性的入口之一 —— 点击后列表报错/不可用。修复前 `goList()` 无参，事件对象无害；**此为本轮改动引入的确定性回归**。

**建议修复**（双保险）：
```html
<!-- 模板：显式空参调用 -->
<el-button text type="primary" @click="goList()">{{ t('dashboard.user.viewMy') }}</el-button>
```
```js
// 函数内加类型守卫，防御后续再被裸引用
function goList(status) {
  const s = Number.isInteger(status) ? status : null
  router.push(s != null ? { path: '/user/my-issues', query: { status: s } } : { path: '/user/my-issues' })
}
```

---

### 🟠 缺陷 B（P1，口径不一致）— DEVELOPER/TESTER 的卡片数字与点进去的列表对不上

**文件行号**：`DashboardService.java:30` ↔ `IssueService.java:293-297`

```java
// DashboardService.java:30 —— 只有 SUBMITTER 收窄，DEVELOPER/TESTER 统计的是【全站】
Long reporterId = Constants.ROLE_SUBMITTER.equals(roleCode) ? currentUser : null;
```

而卡片点击后列表走 `scope=mine`，`IssueService:293-297` 对 **DEVELOPER/TESTER**（非 ADMIN 且非 SUBMITTER）**追加了 `reporter_id = 当前用户`**。

**矛盾**：以 DEVELOPER/TESTER 账号登录时——
- 工作台卡片（标题为「我提交的」`dashboard.user.submittedTotal`、「我的趋势」）显示的是**全站**数量
- 点击卡片跳转后的列表只显示**自己提交的**
- → **卡片数字 ≠ 列表 total**，用户会认为跳转筛选丢数据

**为何本轮才暴露**：BUG-03 修复前 `scope` 被 Spring 静默丢弃，列表也是全站，两者「错得一致」所以看不出来；BUG-03 接通后列表收窄了，看板侧却没同步，**不一致被放大为可见缺陷**。

**建议**：需产品口径决策，二选一——
- **方案 1（推荐，与卡片文案一致）**：`DashboardService.java:30` 改为对「非 ADMIN」一律收窄：
  `Long reporterId = Constants.ROLE_ADMIN.equals(roleCode) ? null : currentUser;`
  与 `pageQuery` 的 scope=mine 规则完全对称（ADMIN 全站 / 其余仅自己）。
- **方案 2**：保持后端不变，改前台文案与跳转语义（卡片改为「全站」口径，跳转不带 `scope=mine`）。

> ADMIN 侧无此问题：看板全站 + `pageQuery` 对 ADMIN 不加过滤 → 两侧一致。
> SUBMITTER 侧无此问题：看板收窄 + SUBMITTER 兜底收窄 → 两侧一致。
> **仅 DEVELOPER / TESTER 两个角色受影响。**

---

### 🟡 缺陷 C（P2，BUG-06 覆盖不全）— loading 遮罩未覆盖趋势图

**文件行号**：`UserDashboard.vue:3` vs `:23-31`

`v-loading="loading"` 只加在 `<el-row>`（统计卡片行）上，而下方趋势图卡片（`:23-31`，数据同样来自 `load()` 的同一次 `overview()` 请求）**无加载态**。首屏会出现「卡片转圈、图表空白」的割裂感。

**建议**：把 `v-loading` 提到最外层 `.user-dashboard` 容器，或给趋势图 `<el-card>` 补一个 `v-loading="loading"`。

---

## 四、边界风险点核查（任务指定 3 项）

### ① `status=0`（待处理）「数字 0 vs 空串」边界 → ✅ **已验证安全，不会漏发**

逐环节追踪 `status=0`：

| 环节 | 代码 | 结果 |
|---|---|---|
| 卡片点击 | `UserDashboard.vue:63` `status != null` | `0 != null` → **true**（`!=` 仅比 null/undefined）→ 带参跳转 ✅ |
| URL | — | `?status=0` ✅ |
| 读 query | `UserIssueList.vue:100` `route.query.status != null ? Number(...)` | `'0' != null` → true → `Number('0')` = **0** ✅ |
| 传子组件 | `IssueTable.vue:307` `props.filters.status ?? ''` | `0 ?? ''` → **0**（`??` 仅拦 null/undefined）✅ |
| watch 合并 | `IssueTable.vue:464` `nv.status ?? ''` | `0 ?? ''` → **0** ✅ |
| 组参 | `IssueTable.vue:353` `!== '' && !== null && !== undefined` | 0 三项全过 → **`p.status = 0` 发出** ✅ |

**结论**：全链路刻意使用了 `!=  null` / `??` / `!== ''` 三种「只拦空、不拦 0」的写法，`status=0` 可正确送达后端。**PASS**。
（唯一会破坏该链路的是缺陷 A 引入的 NaN —— NaN 同样穿透了这三道判空，反而被当成有效值发出。）

### ② `useStatusOptions()` value 类型 → ✅ **确为数字，与 `Number()` 转换一致**

`i18nEnum.js:14` `export const STATUS_CODES = [0, 1, 2, 3, 4]`（数字字面量）
`i18nEnum.js:99-103` `useStatusOptions()` → `STATUS_CODES.map(code => ({ value: code, label: ... }))`

`el-option :value="s.value"` 为 **number**，`UserIssueList` 侧 `Number()` 转换后类型一致 → 跳转后 `el-select` **能正确预选**。工程师声称属实。**PASS**。

### ③ 非管理员「我的问题」数据范围 → ⚠️ **静态逻辑正确，但需部署冒烟实测**

静态审阅 `IssueService:286-297` 逻辑正确（SUBMITTER 兜底 → DEVELOPER/TESTER 按 scope=mine 收窄 → ADMIN 全站）。
但**本机无 JDK17，无法验证运行期**：Spring 参数绑定是否真的接住了 `scope`、MyBatis-Plus wrapper 是否真的拼出 `reporter_id`。**必须由 #130 冒烟确认**，见下方清单 S4-S6。
另注意缺陷 B —— 该角色维度的看板/列表口径不一致需一并决策。

---

## 五、部署冒烟核查清单（供 #130 主理人在 23 号机执行）

### A. 后端编译门禁（最高优先级，本地无法覆盖）

- [ ] **S1** `mvn clean package` 通过，**零编译错误**。重点确认 `IssueMapper.java` 三处 `AS \`count\`` 的**反引号在 Java 字符串中已正确转义**、`@Select` 的 `<script>` XML 未被破坏。
- [ ] **S2** 应用启动无异常，MyBatis 映射器初始化通过（`IssueMapper` 3 个聚合方法能被正常解析）。

### B. BUG-01 count 别名（运行期）

- [ ] **S3** 以 ADMIN 登录 → 管理端看板：**状态分布 / 每日趋势 / 严重占比三张图均有数据，不再恒为 0**。
      抓 `GET /api/dashboard/overview` 响应，确认 JSON 数组元素的键是 **`count`** 而非 `cnt`。
- [ ] **S3b** 看板 Excel 导出（`/api/dashboard/export`）三个 sheet 段的数量列**不为 null/空**（验证 `ExcelExportUtil` 读 `m.get("count")`）。

### C. BUG-03 scope 过滤（**重点，含权限风险**）

- [ ] **S4** **DEVELOPER 或 TESTER** 账号 → 前台「我的问题」→ 列表**只出现自己提交的**问题。
      抓请求确认 query 带 `scope=mine`；抓响应确认无他人 `reporterName`。
- [ ] **S5** **ADMIN** 账号 → 前台「我的问题」→ 可见**全站**问题（设计如此，保留全局排障能力）。
- [ ] **S6** **SUBMITTER** 账号 → 只能看到自己的（安全底线，与 scope 无关）。
- [ ] **S6b** 越权探测：DEVELOPER 手工构造 `?scope=all` 请求 —— 记录实际返回范围。
      ⚠️ 按当前实现，`scope=all` 会**跳过收窄**从而看到全站。若产品要求 DEVELOPER 恒不可见他人数据，此为**待确认的越权面**，请在冒烟时明确结论并回报。

### D. BUG-02 / 06 / 07 前台交互

- [ ] **S7** 工作台统计卡片鼠标悬停显示手型，点击后跳转 `/user/my-issues?status=N`，列表按该状态筛选，**状态下拉预选中对应项**。
- [ ] **S8** **专项测 `status=0`（待处理）卡片** —— 点击后 URL 为 `?status=0`，列表确实只剩待处理，下拉预选「待处理」（0 值边界，最易漏）。
- [ ] **S9** **回归缺陷 A** —— 点击「查看我的」文字按钮，URL 应为**干净的** `/user/my-issues`（**不得**出现 `?status=[object...]`），列表正常加载、无 400。
- [ ] **S10** 工作台首屏刷新，卡片区出现 loading 遮罩且能正常结束（BUG-06）；若缺陷 C 已修，趋势图区同步有遮罩。
- [ ] **S11** 在「我的问题」页停留，从工作台再次点击**不同状态**的卡片，列表**即时重新拉取**且页码重置为 1（BUG-07 的 watch 生效）。

### E. 口径一致性（缺陷 B）

- [ ] **S12** 以 **DEVELOPER/TESTER** 登录，逐一比对：工作台某状态卡片数字 **是否等于** 点进去后列表的 total。
      当前实现下**预期不相等**（卡片全站 / 列表仅自己）。请确认修复方案后复测。

---

## 六、结论

| 项 | 结果 |
|---|---|
| 前端构建门禁 | ✅ PASS（0 error） |
| 静态一致性核查 6 项 | ✅ 6/6 PASS |
| 边界风险 ① status=0 | ✅ PASS（链路安全） |
| 边界风险 ② 下拉类型 | ✅ PASS（确为 number） |
| 边界风险 ③ 数据范围 | ⚠️ 静态正确，待 #130 冒烟 |
| 新发现缺陷 | 🔴 A(P0 回归) / 🟠 B(P1 口径) / 🟡 C(P2 体验) |

**工程师回传的 6 项清单内容全部属实且实现正确**，问题出在清单**未覆盖**的关联位置：同一文件里另一处复用 `goList` 的调用点（缺陷 A），以及看板侧与列表侧的角色口径联动（缺陷 B）。

> ### ROUTE: **Engineer**
> 待修：**缺陷 A（必修，P0 回归）** / **缺陷 B（需产品口径决策后修，P1）** / 缺陷 C（建议修，P2）。
> 修复后请回 QA 做**第 2 轮**回归（本任务 2 轮上限，第 2 轮若仍有残留将转为 Known Issues 随 #130 部署评估）。
