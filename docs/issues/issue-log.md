# issueFlow 问题记录与实现笔记

> 记录实现过程中的关键决策、已知限制与 QA 修复项。与 [`CHANGELOG.md`](../CHANGELOG.md)、[`design-notes.md`](../design-notes.md) 配套阅读。

## 一、关键决策

- **编号生成**：`IS-YYYYMMDD-序号`（每日 0001 起，4 位补零），由 `IssueNoGenerator` 统计当日计数 + 1；并发由 `issue.issue_no` 唯一索引兜底，Service 捕获 `DuplicateKeyException` 重试一次。
- **逻辑删除**：`issue / issue_attachment / issue_history / user / tag` 走 MP 逻辑删除（`deleted`）；`role` / `sys_config` 无 `deleted` 字段，对应实体不继承 `BaseEntity`。
- **操作历史保留**：删除问题时级联逻辑删除附件与历史，但 `issue_history` 无 `ON DELETE CASCADE`，历史可追溯（已删问题不在列表但历史记录保留）。
- **状态机唯一来源**：`handler.StateMachine` 的 6 条转移表是流转唯一事实来源；`IssueFlowController` 仅暴露 `status/reopen/flow/config`。
- **主题默认值**：前端 `store/theme` 默认 `themeColor=#409EFF`、`layout=side`；后端 `SysConfigService.getPublicConfig` 在未配置时回退 `themeColor=#409EFF`、`layout=default`、`menuConfig={}`。

## 二、已知限制

1. **后端未在本机编译验证**：因生成环境无 JDK 17，后端代码仅产出、未做 `mvn package` 本地编译；架构与代码已严格对齐，建议用户在 JDK 17 环境 `docker compose up` 后实测。前端已 `npm run build` 通过。
2. **单 JWT 无 refreshToken**：token 有效期 2h，过期需重新登录；登出/强制失效依赖 Redis 黑名单。MVP 不做无感刷新。
3. **附件安全**：仅按 `content_type` + 大小（≤20MB）校验，**未做病毒扫描、无扩展名白名单**；存储于后端卷 `/data/attachments/{yyyyMM}/{uuid}.ext`。
4. **菜单配置简化**：菜单配置以支持开关（`showStats`/`showFlow`）+ 自由 JSON 的简化形态提供，未做拖拽/角色可见性的完整可视化菜单编排（PRD P2）。
5. **版本维度复用字符串**：看板"版本"直接复用 `issue.env_app_version` 字符串聚合，未建独立 `version` 字典表；筛选时由前端 `DashboardFilters` 可输入。
6. **状态机范围**：仅实现 6 条固定转移，**未实现** PRD/架构中提到的"管理员从任意态强制置 OPEN 纠偏"；重开仅支持 `CLOSED→OPEN` 且需 `flow_reopen_enabled`，无次数上限。
7. **乐观锁未启用**：`issue` 表无 `version` 字段，MVP 不启用 MP 乐观锁（`MybatisPlusConfig` 已预留 `OptimisticLockerInnerInterceptor` 注释位）。
8. **并发计数**：编号生成依赖"查当日计数 + 1"，高并发下依赖唯一索引兜底重试，极端场景可能存在短暂重试开销。

## 三、QA 阶段修复

1. **IssueTable 筛选字段对齐 `startDate`/`endDate`**：问题列表筛选的时间范围，组件内部字段曾为 `startTime`/`endTime`，已对齐为请求参数 `startDate`/`endDate`（与后端 `IssuePageReq` 一致），避免筛选条件被后端忽略。
2. **附件预览改 fetch blob 携带 token**：图片预览原为 `<img src>` 直链，因携带不了 `Authorization` 在鉴权下会 401；已改为 `previewAttachment` 经 Axios（自动注入 Bearer token）取 Blob 再 `URL.createObjectURL` 渲染，新标签页打开，并在卸载时 `revokeObjectURL` 释放。

## 四、待确认事项（技术层面，见 ARCHITECTURE.md §9.4）

- 是否需要 refreshToken 无感刷新？
- 看板导出分工（PNG 前端 / Excel 后端）是否可接受？
- 版本是否需要独立字典表？
- 高并发是否需要启用乐观锁？
- 附件是否需要病毒扫描 / 扩展名白名单？
