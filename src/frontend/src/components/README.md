# components 目录 — 可复用组件

> 均使用 `<script setup>`；组件只负责展示与交互，数据经由 `api/*` 获取。

## IssueForm.vue（新建/编辑结构化表单）
- 职责：问题标题、严重等级、分类标签（多选可创建）、详细描述、复现步骤、环境信息（OS/浏览器/版本/设备）、附件（仅新建时）。
- Props：`initial`（编辑回显对象，含 `tags` 逗号串）。
- Emits：`submit({data, files})`、`cancel`。
- 依赖：`api/tag`、`AttachmentUploader`、`utils/format(SEVERITY_OPTIONS)`。

## IssueTable.vue（问题列表表格）
- 职责：筛选区（状态/等级/标签/版本/关键词/时间范围）+ 表格（编号/标题/等级/状态/提交人/处理人/时间/操作）+ 分页。
- Props：`scope`('mine'|'all')、`filters`。
- Emits：`view`、`edit`；`defineExpose({ fetchData })` 供父组件刷新。
- 权限：编辑/删除按钮按 `userStore.isAdmin || reporterId===当前用户` 显隐；"流转"跳详情抽屉。
- 依赖：`api/issue`、`api/tag`、`utils/format`、`store/user`。

## IssueDetailDrawer.vue（问题详情抽屉）
- 职责：`el-drawer` 展示描述/环境/复现/附件（`AttachmentUploader`）/流转操作（`StatusFlowButtons`）/操作历史（`StatusTimeline`）。
- Props：`modelValue`(v-model)、`issueId`、`flowConfig`。
- Emits：`update:modelValue`、`updated`。
- 依赖：`api/issue`、`AttachmentUploader`、`StatusFlowButtons`、`StatusTimeline`。

## StatusTimeline.vue（操作历史时间线）
- 职责：`el-timeline` 渲染历史，展示动作、操作人、状态转移（`from→to`）、备注，颜色按目标状态。
- Props：`history`(Array)。
- 依赖：`utils/format(statusLabel/actionLabel/statusColor)`。

## StatusFlowButtons.vue（按角色渲染流转按钮）
- 职责：依据 当前状态 + 角色 + 流程开关 计算可点按钮（认领/提交修复/验证通过/回退/关闭/重开），回退与关闭弹框填备注。
- Props：`status`、`issueId`、`flowConfig{rejectEnabled,reopenEnabled}`。
- Emits：`changed`；调用 `api/issue` 的 `changeStatus`/`reopenIssue`。
- 依赖：`store/user`、`utils/format`。

## AttachmentUploader.vue（附件上传/预览/下载/删除）
- 职责：双模式——
  - 详情模式（`issueId` 存在）：展示已上传列表 + 服务端上传/删除，图片用 `previewAttachment`(fetch blob) 预览、非图片下载。
  - 新建模式（`issueId` 空）：仅本地收集文件（`defineExpose({getFiles,clear})`），随表单提交。
- Props：`issueId`、`attachments`、`maxSizeMB`(默认20)；Emits：`change`/`uploaded`/`removed`。
- 依赖：`api/issue`、`utils/exportUtil(downloadBlob)`。

## charts/TrendChart.vue（趋势折线图）
- 职责：ECharts 折线，数据 `[{date|day, count}]`。
- Props：`data`、`height`(默认320px)、`title`。
- `defineExpose({ setOption, exportPng, getInstance })`；导出调用 `utils/exportUtil.exportChartPng`。

## charts/DistributionChart.vue（分布图）
- 职责：ECharts 饼图（状态分布）+ 柱状图（严重等级占比）。
- Props：`data{statusDistribution, severityRatio}`、`height`。
- `defineExpose({ setOption, exportPng, getInstance })`。

## DashboardFilters.vue（看板筛选条）
- 职责：时间范围（daterange）+ 版本（可输入）筛选，双向 `v-model` + `search` 事件。
- Props：`versions`、`modelValue{start,end,version}`；Emits：`update:modelValue`、`search`。

## ThemeConfigPanel.vue（主题/布局/菜单配置面板）
- 职责：主题色（color-picker）、布局模式（side/top/mix）、菜单开关（showStats/showFlow）+ 菜单 JSON，即时生效并写后端。
- 依赖：`store/theme`、`api/sysConfig(getConfig/setConfig)`。

## 依赖关系
```
IssueForm        → AttachmentUploader, api/tag, utils/format
IssueTable       → api/issue, api/tag, store/user, utils/format
IssueDetailDrawer→ AttachmentUploader, StatusFlowButtons, StatusTimeline, api/issue
StatusFlowButtons→ store/user, api/issue, utils/format
AttachmentUploader→ api/issue, utils/exportUtil
charts/*         → echarts, utils/exportUtil, utils/format
DashboardFilters → 无业务依赖（仅 emit）
ThemeConfigPanel → store/theme, api/sysConfig
```
