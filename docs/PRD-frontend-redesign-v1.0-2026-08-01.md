# issueFlow 用户前台 UI/UX 深度定制 PRD

> 文档类型：简单 PRD ｜ 产品经理：许清楚 ｜ 日期：2026-08-01
> 影响范围：用户前台（`/user` 路由，UserLayout；提交者 / 开发 / 测试 / 管理员可见）
> 状态：待用户拍板「待确认问题」后进入设计/开发

---

## 1. 项目信息

| 项 | 内容 |
| --- | --- |
| 语言（文档） | 简体中文 |
| 前端技术栈 | Vue3 + Element Plus + Pinia + Vue Router + Axios + ECharts + Vite + vue-i18n |
| 主题机制 | CSS 变量 + localStorage；前台 `body[data-if-theme]` 挂 light/dark/blue/green 四套主题 |
| 仓库 | `D:\WorkBuddyProjects\issueFlow`，前端根 `src/frontend/src` |
| 访问地址 | 前台 `http://10.55.3.23:18081/user` ｜ 后台 `http://10.55.3.23:18081/admin` ｜ API `http://10.55.3.23:18082/api` |

### 原始需求复述
对用户前台做深度 UI/UX 定制：① 重构导航栏/侧边栏/页头/页脚的整体布局；② 定制首页、问题列表页、问题详情页等核心页面；③ 桌面/平板/移动三端响应式适配；④ 统一配色、字体、间距、圆角等视觉元素；⑤ 通用组件（卡片、表单、按钮、弹窗等）模块化，便于统一调用与维护。

### 现状摘要（探索结论，供落地对齐）
- **布局**：`layouts/UserLayout.vue` 已具备 侧栏(SideMenu) + 顶栏(汉堡/标题/主题/语言/头像下拉) + 内容区(`if-content__inner` max 1200px) + 条件页脚(版权/备案号)。骨架样式在 `styles/theme.css`（`.if-layout / .if-sidebar / .if-topbar / .if-content / .if-footer`）。
- **主题令牌**：`styles/variables.css` 定义 `:root` 默认令牌；`styles/themes.css` 用 `body[data-if-theme]` 覆盖 4 套主题；用户布局在 `UserLayout.vue` 内覆写 `--if-content-max:1200px; --if-radius:16px`。
- **核心页面**：`views/user/UserDashboard.vue`（统计卡片 + 趋势图）、`UserIssueList.vue`（`el-card` 包 `IssueTable` + 新建/编辑 `FormDrawer` + 详情 `IssueDetailDrawer`）、`UserStats.vue`、`UserProfile.vue`。
- **详情形态**：问题详情当前为**抽屉**（`components/IssueDetailDrawer.vue`），非独立路由页。
- **现有可复用组件**：`FormDrawer`、`IssueTable`、`IssueForm`、`IssueFormSections`、`StatusTimeline`、`StatusFlowButtons`、`UserAvatar`、`ThemeSwitch`、`LocaleSwitch`、`AttachmentUploader` 等；**尚无 `If` 前缀的基础 UI 组件库**，`.page-card` 为裸 CSS 类。
- **语义色**：`utils/format.js` 硬编码 status/severity/priority 颜色（ARCH §七.6 硬约束：**固定不随主题变化**）。
- **响应式**：仅有 `@media (max-width:768px)` 移动端抽屉侧栏，**缺 1280px 平板断点**；`ThemeSwitch` 已实现深色模式切换。

---

## 2. 产品目标

沉淀一套「issueFlow 用户前台设计系统」（令牌 + 基础组件），在**不破坏现有 4 套主题与 i18n 机制**的前提下，统一视觉语言、补齐三端响应式，并将卡片/按钮/空状态/加载/弹窗等通用元素组件化，让核心页面（首页 / 列表 / 详情）视觉层次清晰、交互一致、易维护。

---

## 3. 用户故事

| 角色 | 用户故事 |
| --- | --- |
| 普通提交者 | 作为**提交者**，我希望首页一眼看清"我提交的问题"各状态数量与最近动态，以便快速判断是否有问题需要我补充或验证。 |
| 开发人员 | 作为**开发**，我希望问题列表页筛选清晰、状态标识一致、详情操作区固定可见，以便高效认领与流转，减少来回滚动。 |
| 测试人员 | 作为**测试**，我希望问题详情在桌面与平板下都能清楚展示描述/复现步骤/附件并快速执行"验证通过/回退"，以便随时随地完成验证。 |

---

## 4. 需求池

> 优先级：P0 必须有 ｜ P1 应该有 ｜ P2 锦上添花。所有改动须兼容现有 4 套主题与 vue-i18n。

| 需求编号 | 需求描述 | 优先级 | 验收标准 | 影响页面/组件 |
| --- | --- | --- | --- | --- |
| R-01 | **补齐设计令牌**：在 `variables.css` / `themes.css` 中新增间距、字号、阴影、语义色 CSS 变量刻度（详见 §5） | P0 | 4 套主题下令牌均生效；深色主题下阴影/边框对比达标；无硬编码色值残留于新组件 | `styles/variables.css`、`styles/themes.css`、`styles/index.css` |
| R-02 | **新增 IfCard 卡片组件**替代裸 `.page-card`，支持标题/操作区/无内边距/加载态 | P0 | 三个核心页面卡片统一由 IfCard 渲染；圆角随 `--if-radius`；视觉与旧版一致或更优 | 新增 `components/base/IfCard.vue`；`UserDashboard`、`UserIssueList`、`UserStats` |
| R-03 | **新增 IfEmptyState 空状态组件**（图标 + 文案 + 可选操作按钮） | P0 | 列表无数据、加载失败、无搜索结果三种场景均展示统一空状态；文案走 i18n | 新增 `components/base/IfEmptyState.vue`；`IssueTable`、`UserIssueList` |
| R-04 | **新增 IfLoading 加载态组件/指令封装**（骨架屏或 spinner 二选一，统一 loading 视觉） | P0 | 首页统计卡、列表、详情抽屉加载中展示统一 loading；不闪烁 | 新增 `components/base/IfLoading.vue`；`UserDashboard`、`IssueTable`、`IssueDetailDrawer` |
| R-05 | **首页（工作台）重构**：状态统计卡片分组 + 快捷入口 + "我的最近问题"列表 + 趋势图分区 | P0 | 分区标题清晰；卡片点击可跳转对应筛选列表；空数据走 IfEmptyState | `views/user/UserDashboard.vue` |
| R-06 | **问题列表页重构**：页头(标题+新建) + 筛选条 + 表格/卡片双形态；移动端表格降级为卡片流 | P0 | 桌面表格、移动端卡片流均可用；筛选与分页可用；状态 tag 颜色与令牌一致 | `views/user/UserIssueList.vue`、`components/IssueTable.vue` |
| R-07 | **三端响应式适配**：定义 桌面 ≥1280 / 平板 768–1279 / 移动 <768 三档断点并落地 | P0 | 三档视口下布局无横向滚动、无遮挡、触控热区 ≥44px；侧栏移动端抽屉正常 | `styles/theme.css`、`UserLayout.vue`、各核心页面 |
| R-08 | **导航/顶栏/页脚视觉统一**：顶栏层次、面包屑（可选）、页脚版式与令牌对齐 | P1 | 顶栏元素间距/对齐统一；页脚在有版权或备案号时展示且不参与滚动 | `UserLayout.vue`、`styles/theme.css` |
| R-09 | **新增 IfButton 按钮封装**（对 el-button 做尺寸/图标/加载/危险态约定） | P1 | 主要按钮风格统一；危险操作用统一 danger 态；与 i18n 文案配合 | 新增 `components/base/IfButton.vue`；全前台按钮渐进替换 |
| R-10 | **弹窗/抽屉统一为 IfModal 约定**：沉淀 FormDrawer 之外的轻量确认/信息弹窗规范 | P1 | 确认/信息类弹窗视觉统一；移动端满宽；与 FormDrawer 尺寸档位一致 | 新增 `components/base/IfModal.vue`；`UserIssueList`、`IssueDetailDrawer` |
| R-11 | **问题详情信息架构优化**：流转操作区固定 + 分区标签（基本/描述/附件/关联/历史）视觉升级 | P1 | 操作区常驻可见；标签切换流畅；桌面抽屉/移动全屏均可读 | `components/IssueDetailDrawer.vue`、`IssueFormSections.vue` |
| R-12 | **深色模式细节打磨**：复核 dark 主题下卡片、边框、阴影、图表对比度 | P2 | dark 主题下无"黑底黑字"/低对比问题；ECharts 图表适配深色背景 | `styles/themes.css`、`components/charts/*` |
| R-13 | **微交互与过渡**：卡片 hover、按钮反馈、抽屉/弹窗进出过渡统一 | P2 | 过渡时长/缓动统一；无明显卡顿；尊重 `prefers-reduced-motion` | `styles/index.css`、基础组件 |

---

## 5. UI 设计稿 / 设计系统规范

> 落地原则：**新增令牌优先写入 `styles/variables.css` 的 `:root`（作为默认/后台回落），并在 `styles/themes.css` 的 4 套主题块中按需覆盖**，与现有机制完全一致；组件内一律引用 `var(--*)`，禁止硬编码。

### 5.1 配色方案

#### 主色（跟随主题，可运行时覆盖）
| 令牌 | light（默认） | dark | blue | green | 用途 |
| --- | --- | --- | --- | --- | --- |
| `--theme-color` | `#409EFF` | `#409EFF` | `#1E6FFF` | `#17A97C` | 主品牌色 / 主按钮 / 链接 |
| `--theme-color-light` | `#79BBFF` | `#337ECC` | `#6BA2FF` | `#5CC4A3` | 主色浅色 / hover |

> 现状：上述已在 `themes.css` 落地，本次**沿用不改**（除非采纳"待确认问题 Q2"更换主色）。

#### 中性色（跟随主题）
| 令牌 | light | dark | 用途 |
| --- | --- | --- | --- |
| `--bg-page` | `#F5F7FA` | `#141414` | 页面底色 |
| `--bg-container` | `#FFFFFF` | `#1E1E20` | 卡片/容器背景 |
| `--if-sidebar-bg` | `#FFFFFF` | `#1E1E20` | 侧栏背景 |
| `--text-primary` | `#303133` | `#E5EAF3` | 主文本 |
| `--text-regular` | `#606266` | `#CFD3DC` | 常规文本 |
| `--text-secondary` | `#909399` | `#8D9095` | 次要/说明文本 |
| `--border-color` | `#E4E7ED` | `#414243` | 边框/分割线 |

#### 语义色（**固定，不随主题变化**，ARCH §七.6 硬约束）
> 现状硬编码在 `utils/format.js`，本次**令牌化**为 CSS 变量（值不变），供组件与图表统一引用。

| 语义 | 令牌（建议新增） | hex | 说明 |
| --- | --- | --- | --- |
| 成功 | `--if-color-success` | `#67C23A` | 验证通过 / 完成 |
| 警告 | `--if-color-warning` | `#E6A23C` | 待验证 / 中优先级 / 严重 |
| 危险 | `--if-color-danger` | `#F56C6C` | 致命 / 高优先级 |
| 信息 | `--if-color-info` | `#909399` | 待处理 / 已关闭 / 轻微 / 低优先级 |
| 进行中 | `--if-color-processing` | `#409EFF` | 处理中 |

状态/严重/优先级映射（与 `format.js` 保持一致，作为设计对照）：

```text
状态 status:   0 待处理→info   1 处理中→primary   2 待验证→warning   3 验证通过→success   4 已关闭→info
严重 severity: 0 致命→danger   1 严重→warning     2 一般→primary     3 轻微→info
优先级 priority:0 高→danger     1 中→warning       2 低→info
```

### 5.2 字体 / 字号规范

字体族沿用 `styles/theme.css` 现有定义（不改）：
```css
font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto,
  'Helvetica Neue', Arial, 'PingFang SC', 'Microsoft YaHei', sans-serif;
```

字号刻度（建议新增令牌，统一取代散落的 px）：
| 令牌 | 值 | 用途 |
| --- | --- | --- |
| `--if-font-h1` | `26px` | 页面级/大数字（首页统计值） |
| `--if-font-h2` | `20px` | 卡片区块标题 |
| `--if-font-h3` | `16px` | 次级标题 / Logo |
| `--if-font-base` | `14px` | 正文/表格/表单（默认） |
| `--if-font-sm` | `13px` | 辅助说明 / 标签 |
| `--if-font-xs` | `12px` | 页脚 / 备注 |
| 字重 | `400 / 600 / 700` | 常规 / 强调 / 大数字 |
| 行高 | 正文 `1.6`，标题 `1.4` | — |

### 5.3 间距 / 圆角 / 阴影规范

间距刻度（8pt 基准，建议新增令牌）：
| 令牌 | 值 | 典型用途 |
| --- | --- | --- |
| `--if-space-xs` | `4px` | 图标与文字间距 |
| `--if-space-sm` | `8px` | 按钮组间距 |
| `--if-space-md` | `16px` | 卡片内边距 / 栅格 gutter（现状 16） |
| `--if-space-lg` | `24px` | 区块之间 |
| `--if-space-xl` | `32px` | 页面分区 |

圆角（沿用并令牌化）：
| 令牌 | 值 | 说明 |
| --- | --- | --- |
| `--border-radius-base` | `8px` | 基础（后台回落） |
| `--if-radius` | `16px`（用户布局覆写） | 用户端卡片柔和大圆角（现状已在 UserLayout 覆写） |
| `--if-radius-sm` | `8px` | 按钮/输入/标签 |
| `--if-radius-pill` | `999px` | 胶囊标签/头像 |

阴影（建议新增令牌，深色主题需覆盖）：
| 令牌 | light 值 | 用途 |
| --- | --- | --- |
| `--if-shadow-sm` | `0 1px 4px rgba(0,0,0,.04)` | 卡片默认（现状 page-card 用值） |
| `--if-shadow-md` | `0 4px 16px rgba(0,0,0,.08)` | 卡片 hover / 悬浮 |
| `--if-shadow-lg` | `0 8px 32px rgba(0,0,0,.12)` | 弹窗/抽屉 |

> dark 主题下阴影改用更深值（参考 `themes.css` 已有 `--el-box-shadow` 暗色包），由 R-12 复核。

### 5.4 布局栅格与断点

| 档位 | 视口宽度 | 侧栏 | 内容区 | 栅格建议 |
| --- | --- | --- | --- | --- |
| 桌面 Desktop | ≥ 1280px | 常驻（220px，可折叠 64px） | 居中，`--if-content-max: 1200px` | 统计卡 4–6 列；主内容多列 |
| 平板 Tablet | 768–1279px | 常驻或折叠为图标 | 满宽，`max` 放宽或取消 | 统计卡 2–3 列；筛选换行 |
| 移动 Mobile | < 768px | 抽屉式（现状已实现） | 满宽 | 单列；表格→卡片流；触控热区 ≥44px |

新增断点令牌（供文档与 JS 对齐，媒体查询仍以硬阈值书写）：
```css
--if-bp-mobile: 768px;   /* <768 移动 */
--if-bp-desktop: 1280px; /* ≥1280 桌面；768~1279 平板 */
```

> 注意：Element Plus `el-col` 内置断点（sm≥768/md≥992/lg≥1200）与本规范 1280 不完全一致。落地时**平板/桌面分界统一用自定义 `@media` 处理**，`el-col` 的 `xs/sm/md` 仅作栅格列数兜底。

---

## 6. 页面级设计说明

### 6.1 首页 / 工作台（UserDashboard）

分区：① 状态统计卡片区（按状态分组 + 总计）② 快捷入口（新建问题 / 我的问题 / 我的统计）③ 我的最近问题（Top 5–10，走 IfEmptyState 兜底）④ 提交趋势图。

```text
┌─────────────────────────────────────────────────────────┐
│ [☰] 工作台                         [主题] [语言] [头像▾]   │  顶栏
├─────────────────────────────────────────────────────────┤
│  ┌待处理┐ ┌处理中┐ ┌待验证┐ ┌通过┐ ┌关闭┐ ┌ 总计 ┐      │  统计卡(桌面6列/平板3列/移动2列)
│  │  12  │ │  5  │ │  3  │ │ 20 │ │ 8 │ │  48  │        │
│  └──────┘ └─────┘ └─────┘ └────┘ └───┘ └──────┘        │
│  ┌───────────────── 快捷入口 ─────────────────┐          │
│  │ [＋新建问题]  [我的问题]  [我的统计]          │          │
│  └────────────────────────────────────────────┘          │
│  ┌──── 我的最近问题 ────┐  ┌──── 提交趋势 ────┐            │
│  │ #IF-1024 登录报错 …  │  │      ╱╲    ╱      │            │
│  │ #IF-1023 页面白屏 …  │  │  ╲  ╱  ╲  ╱       │  桌面并排/移动上下堆叠
│  │ (空→IfEmptyState)   │  │   ╲╱    ╲╱        │            │
│  └─────────────────────┘  └──────────────────┘            │
└─────────────────────────────────────────────────────────┘
```
要点：统计卡点击 → 跳 `/user/my-issues` 带状态筛选；大数字用 `--if-font-h1` + 语义色；卡片用 IfCard。

### 6.2 问题列表页（UserIssueList）

分区：① 页头（标题 + 新建按钮，现状已有）② 筛选条（关键词/状态/类型/优先级）③ 数据区（桌面表格 / 移动卡片流）④ 分页 ⑤ 新建/编辑/详情走抽屉。

```text
┌─────────────────────────────────────────────────────────┐
│ 我的问题                                 [＋ 提交新问题]    │  页头
├─────────────────────────────────────────────────────────┤
│ [关键词____] [状态▾] [类型▾] [优先级▾] [查询] [重置]        │  筛选条(移动端折叠/换行)
├─────────────────────────────────────────────────────────┤
│ 编号   标题          状态    优先级  处理人   更新时间  操作 │  桌面: IssueTable
│ IF1024 登录报错…    ●待处理  ●高    张三    08-01    查看  │
│ IF1023 页面白屏…    ●处理中  ●中    李四    07-31    查看  │
│ ─────────────────  (移动端每行降级为一张卡片) ───────────  │
│ (无数据 → IfEmptyState：插画 + "暂无问题" + [提交新问题])    │
├─────────────────────────────────────────────────────────┤
│                         〈 1 2 3 … 〉  共 48 条            │  分页
└─────────────────────────────────────────────────────────┘
```
要点：状态/优先级 tag 颜色引用语义色令牌；移动端 <768 表格转卡片流（编号+标题+状态 tag+更新时间+查看）。

### 6.3 问题详情（IssueDetailDrawer，当前为抽屉，见 Q3）

分区：① 顶部标题（问题编号）② 流转操作区（常驻，不随标签切换隐藏，现状已有）③ 分区标签（基本信息 / 问题描述 / 附件 / 关联 / 操作历史）。

```text
┌──────────────── 抽屉(桌面560px / 移动全屏) ───────────────┐
│ 问题详情 IF-1024                                    [×]    │
├──────────────────────────────────────────────────────────┤
│ ● 流转操作: [认领] [提交修复] [验证通过] [回退] (常驻可见)  │
├──────────────────────────────────────────────────────────┤
│ │基本│  编号 IF-1024                                       │
│ │描述│  标题 登录报错                                       │  左竖标签(全屏/移动转顶部横排)
│ │附件│  状态 ●处理中   优先级 ●高   严重 ●严重             │
│ │关联│  处理人 张三    创建时间 2026-08-01                 │
│ │历史│                                                     │
└──────────────────────────────────────────────────────────┘
```
要点：操作区常驻；标签区在全屏/移动端由左竖排切顶部横排（`IssueFormSections` 已支持 `drawerFullscreen` inject）。**若 Q3 决定改独立页**，则新增 `/user/issue/:id` 路由页，复用同一批分区组件。

---

## 7. 通用组件清单（`components/base/` 新增，`If` 前缀）

> 命名与现有 `if-` CSS 命名空间一致；均支持 4 主题 + i18n；props 为建议初版，最终以架构师接口设计为准。

| 组件 | 定位 | 建议 props | 建议 slots / emits |
| --- | --- | --- | --- |
| **IfCard** | 通用卡片（替代 `.page-card`） | `title?:string`、`bordered?:boolean=false`、`shadow?:'none'\|'hover'\|'always'='hover'`、`bodyPadding?:string='16px'`、`loading?:boolean=false` | slots: `default`、`header`、`extra`（右上操作区） |
| **IfButton** | 按钮封装（约定 el-button） | `type?:'primary'\|'default'\|'danger'\|'text'='default'`、`size?:'lg'\|'md'\|'sm'='md'`、`icon?:Component`、`loading?:boolean`、`block?:boolean=false` | emits: `click`；slot: `default` |
| **IfEmptyState** | 空状态/无数据/错误 | `image?:'empty'\|'error'\|'search'='empty'`、`title?:string`、`description?:string`、`actionText?:string` | emits: `action`；slot: `default`/`extra` |
| **IfLoading** | 统一加载态 | `visible:boolean`、`type?:'spinner'\|'skeleton'='spinner'`、`rows?:number=3`（骨架行数）、`text?:string` | slot: `default`（被遮罩内容） |
| **IfModal** | 轻量确认/信息弹窗 | `modelValue:boolean`、`title?:string`、`size?:'sm'\|'md'\|'lg'='md'`、`type?:'confirm'\|'info'`、`loading?:boolean` | emits: `update:modelValue`、`confirm`、`cancel`；slots: `default`、`footer` |
| **IfTag**（建议补充） | 状态/优先级标签统一着色 | `value:number`、`kind:'status'\|'severity'\|'priority'` | 内部读语义色令牌，替代各页散落 el-tag 映射 |
| **IfPageHeader**（建议补充） | 页头（标题 + 操作区 + 可选面包屑） | `title:string`、`breadcrumb?:array` | slot: `extra`（右侧操作） |

复用与不重复造轮子：
- **表单抽屉继续用现有 `FormDrawer`**（已支持 sm/md/lg + 全屏 + 移动满宽），IfModal 只补充"轻量确认/信息"场景，二者边界清晰。
- IfTag 落地后，`IssueTable`、`IssueDetailDrawer` 中的 status/severity/priority tag 渐进替换，颜色统一引用 §5.1 语义色令牌。

---

## 8. 待确认问题（高优先级在前，含推荐选项）

| 编号 | 待确认问题 | 选项 | 推荐 |
| --- | --- | --- | --- |
| **Q1** | 问题详情保持**抽屉**还是改为**独立页面**（`/user/issue/:id`）？需求写"问题详情页"，但现状是抽屉。 | A 保持抽屉（改动小、复用现有）｜ B 改独立页｜ C 桌面抽屉+移动独立页 | **A**（保留抽屉，本次仅做信息架构与视觉升级；如需分享/深链再评估 B） |
| **Q2** | 主色是否更换？现状 light 主色为 Element 默认蓝 `#409EFF`。 | A 保持 `#409EFF`｜ B 换品牌蓝 `#1E6FFF`｜ C 提供其他品牌色值 | **A**（保持，改动最小、与 4 主题一致；若有品牌规范再定 B/C） |
| **Q3** | 深色模式：**已实现**（light/dark/blue/green + ThemeSwitch）。本次是保留四套、还是收敛？ | A 保留 4 套仅打磨深色细节｜ B 收敛为 light+dark 两套｜ C 只留 light | **A**（保留现有能力，R-12 仅做深色细节复核，不删功能） |
| **Q4** | 组件库前缀与目录：新增基础组件用 `If` 前缀、放 `components/base/`？ | A `If` 前缀 + `components/base/`｜ B 其他前缀/目录 | **A**（与现有 `if-` CSS 命名空间统一） |
| **Q5** | 是否引入 Tailwind CSS？（团队默认模板含 Tailwind，但本项目现为纯 CSS 变量方案） | A 不引入，沿用 CSS 变量令牌｜ B 引入 Tailwind | **A**（不引入，避免与 Element Plus/现有令牌体系冲突，降低回归风险） |
| **Q6** | 平板/桌面分界断点取 **1280px**（与 Element Plus 内置 1200 不一致），是否确认？ | A 采用 1280 自定义媒体查询｜ B 对齐 EP 的 1200 | **A**（按需求书 1280；`el-col` 断点仅作兜底） |
| **Q7** | 移动端问题列表：表格降级为**卡片流**是否确认？ | A 卡片流（推荐移动可读性）｜ B 横向滚动表格 | **A**（卡片流，触控友好） |
| **Q8** | 首页是否新增"快捷入口 + 我的最近问题"分区（当前仅统计卡 + 趋势图）？ | A 新增两分区｜ B 保持现状仅视觉升级 | **A**（提升工作台价值，符合各角色故事） |

---

*本 PRD 为"简单 PRD"，聚焦需要做什么与设计规范，具体接口/实现细节由架构师与前端在设计评审后细化。*
