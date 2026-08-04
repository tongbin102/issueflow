# issueFlow 用户前台 UI/UX 重构 — 前端架构设计与任务分解

> 文档类型：前端架构设计（简洁可落地版） ｜ 架构师：高见远 ｜ 日期：2026-08-01
> 输入：`docs/PRD-frontend-redesign-v1.0-2026-08-01.md`（产品经理：许清楚）
> 影响范围：用户前台（`/user` 路由，`UserLayout.vue`；提交者 / 开发 / 测试 / 管理员可见）
> 前端根：`src/frontend/src`

---

## 1. 实现方案概述（≤200 字）

沿用现有技术栈（Vue3 + Element Plus + Pinia + Vue Router + Axios + ECharts + Vite + vue-i18n），**不引入 Tailwind**。核心做三件事：① **令牌扩展**——在 `variables.css`（`:root` 默认/后台回落）新增间距/字号/阴影/圆角/语义色/断点刻度，并在 `themes.css` 4 套主题（light/dark/blue/green）按需覆盖，`format.js` 硬编码色值令牌化（值不变）；② **新增 `components/base/If*` 基础组件库**（IfCard/IfButton/IfEmptyState/IfLoading/IfModal/IfTag，含建议补充的 IfPageHeader），一律引用 `var(--*)` 禁硬编码；③ **重构 3 个核心页面**（UserDashboard/UserIssueList/IssueDetailDrawer）+ IssueTable 移动卡片流 + UserLayout 三端断点微调。全程不破坏 `body[data-if-theme]` + localStorage 主题机制与 i18n 聚合结构。

---

## 2. 关键现状对齐（落地约束）

以下为源码探查结论，工程师须在此基础上**增量修改**，避免破坏既有机制：

| 关注点 | 现状 | 本次策略 |
| --- | --- | --- |
| 样式引入顺序 | `main.js` 依次引入 `variables.css → theme.css → themes.css → index.css → admin-style.css` | **顺序不变**；新增令牌写入 `variables.css`/`themes.css`，新增基础样式写入 `index.css` 尾部 |
| 主题挂载 | `UserLayout` onMounted `themeStore.applyFrontTheme()` 写 `body[data-if-theme]`；卸载移除 | **严禁改动**；新组件只消费 `var(--*)`，不写 body/documentElement |
| 用户布局覆写 | `UserLayout.vue` scoped `.if-layout--user { --if-content-max:1200px; --if-radius:16px }` | 保留；平板/移动断点在此调整 `--if-content-max` |
| 卡片 | `.page-card`（`index.css` 裸类）+ 各页 `el-card` 混用 | 新增 `IfCard` 统一，`.page-card` 保留兼容但新页面不再直接用 |
| 语义色 | 硬编码于 `format.js`（`STATUS_COLORS`/`SEVERITY_COLORS`/`PRIORITY_COLORS`、`*_TAG_TYPE`） | 令牌化为 CSS 变量（hex 不变、固定不随主题）；`format.js` 函数签名保持兼容，供 IfTag/ECharts 引用 |
| 详情形态 | `IssueDetailDrawer.vue` 抽屉（非独立路由，PRD Q1=A 保持抽屉） | **保持抽屉**，仅做信息架构与视觉升级 |
| 响应式 | 仅 `theme.css` 有 `@media(max-width:768px)` 移动抽屉侧栏 | 新增 **1280px 平板断点**；移动端表格降级卡片流 |
| 基础组件目录 | `components/base/` **不存在** | 本次新建该目录 |
| i18n | `locales/{zh-CN,en-US}/index.js` 聚合各模块；key 规范 `模块.分组.语义` | 新增文案优先并入现有 `common`/`issue`/`dashboard` 模块，**避免新增顶级命名空间** |

---

## 3. 文件清单表格

> 类型：**新增** / **修改**。相对路径均以 `src/frontend/src/` 为根。

### 3.1 样式令牌层

| 相对路径 | 类型 | 职责 | 依赖文件 |
| --- | --- | --- | --- |
| `styles/variables.css` | 修改 | `:root` 新增：间距 `--if-space-*`、字号 `--if-font-*`、阴影 `--if-shadow-*`、圆角 `--if-radius-sm/pill`、语义色 `--if-color-*`、断点 `--if-bp-*`；作为默认/后台回落 | — |
| `styles/themes.css` | 修改 | 4 套主题块内**按需覆盖**阴影（dark 用更深值）、`--if-stat-card-bg` 等；语义色**不覆盖**（固定） | `variables.css` |
| `styles/theme.css` | 修改 | 布局骨架新增/调整断点：`@media(max-width:1279px)` 平板、`@media(max-width:768px)` 移动；触控热区 ≥44px；顶栏/页脚间距对齐令牌 | `variables.css` |
| `styles/index.css` | 修改 | 尾部新增：卡片 hover/过渡、`prefers-reduced-motion` 降级、`.page-card` 阴影改引 `--if-shadow-sm`；工具类（`.if-section-title` 等） | `variables.css` |
| `utils/format.js` | 修改 | 色值常量改为读取 CSS 变量或保持 hex 但标注与令牌一致；**函数签名/返回值不变**（兼容现有 40+ 调用点） | — |

### 3.2 基础组件层（`components/base/` 全部新增，`If` 前缀）

| 相对路径 | 类型 | 职责 | 依赖文件 |
| --- | --- | --- | --- |
| `components/base/IfCard.vue` | 新增 | 通用卡片，替代 `.page-card`；props `title/bordered/shadow/bodyPadding/loading`；slots `default/header/extra` | `variables.css`、`IfLoading.vue` |
| `components/base/IfButton.vue` | 新增 | 封装 `el-button`；props `type(primary/default/danger/text)/size(lg/md/sm)/icon/loading/block`；emit `click` | `variables.css` |
| `components/base/IfEmptyState.vue` | 新增 | 空状态/错误/无搜索结果；props `image(empty/error/search)/title/description/actionText`；emit `action` | `variables.css`、i18n |
| `components/base/IfLoading.vue` | 新增 | 统一加载态；props `visible/type(spinner/skeleton)/rows/text`；default slot 为被遮罩内容 | `variables.css` |
| `components/base/IfModal.vue` | 新增 | 轻量确认/信息弹窗（区别于 FormDrawer）；props `modelValue/title/size/type/loading`；emit `update:modelValue/confirm/cancel` | `variables.css`、`IfButton.vue` |
| `components/base/IfTag.vue` | 新增 | 状态/严重/优先级统一着色标签；props `value/kind(status/severity/priority)`；内部读语义色令牌 + i18nEnum 文案 | `utils/format.js`、`utils/i18nEnum.js` |
| `components/base/IfPageHeader.vue` | 新增（建议） | 页头（标题 + 右侧操作区 + 可选面包屑）；props `title/breadcrumb`；slot `extra` | `variables.css` |

### 3.3 页面与业务组件层

| 相对路径 | 类型 | 职责 | 依赖文件 |
| --- | --- | --- | --- |
| `views/user/UserDashboard.vue` | 修改（重构） | 分区：统计卡片区（IfCard+语义色）+ 快捷入口 + 我的最近问题（IfEmptyState 兜底）+ 趋势图；统计卡点击跳 `/user/my-issues` 带状态筛选 | `IfCard`、`IfEmptyState`、`IfTag`、`charts/TrendChart.vue`、`api/dashboard.js` |
| `views/user/UserIssueList.vue` | 修改（重构） | 页头（IfPageHeader+新建）+ IfCard 包裹筛选与数据区；沿用 FormDrawer 新建/编辑 + IssueDetailDrawer 详情 | `IfPageHeader`、`IfCard`、`IfButton`、`IssueTable.vue`、`FormDrawer.vue`、`IssueDetailDrawer.vue` |
| `components/IssueTable.vue` | 修改 | 桌面表格保持；新增 **移动端(<768) 卡片流**降级（编号+标题+状态 tag+更新时间+查看）；tag 渐进换 IfTag；空数据走 IfEmptyState | `IfTag`、`IfEmptyState`、`api/issue.js` |
| `components/IssueDetailDrawer.vue` | 修改（重构） | 流转操作区常驻视觉升级；分区标签视觉打磨；加载走 IfLoading；tag 换 IfTag | `IfLoading`、`IfTag`、`IssueFormSections.vue`、`StatusFlowButtons.vue` |
| `components/IssueFormSections.vue` | 修改（微调） | 配合详情信息架构：标签分区样式对齐令牌；`drawerFullscreen` inject 逻辑不变 | `variables.css` |
| `layouts/UserLayout.vue` | 修改 | 响应式微调：平板断点下侧栏折叠/内容区放宽 `--if-content-max`；顶栏/页脚间距对齐令牌；结构基本不变 | `theme.css`、`variables.css` |

### 3.4 国际化文案层

| 相对路径 | 类型 | 职责 | 依赖文件 |
| --- | --- | --- | --- |
| `locales/zh-CN/common.js` | 修改 | 新增 `common.empty.*`（空状态/错误/无结果标题与描述）、`common.action.*` 补充 | — |
| `locales/en-US/common.js` | 修改 | 与 zh-CN 对齐英文文案 | — |
| `locales/zh-CN/dashboard.js` | 修改 | 新增首页分区标题：快捷入口 / 我的最近问题 / 各快捷项 | — |
| `locales/en-US/dashboard.js` | 修改 | 与 zh-CN 对齐 | — |
| `locales/zh-CN/issue.js` | 修改 | 新增列表空状态、移动卡片流字段、详情分区标题相关文案 | — |
| `locales/en-US/issue.js` | 修改 | 与 zh-CN 对齐 | — |

> 说明：**不新增顶级命名空间**，文案并入现有 `common`/`dashboard`/`issue` 模块，`locales/*/index.js` 无需改动。

---

## 4. 任务列表（按实现顺序）

> 依赖标注前置任务编号；「验收点」为工程师自检与 QA 依据。约定：每个任务完成后须在 4 套主题 + 中英双语下自测无回归。

### 阶段一：令牌基座（无 UI 依赖，先行）

| 任务 | 任务名 | 影响文件 | 依赖 | 验收点 |
| --- | --- | --- | --- | --- |
| **T1** | 扩展设计令牌（间距/字号/阴影/圆角/断点） | `styles/variables.css`、`styles/themes.css` | — | 4 套主题下 `--if-space-*`/`--if-font-*`/`--if-shadow-*`/`--if-radius-*`/`--if-bp-*` 均可读取；dark 阴影加深；DevTools 计算值正确 |
| **T2** | 语义色令牌化 + format.js 对齐 | `styles/variables.css`、`utils/format.js` | T1 | 新增 `--if-color-success/warning/danger/info/processing`；`format.js` 色值与令牌一致且**函数签名不变**；现有页面色彩零回归 |
| **T3** | 全局基础样式与过渡 | `styles/index.css` | T1 | `.page-card` 阴影改引 `--if-shadow-sm`；卡片 hover/过渡统一；`prefers-reduced-motion` 下禁用动画；工具类可用 |

### 阶段二：基础组件库（依赖令牌，供页面调用）

| 任务 | 任务名 | 影响文件 | 依赖 | 验收点 |
| --- | --- | --- | --- | --- |
| **T4** | IfLoading + IfCard | `components/base/IfLoading.vue`、`components/base/IfCard.vue` | T1,T3 | IfCard 支持 title/extra/loading/bodyPadding/shadow；圆角随 `--if-radius`；loading 态复用 IfLoading 不闪烁 |
| **T5** | IfButton + IfEmptyState | `components/base/IfButton.vue`、`components/base/IfEmptyState.vue` | T1,T3 | IfButton 覆盖 primary/default/danger/text + lg/md/sm + loading/block；IfEmptyState 三态（empty/error/search）文案走 i18n、可选操作按钮 emit action |
| **T6** | IfTag（语义色标签） | `components/base/IfTag.vue` | T2 | `kind=status/severity/priority` 三类映射正确；着色引语义色令牌；文案走 i18nEnum；与旧 el-tag 视觉一致或更优 |
| **T7** | IfModal + IfPageHeader | `components/base/IfModal.vue`、`components/base/IfPageHeader.vue` | T4,T5 | IfModal 确认/信息两型、sm/md/lg 三档、移动满宽、confirm/cancel emit；与 FormDrawer 边界清晰（IfModal 不含复杂表单）；IfPageHeader 标题+extra 插槽 |
| **T8** | i18n 文案补齐（基础组件相关） | `locales/zh-CN/common.js`、`locales/en-US/common.js` | — | 空状态/错误/无结果标题描述、按钮补充文案中英齐全；无 `[missing]` 告警 |

### 阶段三：响应式布局（页面重构前铺路）

| 任务 | 任务名 | 影响文件 | 依赖 | 验收点 |
| --- | --- | --- | --- | --- |
| **T9** | 三端断点落地（布局骨架） | `styles/theme.css`、`layouts/UserLayout.vue` | T1 | 桌面≥1280/平板768–1279/移动<768 三档无横向滚动、无遮挡；平板侧栏折叠或图标态；移动抽屉侧栏正常；触控热区≥44px |

### 阶段四：核心页面重构（依赖组件 + 断点）

| 任务 | 任务名 | 影响文件 | 依赖 | 验收点 |
| --- | --- | --- | --- | --- |
| **T10** | 首页工作台重构 | `views/user/UserDashboard.vue`、`locales/{zh-CN,en-US}/dashboard.js` | T4,T5,T6,T9 | 统计卡片区（桌面6/平板3/移动2列）用 IfCard+语义色大数字；快捷入口分区；我的最近问题（空走 IfEmptyState）；趋势图分区；卡片点击带状态筛选跳转 |
| **T11** | IssueTable 移动卡片流 + IfTag 接入 | `components/IssueTable.vue` | T6,T9 | 桌面表格不变；<768 降级卡片流（编号+标题+状态 tag+更新时间+查看）；空数据 IfEmptyState；status/severity/priority 换 IfTag；筛选分页可用 |
| **T12** | 问题列表页重构 | `views/user/UserIssueList.vue`、`locales/{zh-CN,en-US}/issue.js` | T4,T5,T7,T11 | 页头（IfPageHeader+新建按钮）；IfCard 包裹筛选/数据区；新建/编辑沿用 FormDrawer；详情沿用 IssueDetailDrawer；三端可用 |
| **T13** | 问题详情抽屉重构 | `components/IssueDetailDrawer.vue`、`components/IssueFormSections.vue` | T4,T6,T9 | 流转操作区常驻可见且视觉升级；分区标签打磨；加载走 IfLoading；tag 换 IfTag；桌面抽屉/移动全屏均可读 |

### 阶段五：打磨与验收

| 任务 | 任务名 | 影响文件 | 依赖 | 验收点 |
| --- | --- | --- | --- | --- |
| **T14** | 顶栏/侧栏/页脚视觉统一 | `layouts/UserLayout.vue`、`styles/theme.css` | T9 | 顶栏元素间距/对齐统一；页脚版式对齐令牌、不参与滚动；侧栏 logo/菜单间距一致 | 
| **T15** | 深色模式细节复核（R-12） | `styles/themes.css`、`components/charts/TrendChart.vue`、`components/charts/DistributionChart.vue` | T10,T13 | dark 下无黑底黑字/低对比；卡片边框阴影达标；ECharts 适配深色背景（轴/网格/tooltip） |
| **T16** | 全站四主题 + 中英 + 三端回归自测 | 全部核心页面/组件 | T10–T15 | 4 主题 × 2 语言 × 3 端矩阵抽测无阻断缺陷；语义色固定不随主题变；主题切换不闪烁/不报错 |

> 共 **16 个任务**，主链路 T1→T2→(T4/T5/T6/T7)→T9→(T10/T11/T12/T13)→T14/T15→T16。i18n 文案任务（T8）可与组件任务并行。

---

## 5. 共享约定

### 5.1 CSS 变量命名

- **统一 `--if-` 前缀**（与现有 `if-` CSS 命名空间一致），语义化分组：
  - 间距 `--if-space-{xs|sm|md|lg|xl}` = `4/8/16/24/32px`
  - 字号 `--if-font-{h1|h2|h3|base|sm|xs}` = `26/20/16/14/13/12px`
  - 阴影 `--if-shadow-{sm|md|lg}`（dark 主题覆盖为更深值）
  - 圆角 `--if-radius`(布局覆写 16) / `--if-radius-sm`(8) / `--if-radius-pill`(999px)
  - 语义色 `--if-color-{success|warning|danger|info|processing}`（**固定，不在 themes.css 覆盖**）
  - 断点 `--if-bp-mobile:768px` / `--if-bp-desktop:1280px`（供文档/JS 对齐，`@media` 仍写硬阈值）
- **写入位置**：默认值写 `variables.css` `:root`（后台回落）；需随主题变化的（阴影/容器色）在 `themes.css` 4 套主题块覆盖。
- **消费约定**：组件内一律 `var(--*)`，**禁止硬编码色值/尺寸**（语义色除外，其值本就固定）。

### 5.2 `If` 组件 props 约定

- **命名**：组件 `PascalCase` + `If` 前缀；props `camelCase`；双向绑定统一用 `modelValue` + `update:modelValue`。
- **尺寸档位**：统一 `lg|md|sm`（默认 `md`），与 FormDrawer 尺寸语义对齐。
- **类型档位**：按钮/弹窗类型统一 `primary|default|danger|text`（危险操作必用 `danger`）。
- **加载态**：统一 `loading:boolean`，内部复用 IfLoading，不各自造 spinner。
- **事件**：动作类统一 emit `click`/`action`/`confirm`/`cancel`；不在组件内直接跳路由（由页面处理）。
- **边界**：复杂表单继续用 **FormDrawer**；IfModal 仅承载轻量确认/信息；二者不重叠。

### 5.3 i18n key 命名

- 规范沿用 `模块.分组.语义`（如 `common.empty.noData`、`dashboard.user.quickEntry`、`issue.list.empty`）。
- **新增文案并入现有模块**（common/dashboard/issue），不新增顶级命名空间，`index.js` 聚合不动。
- zh-CN 与 en-US **成对新增**，key 完全一致；枚举文案统一走 `utils/i18nEnum.js`，组件不自带枚举字面量。

### 5.4 主题切换不破坏机制（红线）

- 主题唯一入口：`themeStore.setFrontTheme()` → 写 `body[data-if-theme]`；挂载/卸载由 `UserLayout` 负责。
- **严禁**在任何新组件/页面写 `document.documentElement` 或直接操作 `body` 主题属性。
- **严禁**新增覆盖 `--el-*` 或主题令牌的全局副作用样式（避免污染后台/其它主题）。
- 语义色固定：不随主题变化，图表与标签统一引 `--if-color-*` 或 format.js 常量。
- 样式引入顺序保持 `variables → theme → themes → index`，确保 themes 覆盖优先级。

---

## 6. 风险与待确认

| 编号 | 风险/待确认 | 影响 | 建议对策 |
| --- | --- | --- | --- |
| **R1（高）** | `format.js` 语义色被 40+ 处引用（IssueTable/Detail/charts/i18nEnum 等），令牌化若改动函数签名将大面积回归 | 列表/详情/图表着色 | T2 **只令牌化色值、保持函数签名与返回值不变**；IfTag 作为新入口渐进替换，不强改旧调用点 |
| **R2（中）** | 移动端表格降级为卡片流为**新增渲染分支**，与筛选/分页/权限按钮（canEdit/canDelete）联动逻辑需同步，易漏态 | 移动端列表可用性 | T11 复用同一 `list/fetchData/权限计算`，仅切换展示层；卡片流仅保留「查看」主操作，其余操作进详情 |
| **R3（中）** | 平板断点 1280px 与 Element Plus `el-col` 内置断点（1200）不一致，栅格列数可能错位 | 平板布局 | 平板/桌面分界统一用**自定义 `@media`** 控制，`el-col` 的 `xs/sm/md` 仅作兜底；关键分区手写媒体查询 |
| **R4（中）** | dark 主题下 ECharts 图表（TrendChart/DistributionChart）轴线/网格/tooltip 未必自适应，可能低对比 | 深色模式可读性 | R-12/T15 为图表注入随主题的配色（读 CSS 变量或按 `frontTheme` 切 option），避免黑底黑字 |
| **R5（低）** | IfEmptyState 需要空状态插画/图标资源，PRD 未指定素材来源 | 视觉完成度 | **建议降级**：一期用 Element Plus 图标（如 `Box`/`Warning`/`Search`）+ 文案实现，二期再替换定制插画，不阻塞开发 |
| **R6（低）** | 全站按钮「渐进替换」为 IfButton 若一次性铺开工作量大 | 交付节奏 | 一期仅在 3 个核心页面替换，其余页面保留 el-button，后续迭代收敛 |

---

## 7. 附：给工程师的开工提示

1. **先做 T1–T3 令牌基座**并在浏览器 DevTools 逐一核对 4 主题计算值，再动组件，避免返工。
2. `components/base/` 为**本次新建目录**，组件保持无业务耦合（除 IfTag 依赖 format/i18nEnum）。
3. 重构页面时**保留现有数据流与 API 调用**（`api/dashboard.js`/`api/issue.js` 等）与 FormDrawer/IssueDetailDrawer 交互契约，仅换展示层。
4. 每个任务自测矩阵：**4 主题 × 中英 × 桌面/平板/移动**，重点验证主题切换无闪烁、语义色不变、无横向滚动、触控热区≥44px。
5. PRD 待确认项已按推荐选项落地：Q1=保持抽屉、Q2=保持主色、Q3=保留4主题、Q4=If前缀+base目录、Q5=不引入Tailwind、Q6=1280断点、Q7=卡片流、Q8=新增两分区。如后续变更，优先影响 T10/T12/T13。
