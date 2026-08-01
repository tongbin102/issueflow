# PRD：安全加固与可维护性重构（M1–M5 + 大文件拆分 + 魔法值枚举化）

## 一、项目信息

| 项 | 内容 |
| --- | --- |
| 文档名称 | 安全加固与可维护性重构 PRD |
| 版本 / 日期 | v1.0 / 2026-08-01 |
| 作者 | 产品经理 · 许清楚 |
| 语言 | 简体中文 |
| 后端技术栈 | Spring Boot + Spring Security + MyBatis-Plus + MySQL + Redis |
| 部署方式 | 23 号应用服务器，`docker compose`（backend / frontend / mysql / redis） |
| 涉及模块 | 配置层（`application*.yml`、`docker-compose.yml`）、安全层（`SecurityConfig` + Service 鉴权）、`ModuleService`、`enums` 包、`Constants` |

**原始需求复述**：对 issueFlow 后端进行一轮安全基线加固（JWT 密钥去硬编码、生产 SQL 初始化/日志收敛、接口级权限收口）并同步做可维护性重构（拆分 907 行的 `ModuleService`、把散落的魔法值收敛为枚举），保证生产环境安全、可运维、可回滚。

---

## 二、产品目标

1. **安全基线**：生产环境不留任何密钥硬编码/兜底默认值，不自动初始化数据库、不明文打印 SQL 与参数，接口权限校验有统一、可审计的落点。
2. **可维护性**：把超长的 `ModuleService` 按职责拆分、把散落在代码里的魔法值收敛到枚举，降低后续改动的心智负担与出错概率。

---

## 三、需求池

> 优先级：**P0 = 必须本次上线**（安全阻断项）｜**P1 = 应尽快做**｜**P2 = 可择机做**

| 编号 | 需求 | 范围 | 风险 | 验收标准 | 依赖 | 优先级 |
| --- | --- | --- | --- | --- | --- | --- |
| **M1** | JWT 密钥去硬编码 + 去兜底默认值 | `application.yml`、`application-prod.yml`、`docker-compose.yml` | 移除兜底后若环境变量未注入 → **容器启动失败** | 生产仅从 `${JWT_SECRET}` 读取；三处默认值全部移除；23 号服务器容器能正常启动并签发/校验 token | 23 号服务器需先注入 `JWT_SECRET`（≥32 字节） | **P0** |
| **M2** | 生产关闭 SQL 自动初始化 | `application.yml` / `application-prod.yml` 的 `spring.sql.init` | 生产误跑 `schema.sql/data.sql` 覆盖存量数据 | 生产 profile 下 `sql.init.mode=never`；重启不再执行建表/灌数 | 生产库结构已就绪 | **P0** |
| **M3** | 生产关闭 MyBatis SQL 明文日志 | `application.yml` / `application-prod.yml` MyBatis `log-impl` | SQL + 参数明文入日志 → 敏感数据泄露 | 生产不再输出全量 SQL/参数；开发环境可保留 | 与 M4 联动 | **P0** |
| **M4** | 生产日志级别显式收敛 | `application-prod.yml` `logging.level` | mapper 包 DEBUG 仍会打印 SQL；级别未显式覆盖 | 显式设置 `com.issueflow.mapper`/MyBatis 相关包不低于 `info`；`root=warn` 保持 | M3 | **P1** |
| **M5** | 接口级权限校验收口 | `SecurityConfig` + 分散在 Service 的 `Constants.ROLE_ADMIN.equals(...)` 手工鉴权 | 手工鉴权散落易漏判/不一致，无统一审计 | 明确统一方案（方法级注解或集中鉴权）；关键写接口有一致的角色校验；无越权 | RoleEnum、M1 | **P1** |
| **F1** | 拆分 `ModuleService`（907 行） | `service` 包内 | 拆分引入回归、事务边界变化 | 拆分后单文件显著变短；public API 与对外行为不变；现有测试通过 | 无 | **P1** |
| **F2** | 魔法值枚举化 | `enums` 包、`Constants`、引用处 | 大范围替换引入行为差异 | 目标枚举补齐并被采用；无裸字符串/裸整型状态比较遗留（按清单范围） | RoleEnum 等既有枚举 | **P2** |

---

## 四、M1–M5 逐项说明

### M1 · JWT 密钥去硬编码 + 去兜底默认值（P0）

- **现状问题**：
  - `application.yml` 第 58–61 行：`jwt.secret` 明文硬编码 `issueflow-secret-key-2024-...-32bytes!!`。
  - `application-prod.yml` 第 18–19 行：`${JWT_SECRET:issueflow-secret-key-2024-...}` —— 仍带兜底默认值。
  - `docker-compose.yml` 第 75 行：`JWT_SECRET: ${JWT_SECRET:-issueflow-secret-key-2024-...}` —— **第三处兜底**（易被忽略）。
  - 三处兜底任一存在，泄露的密钥都可能被生产实际采用，等同密钥公开。
- **期望行为**：
  - 生产 profile 只从环境变量读取，**不带默认值**：`jwt.secret: ${JWT_SECRET}`。
  - `docker-compose.yml` 去掉 `:-...` 兜底：`JWT_SECRET: ${JWT_SECRET:?JWT_SECRET is required}`（未注入即启动报错，属预期行为）。
  - 密钥要求 HS256 ≥ 32 字节，由运维在 23 号服务器 `.env` 维护，不入库、不进 git。
- **影响文件/配置**：`application.yml`、`application-prod.yml`、`docker-compose.yml`、23 号服务器 `.env`（新增/确认 `JWT_SECRET`）。
- **部署/运维注意事项（重点）**：
  - **注入顺序（务必先注入、再移除兜底）**：
    1. 在 23 号服务器 `.env` 写入强随机 `JWT_SECRET`；
    2. `docker compose config` 校验变量已解析进 backend 服务；
    3. 灰度重启 backend，确认启动成功且能登录（token 正常签发/校验）；
    4. **确认无误后**才提交移除三处兜底的代码并重新部署。
  - **密钥是否轮换需拍板**：沿用旧值 → 存量 token 不失效；改用新值 → 所有已登录用户 token 立即失效需重新登录（建议低峰执行）。
  - **回滚方案**：保留移除兜底前的配置快照；若上线后异常，`git revert` 三处改动并 `docker compose up -d backend` 即可恢复带兜底的旧版；`.env` 中的 `JWT_SECRET` 可保留不影响旧版。

### M2 · 生产关闭 SQL 自动初始化（P0）

- **现状问题**：`application.yml` 第 14–20 行 `spring.sql.init.mode: always` + `schema-locations/data-locations`，生产每次启动都会执行建表/灌数（`continue-on-error: true` 掩盖失败），存在覆盖或污染存量数据风险。
- **期望行为**：生产 profile 下 `spring.sql.init.mode: never`；建表/初始化由 DBA/迁移脚本一次性完成。开发/本地可保留 `always` 便于起步。
- **影响文件/配置**：`application.yml`（基线）、`application-prod.yml`（生产覆盖为 `never`）。
- **部署/运维注意事项**：上线前确认生产库 schema 与数据已就绪；改为 `never` 后重启不再自愈缺表，需运维保证结构版本一致。

### M3 · 生产关闭 MyBatis SQL 明文日志（P0）

- **现状问题**：`application.yml` 第 44–56 行 `mybatis-plus.configuration.log-impl: org.apache.ibatis.logging.stdout.StdOutImpl`，生产会打印全量 SQL 及参数（含账号、手机号等敏感字段），属数据泄露面。
- **期望行为**：生产禁用 stdout SQL 日志。建议做法：基线保留便于开发，在 `application-prod.yml` 用 `mybatis-plus.configuration.log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl` 交由日志框架按级别控制（配合 M4 使 SQL 不落盘），或直接不输出。
- **影响文件/配置**：`application.yml`、`application-prod.yml`。
- **部署/运维注意事项**：与 M4 联动验证——生产日志中不应出现 `==> Preparing:` / `==> Parameters:` 明文。

### M4 · 生产日志级别显式收敛（P1）

- **现状问题**：`application-prod.yml` 第 22–25 行仅设 `com.issueflow: info`、`root: warn`，**未显式覆盖 mapper/MyBatis 相关包**；一旦 M3 改为交给 slf4j，若 mapper 包为 DEBUG 仍会打印 SQL。
- **期望行为**：显式声明 `logging.level.com.issueflow.mapper: info`（或更高），确保切换日志实现后 SQL 明文不因包级别而泄漏；`root: warn` 保持。
- **影响文件/配置**：`application-prod.yml`。
- **部署/运维注意事项**：M3+M4 必须同批上线并联合验证，避免"改了实现但级别放行"造成仍打印 SQL。

### M5 · 接口级权限校验收口（P1）

- **现状问题（已 grep 确认）**：
  - `SecurityConfig` 采用「白名单 `permitAll` + JWT filter + `anyRequest().authenticated()`」，即**只保证"已登录"，不保证"够权限"**。
  - **未发现任何 `@PreAuthorize`，也未开启 `@EnableMethodSecurity`**；接口级角色校验以手工方式散落在 Service 层，例如：
    - `IssueFlowService`（`Constants.ROLE_ADMIN.equals(roleCode)`）
    - `IssueAttachmentService`（`upload/delete` 手工判 ADMIN + 归属）
    - `IssueRelationService`、`DashboardService`（按 `roleCode` 分支）
    - 细粒度权限走 `PermissionService.hasPermission` + `SecurityUtils`。
  - 全仓 `Constants.ROLE_*` 手工比较约 **24 处**，判定逻辑不统一、易漏判、无集中审计。
- **期望行为**：确定一套统一鉴权方案并逐步收口。二选一（需拍板，见待确认）：
  - **方案 A（推荐，渐进）**：开启 `@EnableMethodSecurity`，在关键写接口用 `@PreAuthorize("hasRole('ADMIN')")` 等注解，把最高危的几个接口先收口，Service 内手工判断逐步下线。
  - **方案 B**：维持 Service 手工鉴权，但抽取统一鉴权工具/切面，消除重复的 `equals` 判断。
- **影响文件/配置**：`SecurityConfig`、上述若干 Service、（方案 A 下）Controller 注解。
- **部署/运维注意事项**：M5 涉及行为变更，**必须有接口权限回归清单**；灰度验证"低权限角色访问高权限接口返回 403"；与 M1 同批时先验证登录链路再验证鉴权。

---

## 五、大文件拆分计划：`ModuleService`（907 行）

### 拆分目标
把单文件 907 行、约 40 个方法的 `ModuleService` 按职责拆为若干高内聚单元，`ModuleService` 瘦身为对外编排/事务入口，**public API 与外部行为保持不变**（Controller 尽量不改）。

### 拆分原则
- **单一职责**：只读查询 / 写命令 / 依赖关系 / 校验 / 无状态工具分离。
- **API 稳定**：保留现有 public 方法签名，Controller 无感知或最小改动。
- **事务边界保留**：写操作的事务注解留在 command 层，避免自调用导致事务失效。
- **无状态优先**：纯计算工具抽为 static 工具类，便于单测。

### 目标文件命名与职责

| 目标文件 | 职责 | 迁入的主要方法（现有） |
| --- | --- | --- |
| `ModuleService.java`（瘦身保留） | 对外编排入口、事务边界、委托调用 | 保留 public API：`tree/create/update/delete/move/batchDelete/batchMove/setDependencies/pathMap/assertModuleBelongsToProject` |
| `ModuleQueryService.java` | 只读查询与树装配 | `tree`、`pathMap`、`buildNodes`、`toNodeVO`、`loadDependencies`、`buildPath`、`loadProjectModules` |
| `ModuleDependencyService.java` | 依赖关系维护与环检测 | `setDependencies`、`hasDependencyCycle`、依赖图构建 |
| `ModuleValidator.java` | 参数/业务校验 | `assertMovable`、`assertNameAvailable`、`assertAllInProject`、`assertModuleBelongsToProject` |
| `ModuleTreeSupport.java`（static 工具） | 无状态树/集合计算 | `buildChildrenMap`、`collectDescendantIds`、`depthOf`、`subtreeHeight`、`indexById`、`normalizeParentId`、`cleanIds`、`hasSelectedAncestor`、`nextSort`、`reorderSiblings` |

> 拆分粒度（一步到位 vs 分阶段）需拍板，见待确认。

---

## 六、魔法值枚举化清单

> **重要现状**：`enums` 包已存在 `IssueStatusEnum / PriorityEnum / SeverityEnum / RoleEnum / HistoryActionEnum / DictTypeCodeEnum`，**枚举本身多数已具备**。本次核心工作是**"推广采用 + 补齐缺失"**，而非重复造枚举。

| 枚举 | 现状 | 建议动作 | 覆盖的魔法值来源 | 优先级 |
| --- | --- | --- | --- | --- |
| `IssueStatusEnum` | 已有（OPEN=0…CLOSED=4） | 推广采用，替换代码中裸整型状态比较 | `status == 0..4`、StateMachine from/to | P1 |
| `PriorityEnum` | 已有（HIGH=0/MEDIUM=1/LOW=2） | 推广采用 | 优先级裸整型 | P2 |
| `SeverityEnum` | 已有（FATAL=0…MINOR=3） | 推广采用 | 严重度裸整型 | P2 |
| `RoleEnum` | 已有（SUBMITTER/DEVELOPER/TESTER/ADMIN） | 收敛：与 `Constants.ROLE_*`（约 24 处）二选一，统一到枚举 | `Constants.ROLE_ADMIN.equals(...)` 等 | **P1** |
| `HistoryActionEnum` | 已有（CREATE/CLAIM/…）≈ FlowAction | 推广采用；确认是否需独立 `FlowActionEnum` | StateMachine `actionCode` 字符串 | P1 |
| `EnableStatusEnum`（新增建议：0 禁用/1 启用） | **缺失** | 新增；收敛 user/project/task 的 `status==0/1` | AuthService/ProjectService/ScheduledTaskService 裸 0/1 | P1 |
| `MenuTypeEnum`（1 前台/2 后台） | **缺失**（现为 `Constants.MENU_TYPE_*` int） | 新增或保持常量二选一 | `MENU_TYPE_FRONT/ADMIN` | P2 |
| `BizTypeEnum`（ISSUE/AVATAR/MANUAL） | **缺失**（现为 `Constants.BIZ_TYPE_*`） | 新增建议 | `BIZ_TYPE_*` | P2 |
| `StorageTypeEnum`（LOCAL…） | **缺失**（现为 `Constants.STORAGE_TYPE_LOCAL`） | 新增建议 | `STORAGE_TYPE_LOCAL` | P2 |
| `TriggerTypeEnum`（CRON/MANUAL） | **缺失**（现为 `Constants.TRIGGER_TYPE_*`） | 新增建议 | `TRIGGER_TYPE_*` | P2 |
| `ModuleType` | **待确认**（需先确认 Module 是否有 type 字段） | 有则新增，无则移除该项 | 用户示例项 | 待确认 |

> 是否本次全量枚举化 vs 只做 P1（RoleEnum 收口 + IssueStatus 采用 + EnableStatus 新增）需拍板，见待确认。

---

## 七、发布 Checklist（必须同步上线的变更）

**配置（代码仓）**
- [ ] `application.yml`：移除 `jwt.secret` 硬编码值（第 58–61 行）。
- [ ] `application.yml`：`spring.sql.init` 基线策略确认；MyBatis `log-impl` 基线确认（第 14–20、44–56 行）。
- [ ] `application-prod.yml`：`jwt.secret: ${JWT_SECRET}`（去兜底，第 18–19 行）。
- [ ] `application-prod.yml`：`spring.sql.init.mode: never`。
- [ ] `application-prod.yml`：MyBatis 日志改 slf4j / 关闭 stdout SQL。
- [ ] `application-prod.yml`：`logging.level` 显式覆盖 mapper 包（第 22–25 行）。

**环境变量（23 号服务器 `.env`）**
- [ ] 新增/确认 `JWT_SECRET`（≥32 字节强随机），不入 git。
- [ ] 确认 `MYSQL_*`、`REDIS_*`、`ATTACHMENT_BASE_PATH` 等既有变量不受影响。

**docker-compose**
- [ ] `docker-compose.yml` 第 75 行去掉 `JWT_SECRET` 的 `:-...` 兜底（改 `:?` 或纯引用）。
- [ ] `docker compose config` 校验变量解析正确。

**SQL / 数据库**
- [ ] 确认生产库 schema/数据已就绪（因 M2 关闭自动初始化）。
- [ ] 若枚举化触及状态语义，确认与库中现有 code 一致（0–4 等）。

**上线验证**
- [ ] backend 容器启动成功、登录可签发/校验 token。
- [ ] 生产日志无明文 SQL/参数。
- [ ] （M5）低权限角色访问高权限接口返回 403。
- [ ] 回滚脚本/配置快照就绪。

---

## 八、待确认问题（需用户拍板）

1. **M1 环境变量注入确认（强建议先做）**：是否同意"先在 23 号服务器注入 `JWT_SECRET` 并验证容器启动，再提交移除三处兜底"的顺序？（避免直接移除导致启动失败）
2. **JWT 密钥是否借机轮换**：沿用旧值（token 不失效）还是换新值（全员强制重新登录，建议低峰）？
3. **M5 鉴权方案二选一**：方案 A（开启 `@EnableMethodSecurity` + `@PreAuthorize` 渐进收口，推荐）还是方案 B（抽统一鉴权工具/切面、维持 Service 手工判断）？本次收口范围是全部接口还是仅关键写接口？
4. **`ModuleService` 拆分节奏**：一步到位（按第五节 5 个目标文件全拆）还是分阶段（先抽只读 `ModuleQueryService` + 工具类，写/依赖后续再拆）？
5. **魔法值枚举化范围**：本次全量（含 P2 的 BizType/StorageType/TriggerType/MenuType 等）还是只做 P1（RoleEnum 收口 + IssueStatus 采用 + 新增 EnableStatusEnum）？
6. **`ModuleType` 是否存在**：需确认 Module 实体是否有 type 字段——无则从枚举清单移除该项。
7. **M3 生产日志实现**：MyBatis 改为 `Slf4jImpl`（可控级别、推荐）还是 `NoLoggingImpl`（彻底不输出）？
