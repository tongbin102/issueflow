# 角色管理 ·「分配权限」页面重构 — 交互设计说明

> 产出：产品经理许清楚（software-product-manager）｜ 实现：工程师寇豆码（software-engineer）｜ 验证：QA 严过关（IS_PASS）
> 适用范围：`views/admin/RoleManage.vue` 中「分配权限」抽屉（`FormDrawer`）的布局与交互重构
> 约束：**后端接口、请求/响应结构、PermissionVO 字段一律不变**，仅优化布局与交互

---

## 1. 概述与目标

| 项 | 说明 |
|---|---|
| 改动范围 | 仅「分配权限」弹窗内部的布局、信息层级与交互；角色列表页其余不动 |
| 不改动 | 后端接口、请求/响应结构、`PermissionVO` 字段、权限码语义、「整体替换」保存语义 |
| 新增内容 | 纯前端 UI 状态与视图派生（树形、端分组、已选清单、diff 摘要、虚拟滚动） |

**与现有接口/数据的兼容（重要）**：`GET /api/permissions`、`GET /api/roles/{id}/permissions`、`PUT /api/roles/{id}/permissions` 三个接口**零改动**；`PermissionVO`（id/code/name/module/action/type/sort）字段**全部保持原样**。本次复用后端早已返回的 `type` 字段（1=前台端，2=后台端）做前后台切换，**无需任何后端改动**。提交出参形态不变：仍是单一 `permissionCodes: string[]` 数组。

**树派生（纯前端，不落库）**：权限码格式 `module:resource:action`，按 `:` 拆分层级——第 1 段=一级（模块）、第 2 段=二级（资源）、第 3 段=叶子（真实权限）。只有叶子是真实权限，一二级为虚拟聚合节点，不参与提交；段数不足 3 段时按实际段数收敛。

---

## 2. 布局结构

### 2.1 整体分区（抽屉 size="xl"，约 min(1080px, 92vw)）

```
┌──────────────────────────────────────────────────────────────────────┐
│ A  分配权限 · 角色：项目管理员                                    [ × ] │
├──────────────────────────────────────────────────────────────────────┤
│ B  ┌──────────────┬──────────────┐  搜索框 [🔍] [全选][全选可见][反选][展开][收起] │
│    │ 前台端 (12) │ 后台端 (34) │                                     │
│    └──────────────┴──────────────┘                                     │
├──────────────────────────────────────────────────────────────────────┤
│ C  已选 46 项 · 前台 12 / 后台 34 · ⚠ 含 2 项已失效          [权限项较多提示] │
├──────────────────────────────┬───────────────────────────────────────┤
│ D-左：权限树（el-tree-v2 虚拟）│ D-右：已选清单（可虚拟）              │
│  ▾ ☑ 问题管理 (8/8)  [全选本组]│ 已选 34 项 [当前端▾][清空]           │
│    ▾ ☑ 项目 (4/4)           │  ▾ 问题管理 (8)                        │
│      ☑ 查看 issue:project:view ×│   · 查看项目 ×                      │
│      ☑ 新建 issue:project:create ×│  ▾ ⚠ 已失效 (2)                     │
│  ▾ ◪ 系统管理 (3/9)          │   · legacy:foo:bar ×                  │
├──────────────────────────────┴───────────────────────────────────────┤
│ E  内置角色提示（如适用）              [重置] [取消] [ 保存 ]           │
└──────────────────────────────────────────────────────────────────────┘
```

| 区 | 内容 |
|---|---|
| A 标题区 | 角色名，多角色连续配置时确认对象 |
| B 端切换+工具 | `el-segmented` 分段控件（带已选数徽标）+ 搜索框 + 全选/全选可见/反选/展开/收起 |
| C 统计条 | 已选总数 · 前后台分布 · 失效数 · 命中数 · 多条目提示 |
| D 主体双栏 | 左：权限树（选择）；右：已选清单（复核）。唯一滚动区 |
| E 底部操作 | 内置角色提示 + 重置 / 取消 / 保存 |

### 2.2 响应式断点

| 断点 | 布局 |
|---|---|
| ≥ 992px | 双栏（左 7 / 右 3），抽屉 1080px |
| < 992px | 上下堆叠；右栏折叠为一行摘要，点击展开为 40vh 面板；操作区吸底 |
| < 768px | 视图切换「权限树 / 已选(N)」互斥全屏 |

A/B/C/E 固定，仅 D 区内部滚动（左右栏各自独立滚动条）；树节点行高固定（桌面 32px / 窄屏 40px）以适配虚拟滚动。

---

## 3. 状态模型与状态流转

### 3.1 前端状态清单（均为新增 UI 状态，不影响请求体）

| 状态 | 类型 | 说明 |
|---|---|---|
| `allPermissions` | `PermissionVO[]` | 权限目录平铺数据 |
| `permIndexByCode` | `Map<string,PermissionVO>` | 目录索引，O(1) 反查 |
| `treeByType` | `{1:Node[], 2:Node[]}` | 由目录派生的两棵树（computed，仅目录变化时重建） |
| `activeTab` | `1 \| 2` | 当前端，默认 2（后台端） |
| `selectedByType` | `{1:Set<string>, 2:Set<string>}` | **核心**：两端各自独立的已选权限码集合 |
| `orphanSelected` | `Set<string>` | 角色拥有但目录中不存在的陈旧权限码 |
| `originalSelected` | `Set<string>` | 打开时的完整快照，用于 diff 与重置 |
| `keyword` / `debouncedKeyword` | `string` | 搜索输入（防抖 250ms 驱动过滤） |
| `matchedCurrentCodes` | `Set<string>` | 当前端命中搜索的叶子码（高亮 + 全选可见作用域） |
| `expandedKeys` | `{1:string[], 2:string[]}` | 两端各自展开节点，切换 Tab 不丢失 |
| `selectedScope` | `'current' \| 'all'` | 右侧清单范围：当前端 / 全部 |
| `loading` / `saving` / `error` | `boolean/object` | 加载 / 保存中 / 错误态（含重试入口） |
| `confirmVisible` / `diffPreview` | `bool/{added,removed}` | 保存前变更确认弹层与摘要 |
| `isBuiltinRole` | `boolean` | 内置角色 → 全树只读 + 提示 |

### 3.2 核心流转

```
打开抽屉 → 并行加载 目录 + 角色权限 → 按 type 拆分初始化
  → 搜索(防抖/高亮/自动展开祖先) / 勾选(更新 selectedByType[activeTab] → 重算祖先半选 → 同步右栏)
  → 全选/反选/分组批量(作用域=当前端+当前过滤) / 右栏移除单项 / 清空(二次确认)
  → 切换 Tab(仅切视图，不请求不保存，保留各自状态)
  → 点击保存(有变更→diff 确认→合并提交；无变更→按钮 disabled)
  → 成功(toast→关闭→刷新列表) / 失败(抽屉不关+错误条+重试)
```

### 3.3 初始化：角色权限码按 type 拆分回两个 Tab

并行 `listPermissions()` + `getRolePermissions(id)` 完成后：遍历角色码 → 在索引中则按 `normalizeType(vo.type)` 归入 `selectedByType[1/2]`，缺失则归入 `orphanSelected`；`originalSelected` = 全量快照。`normalizeType`：1→1，2→2，其它(null/0)→2 并归入该端末尾「未分类」虚拟分组（带 ⓘ 提示）。

### 3.4 提交：合并为原有数据格式

```
finalCodes = 去重([ ...selectedByType[1], ...selectedByType[2], ...orphanSelected ])
// 排序：按 sort 升序再 code 字典序；陈旧码追加末尾
PUT /api/roles/{id}/permissions  body = { permissionCodes: finalCodes }
```

**关键约定**：陈旧权限码（目录已无）在 UI 可见（右栏「已失效」分组、灰+⚠）、可主动移除，但**永不被系统隐式丢弃**——整体替换语义下不产生数据损失的保障。

### 3.5 父子联动与半选

真实数据源只有叶子集合 `selectedByType[activeTab]`；一二级节点状态全部由叶子推导（不单独存储）。
- 叶子全选 → 父 ☑；部分 → 父 ◪（indeterminate）；全不选 → 父 ☐。
- 点击父节点：选中/半选→取消其全部叶子；未选→选中其全部叶子。**搜索态下作用域收敛为可见（命中）叶子**。

---

## 4. 交互细节（对应 6 条需求）

1. **前后台切换**：`el-segmented` 分段控件，徽标显示各端已选数；两端状态完全独立（已选/展开/滚动各自保存）；切换不触发任何请求与保存；提交时合并。
2. **搜索/批量/联动**：关键字防抖 250ms 后 `tree.filter`，匹配 name/code/模块中文标签；命中片段 `<mark>` 高亮，命中项祖先自动展开（清空恢复此前展开态）；工具区 `全选/全选可见/反选/展开/收起`；分组行 hover 出「全选本组/清空本组」；父子联动与半选由 `el-tree-v2` 自动处理。
3. **信息层级重排**：左树选择、右清单复核；右栏按 module 分组、每项 `×` 即时移除并同步左树反选；头部「已选数 + 范围切换 + 清空（二次确认）」；统计条实时显示总数+前后台分布+失效数。
4. **视觉区分**：已选=主题色实心+行浅底；半选=横线+计数(x/y)；未选=默认；已失效=灰+⚠+标签（仅右栏）；内置只读=整体 disabled+提示；整行点击即切换勾选。
5. **保存确认/反馈/重试**：有变更点保存→diff 确认弹层（新增/移除明细+合计，陈旧移除标 ⚠，超 10 条折叠）；无变更→按钮 disabled+tooltip；成功 toast→关闭→刷新列表；失败→抽屉不关+状态零丢+错误条+重试按钮；saving 期间防重复；重试直接复用 finalCodes 不再弹 diff；连续失败 3 次追加提示。
6. **性能与适配**：左树 `el-tree-v2` 天然虚拟化；右栏 >100 项启用虚拟滚动；≥992px 双栏、<992px 堆叠/视图切换；搜索防抖 + 预建索引避免递归遍历。

---

## 5. 边界情况

| # | 场景 | 处理 |
|---|---|---|
| B1 | `type` 为 null/0/其它 | 归入后台端「未分类」分组，参与勾选与提交，不丢弃 |
| B2 | 目录整体为空 | 两 Tab 空状态，保存 disabled，仍展示陈旧权限 |
| B3 | 某端无权限 | Tab 可点，进入后空状态+快捷切换 |
| B4 | 搜索无结果 | 空状态+清除按钮，批量按钮 disabled |
| B5 | 超长列表 | 单端叶子 >200 启用虚拟滚动；>1000 默认折叠仅展开含已选项 |
| B6 | 内置角色 | 全只读、无保存按钮、保留 builtinTip，树/清单可浏览 |
| B7 | 陈旧权限码 | 右栏「已失效」分组、默认保留、可主动移除（进 diff 标 ⚠） |
| B8 | 保存网络错误/超时 | 抽屉不关、状态零丢、错误条+重试；超时 15s |
| B9 | 保存中重复点击 | saving 期间按钮 loading+disabled，仅一次请求 |
| B10 | 切换 Tab | 仅切视图，不请求不保存不清空任一端状态 |
| B11 | 有未保存变更关闭 | 弹「放弃变更」确认 |
| B12 | 加载中途关闭 | 取消未完成请求回调，再次打开完全重置 |
| B13 | code 段数不足 3 | 按实际段数收敛层级，末段为叶子 |
| B14 | 同一 code 重复 | 以 sort 最小者为准去重展示 |
| B15 | module 无 i18n 词条 | 回退显示原始 module 英文值 |
| B16 | 快速连续切换/输入 | 防抖 + 以最后一次为准，丢弃过期计算 |

---

## 6. 新增 i18n Key 清单（zh-CN 与 en-US 同名同层级补齐）

`role.tab.frontend/backend` · `role.tree.{uncategorized,uncategorizedTip,invertSelect,selectAllVisible,selectGroup,clearGroup,matchCount}` · `role.selected.{title,count,distribution,scopeCurrent,scopeAll,remove,clear,clearConfirm,stale,staleTip,staleCount}` · `role.diff.{title,subtitle,added,removed,total,viewAll,confirm,cancel}` · `role.empty.{noPermission,noPermissionInTab,noSearchResult,clearSearch}` · `role.tip.manyItems` · `role.msg.{noChanges,permSaveFailed,permSaveTimeout,permSaveFailedMulti,loadPermFailed,discardConfirm}` · `role.action.{retry,reload,reset}`。复用既有 `role.placeholder.searchPerm`、`role.tree.selectAll/expandAll/collapseAll`、`role.msg.permSaved/builtinTip`、`role.permModule.*`。

---

## 7. 交付文件

| 文件 | 操作 |
|---|---|
| `src/frontend/src/components/AssignPermissionDialog.vue` | 新建，分配权限全部新交互 |
| `src/frontend/src/views/admin/RoleManage.vue` | 修改，抽离弹窗为独立组件，保留列表与其余逻辑 |
| `src/frontend/src/components/FormDrawer.vue` | 修改（加法），新增 `xl` 尺寸档与 `beforeClose` 钩子 |
| `src/frontend/src/locales/zh-CN/role.js` / `en-US/role.js` | 修改，补全上述中英文案 |

> 验证结论：QA 一轮 IS_PASS；`vite build` 临时输出目录零报错通过（默认 `dist/` 写被本机 OS 安全删除拦截，与代码无关）；23 号 Docker 部署构建成功，冒烟 5 项全绿。
