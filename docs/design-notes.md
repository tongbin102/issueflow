# issueFlow 设计思路摘要

> 本文档为 issueFlow 设计要点的高层摘要，便于快速建立全局认知。
> 详细接口、数据模型与任务分解见 [`architecture.md`](./architecture.md)；产品目标与权限矩阵见 [`prd.md`](./prd.md)。
> 架构决策记录见 [`adr/`](./adr/)；变更历史见 [`CHANGELOG.md`](./CHANGELOG.md)。

## 1. 分层架构（后端）

```
Controller（REST / 校验 / 鉴权）
   ↓
Service（业务 / 事务 / 状态机 / 编号生成 / 数据范围）
   ↓
Mapper（MyBatis-Plus 持久化 / 分页 / 逻辑删除）
   ↓
Entity（表映射 / 枚举）
横切：security(JWT Filter) · common(Result/异常/常量) · util · config · handler
```

- **Controller**：薄层，负责路由、参数校验（`@Valid`）、调用 Service，不直接写业务逻辑。
- **Service**：业务核心，`@Transactional` 保证一致性；数据范围（SUBMITTER 仅己）在此判定。
- **Mapper**：MyBatis-Plus 标准 CRUD + 看板聚合自定义 `@Select`（`IssueMapper`/`IssueHistoryMapper`）。
- **Entity/Enums**：表映射与状态/角色/严重等级/动作枚举，中文 `desc` 在 VO 组装时反查填充。

## 2. 统一返回与异常处理

- 统一 `Result<T>{code, message, data, timestamp}`，成功 `code=200`；分页 `PageResult<T>{list,total,page,size}`。
- 业务异常 `BizException(code, msg)` 由 `GlobalExceptionHandler`（`@RestControllerAdvice`）统一包装为 `Result`；含参数校验、AccessDenied(403) 兜底。
- `ResultCode` 枚举集中管理业务码（如 `1001 ISSUE_NOT_FOUND`、`1002 STATUS_TRANSITION_DENIED`、`1004 PERMISSION_DENIED`）。

## 3. JWT 无状态认证

- 登录：`BCrypt` 校验 → `JwtUtil.generate(userId, roleCode)`（HS256，payload 含 `jti`/`exp`，2h）。
- 请求：`JwtAuthenticationFilter` 校验签名与有效期 → 比对 **Redis 黑名单**（`jwt:blacklist:{jti}`，TTL=剩余有效期）→ 注入 `SecurityContext`。
- 登出：写黑名单使 token 提前失效；未认证统一 401（`RestAuthenticationEntryPoint`），越权统一 403。

## 4. 状态机驱动流转

- `handler.StateMachine` 以 6 条转移表为唯一事实来源，按 `roleCode` 与 `flow_*` 开关判定 `isAllowed`。
- 非法转移抛 `1002`；每次流转经 `IssueHistoryService.record` 写操作历史（操作人/时间/源→目标/备注）。
- 前端 `StatusFlowButtons` 依据状态+角色+开关渲染可点按钮，回退/关闭弹框填备注。
- 详见 [ADR 003](./adr/003-问题状态机与流转规则.md)。

## 5. ECharts 看板

- 后端 `DashboardService.overview` 聚合 5 项指标（趋势/状态分布/严重占比/平均周期/解决率），`export` 经 EasyExcel 出 xlsx。
- 前端 `charts/TrendChart`、`charts/DistributionChart` 渲染；导出分工：PNG 由前端 ECharts `getDataURL`，Excel 由后端生成（数据口径以后端为准）。
- SUBMITTER 看板仅统计自己提交的问题。

## 6. Docker 分层部署

- `docker-compose.yml` 编排 `mysql / redis / backend / frontend` 四服务，数据卷持久化。
- 后端 `jar`（`finalName=issueflow-backend`）由 `Dockerfile` 构建；前端 `dist/` 经 nginx 托管。
- 首次启动自动建表（`db/schema.sql`）+ 初始化角色（`data.sql`）+ 写入默认管理员 `admin/admin123`（`ApplicationRunner`）。

## 7. 主题与权限联动

- 主题以 CSS 变量（`--theme-color` 等）注入 `:root`，优先级：用户本地 > 后端配置 > 默认。
- 页面级权限由路由 `meta.roles` + 守卫；按钮级由 `v-permission` 指令；二者互补。
- 详见 [ADR 002](./adr/002-rbac与数据隔离设计.md)。
