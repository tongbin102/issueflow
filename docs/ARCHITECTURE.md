# issueFlow 问题提报平台 - 系统架构设计（ARCHITECTURE）

> 文档版本：v1.0
> 文档日期：2026-08-04
> 文档状态：**权威当前态** —— 取代 `docs/archive/2026-08-04/architecture.md`（Phase 1 基线，已归档）与 `docs/archive/2026-08-04/` 下其余 ARCH 系列文档；各历史版本保留在归档目录作为演进快照（不删除）。
> 架构师：高见远
> 技术栈：Spring Boot 3.2 + MyBatis-Plus 3.5.x + MySQL 8 + Redis 7 + JWT + Spring Security 6 + Knife4j + Vue3 + Element Plus + Vue Router 4 + Pinia + Axios + ECharts + Vite + Docker Compose

---

## 版本历史

| 版本 | 日期 | 来源文档 | 演进要点 |
| --- | --- | --- | --- |
| v1.0 | 2026-07-30 | `docs/archive/2026-08-04/architecture.md`（基线，已归档） | 全量架构：分层、选型、实体模型、REST API、状态机、任务分解 |
| v1.1 | 2026-07-30 | `incremental-design-phase2.md` | 问题关联、权限目录、角色管理、菜单按端动态渲染 |
| v1.2 | 2026-07-30 | `incremental-design-phase3.md` | 项目负责人/成员、流程配置迁移、风格设置抽屉 |
| v1.3 | 2026-07-30 | `incremental-design-phase4.md` | 模块树、模块关联、流程管理菜单、联动约束 |
| v1.4 | 2026-07-31 | `incremental-design-phase5.md` | 流程数据模型落库、FormDrawer 规范、组织树、数据初始化 |
| v1.5 | 2026-07-31 | `ARCH_phase6.md` | 问题类型、i18n、主题、弹窗改抽屉、网站设置 |
| v1.6 | 2026-08-01 | `ARCH_phase7.md` | 字典配置、来源/优先级、个人中心、基础设施、备份 |
| v2.0 | 2026-08-04 | **本文档 `docs/ARCHITECTURE.md`** | 多版本整合为单一权威文档：架构总览 + 决策 + 数据模型 + 接口 + 流程 + 演进 + 共享知识 + 待明确 |

> **专项架构文档（不合并，独立文档）**：`docs/ARCH-frontend-redesign-v1.0-2026-08-01.md`（前台 UI/UX 重构）、`docs/ARCH-security-refactor-v1.0-2026-08-01.md`（安全加固与可维护性重构）、`docs/ARCH-dynamic-field-v1.0-2026-08-01.md`（问题类型下沉字典 + 动态字段配置），详见第七章。
> **归档说明**：上表及下文中未带完整路径的来源文档（如 `incremental-design-phaseN.md` / `ARCH_phaseN.md`）均位于 `docs/archive/2026-08-04/` 下（同名保留）。
> **类图 / 时序图**：当前最新图见 `docs/diagrams/class-diagram-latest.mermaid`、`docs/diagrams/sequence-diagram-latest.mermaid`；各期图见 `docs/diagrams/class-diagram-phaseN.mermaid`、`docs/diagrams/sequence-diagram-phaseN.mermaid`。

---

## 一、架构总览

### 1.1 分层架构（后端）

```
┌─────────────────────────────────────────────┐
│ Controller 层   REST 端点 / 参数校验 / 鉴权注解 │
├─────────────────────────────────────────────┤
│ Service 层     业务逻辑 / 事务 / 状态机 / 编号生成 │
├─────────────────────────────────────────────┤
│ Mapper 层      MyBatis-Plus 持久化 / 分页 / 逻辑删除 │
├─────────────────────────────────────────────┤
│ Entity 层      表映射 / 枚举                       │
└─────────────────────────────────────────────┘
横切：Security(JWT Filter) · common(Result/异常/常量) · util · config · handler
```

- **Controller**：薄层，负责路由、参数校验（`@Valid`）、调用 Service，不直接写业务逻辑。
- **Service**：业务核心，`@Transactional` 保证一致性；数据范围（SUBMITTER 仅己）在此判定；权限校验统一在 **Service 首行** `permissionService.requirePermission(code)`。
- **Mapper**：MyBatis-Plus 标准 CRUD + 看板聚合自定义 `@Select`。
- **Entity/Enums**：表映射与状态/角色/严重等级/动作枚举，中文 `desc` 在 VO 组装时反查填充。

### 1.2 技术选型与理由

| 层 | 选型 | 理由 |
|---|---|---|
| 后端框架 | Spring Boot 3.2 | 内置 Spring Security 6 / Spring MVC / 调度，生态完整 |
| 持久层 | MyBatis-Plus 3.5.x（`mybatis-plus-spring-boot3-starter`） | 通用 Mapper/Service、分页、逻辑删除、自动填充，减少 70% CRUD 样板 |
| 安全 | Spring Security 6 + JWT（jjwt 0.12.x） | `SecurityFilterChain` Bean；`JwtAuthenticationFilter` 注入 `SecurityContext`；无状态（STATELESS）；登出/强制失效用 Redis 黑名单（`jwt:blacklist:{jti}`，TTL=剩余有效期） |
| 缓存 | Redis 7 | Token 黑名单、看板统计缓存、字典缓存、任务并发互斥 |
| 存储 | MySQL 8 + Docker 卷 | 附件本地卷 `/data/attachments/{yyyyMM}/{uuid}.ext`，DB 仅存相对路径 |
| 前端 | Vue3 + Vite + Element Plus + Pinia + Vue Router 4 + Axios + ECharts + vue-i18n | 组合式 API；按角色动态布局；`store/{user,theme,app,locale,dict}`；Axios 拦截器统一解包 `Result<T>` |
| API 文档 | Knife4j（OpenAPI3） | `/doc.html` 交互式文档，便于联调与 QA |
| 部署 | Docker Compose | `mysql / redis / backend / frontend` 四服务，数据卷持久化 |

### 1.3 架构模式与横切约定

- **统一返回**：`Result<T>{code, message, data, timestamp}`，成功 `code=200`；分页 `PageResult<T>{list,total,page,size}`；业务异常 `BizException(code, msg)` 由 `GlobalExceptionHandler`（`@RestControllerAdvice`）统一包装。
- **JWT 无状态认证**：登录 `BCrypt` 校验 → `JwtUtil.generate(userId, roleCode)`（HS256，payload 含 `jti`/`exp`，2h）；请求经 `JwtAuthenticationFilter` 校验签名与有效期 → 比对 Redis 黑名单 → 注入 `SecurityContext`；未认证 401、越权 403。
- **状态机驱动流转**：`handler.StateMachine` 以流转表为唯一事实来源（Phase 5 起数据驱动），按 `roleCode` 与开关判定 `isAllowed`；非法转移抛 `1002`；每次流转经 `IssueHistoryService.record` 写操作历史。
- **ECharts 看板**：后端 `DashboardService.overview` 聚合 5 项指标；PNG 前端 `getDataURL` 导出、Excel 后端 EasyExcel；SUBMITTER 看板仅统计自己。
- **主题**：CSS 变量（`--theme-color` 等）注入 `:root`（前台主题只写 `document.body[data-if-theme]`）；优先级：用户本地 > 后端配置 > 默认；4 套主题 light/dark/blue/green。
- **权限**：页面级由路由 `meta.roles` + 守卫；按钮级由 `v-permission` 指令；Phase 2 起 `permission` + `role_permission` 体系，ADMIN 放行全部。

---

## 二、关键架构决策

### 2.1 基线已拍板决策（Phase 1）

| 决策 | 落地方式 |
|---|---|
| 流程配置 MVP：仅启用/禁用回退与重开 | `sys_config` 存 `flow_reopen_enabled` / `flow_reject_enabled`；`StateMachine` 读开关 |
| 附件本地卷 ≤20MB，图片预览 | `AttachmentController` + `FileUtil` 落 `/data/attachments`；`/api/attachments/{id}/preview` 内联 |
| 编号 `IS-YYYYMMDD-序号`（每日 0001 起） | `IssueNoGenerator` 按日期计数生成，DB 唯一索引兜底 |
| 已关闭重开仅管理员、无次数上限 | `StateMachine` 允许 `CLOSED→OPEN` 仅 `ADMIN` |

### 2.2 Phase 7 四大关键决策（A~D）

#### 决策 A — 问题类型保持一级平铺
- 迁移脚本**不触碰** `/admin/issue-types` 的 `parent_id`（保持 0）。
- 后台一级菜单最终排序：`概览(1) / 业务管理(2) / 问题类型(3) / 流程监控(4) / 流程管理(5) / 基础设施(6) / 系统管理(7)`。
- 「项目管理」分组迁空后 `deleted=1`，不留空分组；PRD §5.3 草图把「问题类型」画在业务管理下 —— 以主理人裁定为准，**UI 草图此处作废**，验收按本文档。

#### 决策 B — 优先级固定枚举（仿 severity）
| 项 | 规格 |
| --- | --- |
| 列 | `issue.priority TINYINT NOT NULL DEFAULT 1` |
| 取值 | `0=高(HIGH) / 1=中(MEDIUM) / 2=低(LOW)`，**数值越小越紧急**，与 `severity` 方向一致 |
| 后端 | `enums/PriorityEnum.java`；`IssueCreateReq.priority` 加 `@NotNull @Min(0) @Max(2)`；VO 返回 `priority` + `priorityDesc` |
| 前端 | `usePriorityOptions()` / `priorityLabelI18n(code)` / `priorityTagType(code)` → `danger/warning/info`（固定色值，不随主题变） |
| 布局 | 表单中与「严重等级」同一 `el-row` 并排两个 `el-col :span="12"` |
| 存量 | `UPDATE issue SET priority=1 WHERE priority IS NULL`（加列即 DEFAULT 1） |

> **为何不字典化**：字典化会让优先级下拉走异步 store，与 severity 的同步枚举下拉产生**加载时序差**，直接违反 R4「用户不应感知两者是不同批次开发」的硬要求。

#### 决策 C — 定时任务用 Spring TaskScheduler 动态注册
```
ScheduledTaskService ──(CRUD/启停/立即执行)──> DynamicTaskScheduler
                                                 ├─ ThreadPoolTaskScheduler(poolSize=4, 优雅停机)
                                                 ├─ Map<Long, ScheduledFuture<?>> registry
                                                 └─ Map<String, ScheduledJob> jobRegistry (Spring 注入)
                                                        ├─ CleanExpiredFileJob   (内置示例1)
                                                        └─ CleanLoginLogJob      (内置示例2, 清理 90 天前登录日志)
```
- **启动装载**：`@EventListener(ApplicationReadyEvent.class)`（**不用 `@PostConstruct`** —— Phase 5 教训：该阶段 Mapper/数据源可能未就绪）。
- **cron 校验**：`CronExpression.isValidExpression(cron)`（Spring 6 原生）；**立即执行**用 Redis `SETNX job:running:{id}` 防重入；执行日志保留最近 200 条。
- **单机约束**：多实例部署会重复触发，届时升 Quartz 集群（表结构已按「任务定义与调度器解耦」设计）。

#### 决策 D — 备份范围与安全边界
| 维度 | 规格 |
| --- | --- |
| 全量范围 | 业务表 + 配置表，共约 22 张（见 §3.9） |
| 核心配置范围 | `sys_config`、`menu`、`permission`、`role`、`role_permission`、`flow_node`、`flow_transition`、`issue_type`、`dict_type`、`dict_item`、`scheduled_task` —— 共 11 张（**不含** organization/project/module） |
| 附件 | **不导出二进制**；`issue_attachment` / `file_record` 元信息行照常导出（含 `file_path`），文件头标注 `attachmentBinaryIncluded: false` |
| 敏感列 | 「全量」范围导出 `user` 时 **`password` 列输出为 `"***"`**；文件头标注 `passwordMasked: true` |
| 保护阈值 | 单表 > 20 万行 或 累计写出 > 200MB 时中断并抛业务异常；接口自身 `@Transactional(readOnly=true)` 但不设长事务 |
| 权限 | 新权限码 `system:backup:export`（+ ADMIN 天然放行），未授权 403 |

### 2.3 架构约束（继承 + 新增）

1. **唯一索引铁律**（继承）：任何唯一约束必须「生成列 + 条件唯一」，**禁止** `(col, deleted)` 复合。Phase 7 唯一新增：`dict_item.code_active = CONCAT(type_id,':',code)`。
2. **主题隔离**（继承）：前台主题只写 `document.body[data-if-theme]`，严禁 `documentElement`。
3. **i18n 只做 UI 文案**（继承）：`dict_item.name` 不入库多语言；前端先查 `t('dict.value.' + typeCode + '.' + code)`，未命中回退数据库 `name`。
4. **弹出层唯一形态**（继承）：一律 `FormDrawer`（sm=480 / md=620 / lg=800，≤768px 满宽），仅 `ElMessageBox` / `ElMessage` / `ElNotification` 例外。
5. **新增**：个人中心所有接口**不接受 userId 入参**，一律 `SecurityUtils.getCurrentUserId()` —— 接口签名层面消灭越权可能。
6. **新增**：文件下载/预览一律经 `FileRecordService` 校验后由后端读流回传，禁止暴露绝对路径给前端拼 URL；路径穿越防护在 `FileUtil.resolveSafe(base, relative)` 做 `normalize().startsWith(base)` 断言。
7. **部署顺序硬约束**：**先灌 SQL → 再重启后端**；`DynamicTaskScheduler` 在 `ApplicationReadyEvent` 读 `scheduled_task`，表不存在会告警（已 try/catch 降级，但仍须守序）。

---

## 三、数据模型

### 3.1 核心实体（基线 + Phase 7 增量）

**User（用户）**：`id / username(UNIQUE) / password(BCrypt) / real_name / email / phone / role_id(FK) / status / avatar(Phase7+) / nickname(Phase7+) / leader_id(Phase5+) / pwd_updated_at(Phase7+) / org_id(演进) / created_at / updated_at / deleted`

**Issue（问题主表）**：`id / issue_no(UNIQUE, IS-YYYYMMDD-0001) / title / description / severity(0致命1严重2一般3轻微) / priority(Phase7: 0高1中2低, DEFAULT 1) / type_id(Phase6+) / source_id(Phase7: 来源 dict_item.id) / module_id(Phase4+) / project_id(Phase2+) / tags / reproduce_steps / env_* / status(0待处理1处理中2待验证3验证通过4已关闭) / reporter_id / assignee_id / closed_at / created_at / updated_at / deleted`

**Role（角色字典）**：`id / code(SUBMITTER/DEVELOPER/TESTER/ADMIN) / name / description`（Phase 2 起配合 `permission` + `role_permission` 体系）

**IssueAttachment（附件）**：`id / issue_id / file_name / original_name / file_path / file_size / content_type / uploader_id / created_at / deleted`（Phase 7 起与 `file_record` 双写）

**IssueHistory（操作历史）**：`id / issue_id / action / from_status / to_status / operator_id / remark / created_at / deleted`

**Tag（分类标签字典）｜SysConfig（配置）**：`id / name(UNIQUE) / color`；`id / config_key(UNIQUE) / config_value(JSON) / description / updated_at`

### 3.2 Phase 7 新增表

**dict_type（字典类型）**：`id / name / code(UNIQUE, 创建后不可改) / description / sort / enabled / is_system / code_active(生成列条件唯一) / created_at / updated_at / deleted`。种子（均 `is_system=1`）：`ISSUE_SOURCE 问题来源`、`ISSUE_STATUS 问题状态`、`ISSUE_PRIORITY 优先级`（只读展示）、`ISSUE_SEVERITY 严重等级`（只读展示）。

**dict_item（字典项）**：`id / type_id / name / code / description / sort / enabled / is_system / extra(预留 P2 颜色) / code_active(生成列: CONCAT(type_id,':',code) 条件唯一) / created_at / updated_at / deleted`。`ISSUE_SOURCE` 种子：`MANUAL 系统录入(1) / API_IMPORT 接口导入(2) / EXCEL_IMPORT 批量导入(3) / EMAIL 邮件反馈(4) / OTHER 其他(99)`；`ISSUE_STATUS` 种子镜像现有枚举 0-4。

**login_log（登录日志，Phase 7）**：`id / user_id(NULL 失败场景) / username / ip(X-Forwarded-For 首段→X-Real-IP→getRemoteAddr) / user_agent / browser / os / success / fail_reason / login_at / created_at / updated_at / deleted`。索引 `(user_id, login_at DESC)`、`(login_at)`。

**file_record（统一文件视图，Phase 7）**：`id / file_name(uuid.ext) / original_name / relative_path(yyyyMM/uuid.ext) / file_path(绝对路径) / file_size / content_type / ext / biz_type(ISSUE/AVATAR/MANUAL) / biz_id / uploader_id / storage_type(LOCAL) / created_at / updated_at / deleted`。迁移脚本将存量 `issue_attachment` 一次性回灌，两表并存（`issue_attachment` 仍为问题详情数据源，新增附件双写）。

**scheduled_task（定时任务，Phase 7）**：`id / task_name / task_group / job_key(白名单) / cron(Spring 6 位) / params(JSON) / status(1运行0暂停) / description / last_exec_time / last_exec_result / last_cost_ms / created_at / updated_at / deleted`。`next_exec_time` 不落库，实时计算。内置任务：`清理过期临时文件`、`清理过期登录日志`（90 天）。

**scheduled_task_log（任务执行日志，Phase 7）**：`id / task_id / start_time / cost_ms / success / message(VARCHAR 2000) / trigger_type(CRON/MANUAL) / created_at / updated_at / deleted`。

**其他演进表**：`issue_relation`（Phase 2 前置/后置关联）、`permission` / `role_permission`（Phase 2 权限体系）、`module`（Phase 4 模块树）、`flow_node` / `flow_transition`（Phase 5 流程数据模型）、`organization`（Phase 5 扩展 code/leader_id/status）、`issue_type`（Phase 6 问题类型）。

### 3.3 枚举 / 字典值

- **RoleEnum**：`SUBMITTER`(提交者) `DEVELOPER`(开发人员) `TESTER`(测试人员) `ADMIN`(管理员)
- **IssueStatusEnum**：`OPEN=0`(待处理) `IN_PROGRESS=1`(处理中) `PENDING_VERIFY=2`(待验证) `VERIFIED=3`(验证通过) `CLOSED=4`(已关闭)
- **SeverityEnum**：`FATAL=0`(致命) `SERIOUS=1`(严重) `NORMAL=2`(一般) `MINOR=3`(轻微)
- **PriorityEnum**（Phase 7）：`HIGH=0`(高) `MEDIUM=1`(中) `LOW=2`(低)
- **HistoryActionEnum**：`CREATE` `CLAIM` `SUBMIT_FIX` `VERIFY_PASS` `VERIFY_REJECT` `CLOSE` `REOPEN` `EDIT`

### 3.4 类图

当前最新类图见：**`docs/diagrams/class-diagram-latest.mermaid`**（来源：Phase 7 抽取，覆盖字典/个人中心/基础设施/备份等 Phase 7 全量类，并保留基线 `BaseEntity` 骨架）。

各期类图：`docs/diagrams/class-diagram-phase2.mermaid` ~ `class-diagram-phase5.mermaid`、`class-diagram-phase7.mermaid`；根目录基线 `docs/class-diagram.mermaid`、`docs/incremental-class-diagram.mermaid`。


---

## 四、接口设计

### 4.1 统一返回结构

```json
{ "code": 200, "message": "success", "data": <T>, "timestamp": 1690000000000 }
```
错误：`{ "code": <ResultCode>, "message": "<错误信息>", "data": null, "timestamp": ... }`

**ResultCode**（节选）：`200 SUCCESS`、`401 UNAUTHORIZED`、`403 FORBIDDEN`、`404 NOT_FOUND`、`400 VALID_ERROR`、`500 SYSTEM_ERROR`、`1001 ISSUE_NOT_FOUND`、`1002 STATUS_TRANSITION_DENIED`、`1003 FILE_TOO_LARGE`、`1004 PERMISSION_DENIED`。

### 4.2 基线 REST API 端点（Phase 1 定稿）

> 前缀 `/api`；角色列：S=提交者 D=开发 T=测试 A=管理员；`*`=任意登录用户；鉴权：除登录外均需 JWT。

| # | Method | Path | 角色 | 说明 |
|---|---|---|---|---|
| 1 | POST | /api/auth/login | 公开 | LoginReq → LoginVO |
| 2 | POST | /api/auth/logout | * | 写 Redis 黑名单 |
| 3 | GET | /api/auth/info | * | 当前用户信息 |
| 4 | POST | /api/issues | * | 创建（可附附件） |
| 5-8 | PUT/DELETE/GET/GET | /api/issues/{id}、/api/issues | 按角色 | 编辑/删除/详情/分页多条件 |
| 9 | GET | /api/issues/{id}/history | 按角色 | 操作历史 |
| 10-11 | POST | /api/issues/{id}/status、/reopen | 按转移规则 | 状态流转 / 管理员重开 |
| 12-15 | POST/GET/GET/DELETE | /api/issues/{id}/attachments、/api/attachments/... | 按角色 | 附件上传/下载/预览/删除 |
| 16-19 | GET/POST/PUT/DELETE | /api/users | A | 用户管理 |
| 20 | GET | /api/roles | * | 角色列表 |
| 21 | GET/POST/PUT/DELETE | /api/tags | A(写)/*(读) | 标签 |
| 22-23 | GET | /api/dashboard/overview、/export | 按角色 | 看板 / Excel 导出 |
| 24-25 | GET/PUT | /api/flow/config | A | 流程开关（Phase 5 起数据模型化） |
| 26-27 | GET/PUT | /api/sys/config | *(读)/A(写) | 系统配置 |

### 4.3 Phase 7 新增接口（摘要，完整见归档 `ARCH_phase7.md` §3.8）

- **字典配置 `/api/dicts`**：`types` CRUD（预设/引用保护）、`items` CRUD + `status` 启停 + 引用计数、`options?typeCode=&includeDisabled=`（全站下拉唯一数据源，登录即可）。
- **个人中心 `/api/profile`**（全部仅操作当前登录用户，无 userId 入参）：`GET/PUT /api/profile`、`POST /avatar`、`PUT /password`（改密强制登出）、`PUT /binding`、`GET /activities`（登录日志 + 本人 issue_history 归并时间线）、`GET /avatar/{userId}`（头像只读端点）。
- **文件管理 `/api/admin/files`**：分页/上传/下载/预览/删除 + `config` 读写（`file:list/upload/delete/config`）。
- **配置管理 `/api/admin/configs`**：分页 + 键唯一校验 + 内置前缀保护（`site.`/`file.`/`flow_`/`theme_`/`layout`/`menu_config` 禁删）。
- **Redis 监控 `/api/admin/redis/info`**：`redis:monitor`；异常时 HTTP 200 + `available=false` + `errorMessage`（不抛 500，只读无写命令）。
- **定时任务 `/api/admin/jobs`**：CRUD / `status` 启停 / `run` 立即执行（SETNX 互斥）/ `logs` / `options`（jobRegistry 白名单）。
- **备份 `/api/admin/backup`**：`estimate`（表数/条数/建议文件名）、`export`（流式二进制；失败返回 JSON 错误体，前端按 Content-Type 判定）。
- **既有接口增量**：`/api/issues` 增 `sourceId`/`priority`；`/api/auth/info` 增 `avatar`/`nickname`；`/api/auth/login` 内部新增登录日志埋点。
- **新增权限码（22 个）**：`dict:*`(4)、`file:*`(4)、`config:*`(4)、`redis:monitor`、`job:*`(5)、`system:backup:export`、`infra:view`、`business:view`。

### 4.4 状态流转角色规则（`StateMachine`）

| 起始→目标 | 触发角色 | 备注必填 |
|---|---|---|
| OPEN→IN_PROGRESS | D / A | 认领 |
| IN_PROGRESS→PENDING_VERIFY | D / A | 提交修复 |
| PENDING_VERIFY→VERIFIED | T / A | 验证通过 |
| PENDING_VERIFY→IN_PROGRESS | T / A（需 `flow_reject_enabled`） | 回退，必填原因 |
| VERIFIED→CLOSED | T / A | 关闭，写 closed_at |
| CLOSED→OPEN | A（需 `flow_reopen_enabled`，无次数上限） | 重开 |

> 正向流转按角色权限；回退仅 `PENDING_VERIFY→IN_PROGRESS`，除管理员外仅测试可触发；管理员可从任意态强制置 `OPEN`。Phase 5 起该规则表由 `flow_transition` 数据驱动。

---

## 五、程序调用流程

当前最新时序图见：**`docs/diagrams/sequence-diagram-latest.mermaid`**（来源：Phase 7 抽取，覆盖个人中心资料编辑/头像上传、改密强制登出、字典 CRUD、Redis 监控降级、文件上传下载、定时任务、备份导出等关键流程）。

各期时序图：`docs/diagrams/sequence-diagram-phase2.mermaid` ~ `sequence-diagram-phase5.mermaid`、`sequence-diagram-phase7.mermaid`；根目录基线 `docs/sequence-diagram.mermaid`、`docs/incremental-sequence-diagram.mermaid`。

关键流程一览（详细时序见上述图与归档文档）：

| 流程 | 说明 |
|---|---|
| 提交问题 | `IssueCreate.vue → POST /api/issues → IssueService → IssueNoGenerator(IS-YYYYMMDD-NNNN) → IssueMapper → DB → IssueHistoryService.record(CREATE)` |
| 状态流转 | `StatusFlowButtons → POST /api/issues/{id}/status → IssueFlowService → StateMachine.isAllowed(roleCode) → IssueHistoryService.record` |
| 登录鉴权 | `Login.vue → POST /api/auth/login → BCrypt 校验 → JwtUtil.generate → 返回 token；后续请求经 JwtAuthenticationFilter → Redis 黑名单比对 → SecurityContext` |
| 看板统计 | `Dashboard.vue → GET /api/dashboard/overview → DashboardService 聚合 5 指标 → ECharts 渲染；PNG 前端导出 / Excel 后端导出` |
| 个人中心（Phase 7） | `UserProfile.vue → /api/profile 系列 → ProfileService → UserService + MaskUtils + LoginLogService + issue_history 归并 → 顶栏 UserAvatar 同步刷新` |
| 字典 CRUD（Phase 7） | `DictManage.vue → /api/dicts 系列 → DictService(预设保护 + 引用计数) → DictCache(Redis+本地双层) → evict` |
| 备份导出（Phase 7） | `SiteSettings.vue → BackupDrawer → estimate → export → BackupService(游标分页 + 流式写出 + 临时文件) → 浏览器下载` |

---

## 六、架构演进（Phase 1→7）

| Phase | 架构主题 | 关键落地 |
|---|---|---|
| 1（基线） | 全量 MVP | 分层架构、JWT + RBAC、状态机、看板、Docker Compose |
| 2 | 关联与权限体系 | `issue_relation`、`permission`/`role_permission`、`requirePermission`、菜单按端动态渲染 |
| 3 | 项目与风格 | 项目负责人/成员、列设置、流程配置迁移、整体风格抽屉（localStorage） |
| 4 | 模块树 | `module` 邻接表、模块关联、流程管理菜单、归属/删除校验 |
| 5 | 流程与组织 | `flow_node`/`flow_transition` 数据驱动状态机、`FormDrawer` 规范、组织树、上级领导、数据初始化 |
| 6 | 类型与体验 | `issue_type`、FormDrawer 全量迁移、i18n、4 套主题、网站设置、侧栏 100vh |
| 7 | 字典与基础设施 | `dict_type`/`dict_item`、来源/优先级、个人中心、`file_record`/`login_log`/`scheduled_task`、备份导出 |

> 各 phase 详细任务分解（T0~T9 等）保留在归档：`docs/archive/2026-08-04/incremental-design-phase2~5.md`、`ARCH_phase6.md`、`ARCH_phase7.md`；增量设计合并权威文档：`docs/DESIGN-incremental-evolution-v1.0-2026-08-04.md`。

---

## 七、专项架构设计（独立文档，不合并）

| 文档 | 内容摘要 |
|---|---|
| [`docs/ARCH-frontend-redesign-v1.0-2026-08-01.md`](./ARCH-frontend-redesign-v1.0-2026-08-01.md) | 用户前台 UI/UX 重构：样式令牌层、`If` 前缀基础组件库、响应式布局、核心页面重构、5 阶段任务分解 |
| [`docs/ARCH-security-refactor-v1.0-2026-08-01.md`](./ARCH-security-refactor-v1.0-2026-08-01.md) | 安全加固与可维护性重构：M1–M5、`ModuleService` 拆分、魔法值枚举化、配置变更矩阵、23 号服务器环境变量清单 |
| [`docs/ARCH-dynamic-field-v1.0-2026-08-01.md`](./ARCH-dynamic-field-v1.0-2026-08-01.md) | 问题类型下沉字典 + 动态字段配置：`field_section`/`field_config`/`issue_field_value` 竖表、schema JSON 契约、联动 DFS 检测、DynamicFormRenderer |

---

## 八、共享知识 / 跨文件约定

### 8.1 响应包装与异常
- 所有 REST 统一返回 `Result<T>`（`{code, data, message}`），分页返回 `PageResult<T>`（`{list, total, page, size}`）。
- 业务错误统一抛 `BizException(msg)`，由全局异常处理器转 `Result.code≠0`；**禁止**在 Controller 里 `try/catch` 后返回 `null`。
- 备份导出是例外：失败返回 HTTP 200 + `application/json` 错误体（前端按 `Content-Type` 判定），成功返回 `application/octet-stream`。

### 8.2 权限与逻辑删除
- 权限校验统一在 **Service 首行** `permissionService.requirePermission(code)`；Controller 只做 DTO 绑定与 `@Valid`。
- 全部表继承 `BaseEntity`，`deleted` 软删；查询由 MyBatis-Plus 全局插件自动附加 `deleted=0`，**任何 Mapper XML/注解不得手写 `deleted=0`**。

### 8.3 唯一索引铁律（继承）
- 任何唯一约束必须「**生成列 + 条件唯一**」，`uk_col = GENERATED ALWAYS AS (IF(deleted=0, 业务键, NULL))`，禁止 `(col, deleted)` 复合。
- 本期唯一的生成列：`dict_item.code_active = CONCAT(type_id,':',code)`。Java 实体**不映射**该列（避免 MP 尝试 INSERT 报错）。

### 8.4 i18n 约定
- `vue-i18n` 用 `legacy:false`；语言文件按模块拆分（`dict` / `profile` / `infra` / `backup`），由 `index.js` 聚合；**zh-CN 与 en-US 的 key 集合必须一致**。
- 字典预设项名：`t('dict.value.' + typeCode + '.' + code)`，未命中回退数据库 `name`。
- **硬约束**：`views/`、`components/`、`layouts/` 内不得出现中文字面量（`grep -rnP "[\x{4e00}-\x{9fa5}]"` 结果应只剩注释行）。

### 8.5 主题与弹出层
- 主题只写 `document.body[data-if-theme]`，**严禁** `documentElement`；4 套主题语义变量集中在 `styles/themes.css`。
- 弹出层唯一形态为 `FormDrawer`：`sm=480 / md=620 / lg=800`；**≤768px 强制满宽**。仅 `ElMessageBox` / `ElMessage` / `ElNotification` 例外。

### 8.6 枚举 / 字典读取
- 固定枚举（severity、priority）走 `utils/i18nEnum.js`；**优先级 tag 色固定 `danger/warning/info`，不随主题变**。
- 字典走 `store/dict.js`（`optionsOf` / `allOptionsOf` / `nameOf` / `invalidate`），结构与 `store/issueType.js` 对齐；**禁止在组件内硬编码选项数组**。

### 8.7 防 N+1（硬指标）
- 列表回填一律批量：`DictCache.items(typeCode)` / `nameMap(ids)` 内存 Map 匹配，**0 次额外 DB**。
- 引用计数用**单条**聚合 `SELECT source_id, COUNT(*) ... GROUP BY source_id`；禁止每行一次 `COUNT`。
- 验收：开 MyBatis SQL 日志，加载问题/文件/字典各 20 行，各自 SQL 条数 ≤ 5。

### 8.8 文件与越权安全
- 文件下载/预览一律后端读流回传，`FileUtil.resolveSafe(base, relative)` 做 `normalize().startsWith(base)` 路径穿越断言；**禁止**把绝对路径暴露给前端拼 URL。
- 个人中心全部接口**不收 userId 入参**，统一 `SecurityUtils.getCurrentUserId()`；前端即便传了 userId 也忽略。

### 8.9 备份 / 调度安全
- 备份表清单为后端 `List<String>` 常量（CORE 11 / ALL 22），`BackupMapper` 虽用 `${}` 拼接，入参**永不来自前端**；Service 层 `TABLES.contains(table)` 双保险。
- 定时任务 cron 用 Spring `CronExpression`（6 位）；`jobKey` 必须命中 `jobRegistry` 白名单，**禁止任何类名反射**（安全红线）。
- Redis 监控**只读**，禁止实现 `DEL/FLUSH/CONFIG SET`；异常统一转 `available=false` + 归一化文案，**不抛 500**。

### 8.10 部署与缓存失效顺序
- **部署顺序硬约束**：先灌 `V20260810_issueflow_phase7.sql` → 再重启后端 → 再发前端。
- 缓存失效：字典写操作后 `DictCache.evict(typeCode)` + 前端 `store/dict.js.invalidate`；配置写后按前缀 evict（站点 / 文件 / 主题）保证「两处修改互相可见」。

### 8.11 基线跨文件约定（补充）
- JWT：HS256；payload=`{userId, roleCode, jti, exp}`；有效期 2h；请求头 `Authorization: Bearer <token>`；登出/强制失效写 Redis `jwt:blacklist:{jti}`。
- 前端 API：`api/request.js` 单例 Axios；请求拦截器注入 token；响应拦截器 `code!==200` 用 `ElMessage` 报错，`401` 清 token 跳 `/login`，`403` 跳 `/403`。
- 附件：存储根 `/data/attachments/{yyyyMM}/{uuid}.ext`；单文件 ≤20MB（Phase 7 起由 `file.*` 配置动态收紧）；非图片下载、图片 `preview` 内联。
- 编号生成：`IssueNoGenerator.nextIssueNo()` 取 `YYYYMMDD` 当日计数+1，`IS-YYYYMMDD-0001`；并发由 DB 唯一索引兜底。
- 时间/时区：统一 UTC 存储，前端按浏览器时区展示；看板时间范围入参 `yyyy-MM-dd`。

---

## 九、待明确事项

### 9.1 PRD 待确认问题 → 已决策映射（Phase 7，Q1~Q12 全部闭环）

| PRD | 问题 | 决策落点 | 采纳方案 |
| --- | --- | --- | --- |
| Q1 | 问题类型是否迁入业务管理 | 主理人 A | **B 保持一级平铺**；迁移脚本不碰 `/admin/issue-types` 的 `parent_id` |
| Q2 | 优先级是否字典化 | 主理人 B | **A 固定枚举** `priority TINYINT 0/1/2`，仿 severity，不走字典 |
| Q3 | Redis 数据来源 | §2.2 / T7 | **A 后端实时 `INFO`**；自动刷新默认关闭 |
| Q4 | 字典项名是否 i18n | §2.3-3 | **A 沿用 code 映射**；双语字段列 P2-7 |
| Q5 | 改密是否强制登出 | T5 | **A 强制登出**；复用 `jwt:blacklist:{jti}` 黑名单，前端 3s 跳登录 |
| Q6 | 备份边界 | 主理人 D | **①不导出二进制 ②设上限 200MB/20 万行 ③password 脱敏为 `***`** |
| Q7 | 核心配置含哪些表 | §3.9 | **A 11 张**（不含 organization/project/module） |
| Q8 | 是否引调度框架 | 主理人 C | **A Spring TaskScheduler 动态注册**，不引 Quartz |
| Q9 | 登录日志留存 | T5 | **B 成功+失败都记 + 近 90 天**（内置清理任务） |
| Q10 | 头像存储方式 | T5 | **A `user.avatar` 存相对路径**，复用文件存储 |
| Q11 | 配置管理 vs 网站设置 | T6 | **A 同源不同视图**，内置键打「内置」tag 禁删 |
| Q12 | 后台是否也做个人中心 | T4 | **A 仅前台**；后台「用户设置」只改文案不升功能 |

### 9.2 设计残点（Phase 7，含默认推荐）

1. **头像展示端点形态**：默认后端读流，新增专用只读端点 `GET /api/profile/avatar/{userId}`（登录即可）；不改 `WebMvcConfig`。
2. **生产 Redis 禁用 `INFO`/`DBSIZE`**：保持「只读降级」设计；部署前需运维确认生产 Redis 是否开放，若禁用则监控页稳定显示错误态并加运维提示。
3. **活动记录深翻页（>50 页）**：两路数据源各取 `page*size` 内存归并再切片；`page>50` 时提示切换到分类型查询。
4. **字典项双语字段（P2-7）**：本期 `dict_item.name` 不入库多语言，前端按 code 映射；双语字段（`name_en`）列 P2。
5. **email / phone 唯一索引**：本期不加（存量可能重复/空值）；唯一性由 Service 层保证；后续加必须先用生成列条件唯一 + 先跑重复数据体检。
6. **多实例部署一致性**：`DynamicTaskScheduler` 进程内调度多实例会重复触发（默认单机，多实例升 Quartz）；`DictCache` 本地 Map 失效不一致（多实例改 Redis Pub/Sub 广播）。

### 9.3 明确不在本期范围（Phase 7 P2 / 已剔除）

- 原需求「DB 还原到初始化」：**已剔除**，`SystemDataService.resetData` 保持现状。
- 统计维度按优先级切分、Redis 趋势图、服务端头像裁剪、组织/项目纳入核心配置备份：均属 P2，UI/接口预留扩展位但不实现。

### 9.4 基线待明确事项（Phase 1，延续关注）

1. JWT 刷新：MVP 单 token + Redis 黑名单（2h 过期重登）；`refreshToken` 无感刷新后续迭代。
2. 看板导出分工：PNG 前端 ECharts、Excel 后端 EasyExcel（数据口径以后端为准）。
3. 版本维度来源：复用 `issue.env_app_version` 字符串聚合，未建版本字典表。
4. 并发乐观锁：`issue` 表未加 `version` 字段（MVP 不启用，逻辑删除+历史可追溯）。
5. 附件安全：MVP 按 `content_type` + 大小校验，未做病毒扫描/扩展名白名单强制（Phase 7 起扩展名白名单可配置）。
6. 逻辑删除与历史外键：`issue` 软删后 `issue_history` 保留（不设 CASCADE），查询历史需确认是否包含已删问题。

---

## 附录：任务分解指引（Phase 7 T0~T9 概览）

> 完整任务分解（含每任务实现要点与验收标准）见归档 `docs/archive/2026-08-04/ARCH_phase7.md` 第五章（T0~T9 逐项）；本文档仅列总览。

| ID | 任务 | 优先级 | 依赖 | 规模 |
| --- | --- | --- | --- | --- |
| T0 | DB 迁移脚本 + 实体/Mapper/DTO 骨架 | P0 | — | 大（1 SQL + 45 Java） |
| T1 | 字典配置：后端 CRUD + 缓存 | P0 | T0 | 中 |
| T2 | 字典配置：前端页面 + store + i18n | P0 | T1 | 中 |
| T3 | 来源 + 优先级字段全链路贯通（含 R6 全页面补全） | P0 | T1, T2 | 大 |
| T4 | 菜单重构（R5 业务管理分组 / R2.1 用户设置 / 基础设施三层） | P0 | T0 | 中 |
| T5 | 个人中心（后端 + 前端） | P1 | T0, T4 | 大 |
| T6 | 基础设施 A：文件管理 + 配置管理 | P1 | T0, T4 | 大 |
| T7 | 基础设施 B：Redis 监控 + 定时任务 | P1 | T0, T4 | 大 |
| T8 | 备份数据（后端流式导出 + 前端抽屉） | P1 | T0, T1 | 中 |
| T9 | i18n / 主题 / 响应式 / 权限 收尾与冒烟自检 | P0 | T1-T8 | 全量 |

**依赖包说明**：Phase 7 后端与前端**均无新增依赖**（定时调度用 Spring 原生 TaskScheduler、UA 解析优先用已有 hutool-all 的 `UserAgentUtil`、JSON 流式写用 jackson 自带 `JsonGenerator`）；仅需确认 `@element-plus/icons-vue@^2.3.1` 中存在 `Timer`/`Odometer`/`Tools`/`Folder`/`Operation`/`Notebook` 图标。

---

*—— 本文档为 issueFlow 权威当前态架构设计；产品需求见 `docs/PRD.md`，变更记录见 `docs/CHANGELOG.md`，架构决策记录见 `docs/adr/`。*
