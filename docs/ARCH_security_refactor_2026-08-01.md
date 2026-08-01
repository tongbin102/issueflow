# 架构设计与任务分解：安全加固与可维护性重构（M1–M5 + ModuleService 拆分 + 枚举化）

| 项 | 内容 |
| --- | --- |
| 文档名称 | 安全加固与重构 · 架构设计与任务分解 |
| 版本 / 日期 | v1.0 / 2026-08-01 |
| 架构师 | 高见远 |
| 上游输入 | `docs/PRD_security_refactor_2026-08-01.md` |
| 技术栈 | Spring Boot + Spring Security + MyBatis-Plus + MySQL + Redis，`docker compose` 部署（23 号服务器） |
| 采纳决策 | M1 先注入后去兜底 / M2 prod=never·基线保留 always / M3+M4 prod=Slf4jImpl + mapper=info / M5 方案 A（`@EnableMethodSecurity` + `@PreAuthorize` 渐进）/ ModuleService 分阶段（先抽 Query + Support）/ 枚举化 P1 范围 |

---

## 1. 实现方案概述

分三条相互解耦的线并行推进：**配置安全线**（M1/M2/M3/M4，纯 yml + compose 改动，零代码风险，但 M1 强依赖 23 号 `.env` 先行注入）；**鉴权收口线**（M5：开 `@EnableMethodSecurity`，关键写接口加 `@PreAuthorize("hasRole('ADMIN')")`，Service 原判断保留为双保险）；**可维护性线**（ModuleService 分阶段先抽只读 `ModuleQueryService` 与无状态 `ModuleTreeSupport`，public API 不变；枚举化仅做 P1：新增 `EnableStatusEnum`、推广 `IssueStatusEnum`、以 `RoleEnum` 收口 `Constants.ROLE_*`）。任务按依赖排序，配置线最先、可回滚，重构线最后、行为不变。

---

## 2. 文件清单表格

| 相对路径 | 类型 | 职责 | 依赖文件 |
| --- | --- | --- | --- |
| `src/backend/src/main/resources/application.yml` | 修改 | 基线配置：移除 `jwt.secret` 硬编码值改环境变量引用；`sql.init.mode` 基线保留 `always`；`log-impl` 基线保留 `StdOutImpl` 便于本地 | — |
| `src/backend/src/main/resources/application-prod.yml` | 修改 | 生产覆盖：`jwt.secret: ${JWT_SECRET}`（去兜底）、`sql.init.mode: never`、`log-impl: Slf4jImpl`、`logging.level.com.issueflow.mapper: info` | application.yml |
| `docker-compose.yml` | 修改 | 去掉 `JWT_SECRET` 的 `:-...` 兜底，改 `${JWT_SECRET:?JWT_SECRET is required}` | 23 号 `.env` |
| `23 号服务器 .env`（非仓库文件） | 新增/确认 | 运维维护强随机 `JWT_SECRET`（HS256 ≥32 字节），不入 git | — |
| `src/backend/src/main/java/com/issueflow/config/SecurityConfig.java` | 修改 | 类上新增 `@EnableMethodSecurity(prePostEnabled=true)`；过滤链/白名单不变 | RoleEnum |
| `src/backend/src/main/java/com/issueflow/service/ModuleService.java` | 修改 | 瘦身为编排/事务入口，只读方法委托给 `ModuleQueryService`，工具计算委托 `ModuleTreeSupport`；public API 签名不变 | ModuleQueryService、ModuleTreeSupport |
| `src/backend/src/main/java/com/issueflow/service/ModuleQueryService.java` | 新增 | 只读查询 + 树装配：`tree`、`pathMap`、`buildNodes`、`toNodeVO`、`loadDependencies`、`buildPath`、`loadProjectModules`（无 `@Transactional` 写，或标注 `readOnly=true`） | ModuleMapper、ModuleDependencyMapper、ModuleTreeSupport |
| `src/backend/src/main/java/com/issueflow/util/ModuleTreeSupport.java` | 新增 | 无状态 static 工具：`buildChildrenMap`、`collectDescendantIds`、`depthOf`、`subtreeHeight`、`indexById`、`normalizeParentId`、`cleanIds`、`hasSelectedAncestor`、`nextSort`、`reorderSiblings` | Module 实体 |
| `src/backend/src/main/java/com/issueflow/enums/EnableStatusEnum.java` | 新增 | 启用状态枚举：`DISABLED(0)/ENABLED(1)` + `getByCode`，收敛 user/project/task 的 `status==0/1` | — |
| `src/backend/src/main/java/com/issueflow/enums/RoleEnum.java` | 调整 | 补充 `hasRole/isAdmin` 便捷方法（可选），作为角色码唯一权威来源 | — |
| `src/backend/src/main/java/com/issueflow/enums/IssueStatusEnum.java` | （推广采用，不改结构） | 替换代码中裸整型 `status==0..4` 比较为 `IssueStatusEnum.XXX.getCode()` | — |
| `src/backend/src/main/java/com/issueflow/common/Constants.java` | 调整 | 标注 `ROLE_*` 为 `@Deprecated`（或删除并全量改引用 RoleEnum），保留其它常量 | RoleEnum |
| `src/backend/src/main/java/com/issueflow/service/IssueFlowService.java` | 修改 | Controller 关键写接口加 `@PreAuthorize` 后，`ROLE_ADMIN.equals` 判断保留为双保险；裸整型状态改 IssueStatusEnum | RoleEnum、IssueStatusEnum |
| `src/backend/src/main/java/com/issueflow/service/IssueAttachmentService.java` | 修改 | upload/delete 手工判 ADMIN 保留双保险；ROLE_* 引用改 RoleEnum | RoleEnum |
| `src/backend/src/main/java/com/issueflow/service/IssueService.java` | 修改 | 4 处 ROLE_* 与裸状态收口到 RoleEnum / IssueStatusEnum | RoleEnum、IssueStatusEnum |
| `src/backend/src/main/java/com/issueflow/service/IssueRelationService.java`、`DashboardService.java`、`SystemDataService.java`、`PermissionService.java` | 修改 | 各 1 处 `roleCode` 分支收口到 RoleEnum；PermissionService 为细粒度权限权威点，仅替换字符串 | RoleEnum |

> 说明：ROLE_* 手工比较实测分布 —— IssueService 4 处、IssueAttachmentService 2 处、其余 5 个 Service 各 1 处（合计约 11 处直接引用，PRD 估算 24 处含 Controller/工具类间接引用）。

---

## 3. 任务列表（T1–T14，按依赖排序）

> 三条线：**配置安全线** T1–T5；**鉴权收口线** T6–T8；**可维护性线** T9–T14。T1 是全局前置（不可跳过）。

| 任务 | 任务名 | 影响文件 | 依赖前置 | 验收点 |
| --- | --- | --- | --- | --- |
| **T1** | 23 号环境变量注入与验证（前置阻断） | `23 号 .env`（新增 `JWT_SECRET`） | 无 | `.env` 写入 ≥32 字节强随机 `JWT_SECRET`；`docker compose config` 显示已解析进 backend；灰度重启 backend 启动成功、可登录并签发/校验 token |
| **T2** | 去除三处 JWT 兜底（M1 代码） | `application.yml`、`application-prod.yml`、`docker-compose.yml` | T1 | 三处兜底全部移除：prod=`${JWT_SECRET}`、compose=`${JWT_SECRET:?...}`、基线无明文值；缺失变量时容器按预期启动失败 |
| **T3** | 生产关闭 SQL 自动初始化（M2） | `application-prod.yml`、`application.yml` | 无 | prod `sql.init.mode: never`；基线保留 `always`；生产重启不再执行 schema/data |
| **T4** | 生产 MyBatis 日志改 Slf4j（M3） | `application-prod.yml`（`log-impl: Slf4jImpl`）、`application.yml`（基线 StdOut 保留） | 无 | 生产日志不出现 `==> Preparing:` / `==> Parameters:` 明文 |
| **T5** | 生产日志级别显式收敛（M4） | `application-prod.yml`（`logging.level.com.issueflow.mapper: info`，`root: warn`） | T4 | mapper 包 ≥ info；与 T4 联合验证 SQL 明文不落盘 |
| **T6** | 开启方法级安全（M5-a） | `SecurityConfig.java`（`@EnableMethodSecurity`） | T1 | 应用正常启动；`@PreAuthorize` 生效；未加注解接口行为不变 |
| **T7** | 关键写接口加 `@PreAuthorize`（M5-b） | 关键写 Controller（问题流转/附件/关系/系统数据管理端） | T6 | 低权限角色访问高危写接口返回 403；管理员正常；回归清单通过 |
| **T8** | Service 手工鉴权保留为双保险（M5-c） | `IssueFlowService`、`IssueAttachmentService`、`IssueRelationService`、`DashboardService`、`SystemDataService` | T7 | 去注解仍能拦截；双保险不产生重复报错；行为一致 |
| **T9** | 新增 `EnableStatusEnum`（F2-a） | `enums/EnableStatusEnum.java`（新增） | 无 | 枚举含 `DISABLED(0)/ENABLED(1)` + `getByCode`；单测覆盖 |
| **T10** | RoleEnum 收口 Constants.ROLE_*（F2-b） | `RoleEnum.java`、`Constants.java`、7 个引用 Service | T9 | `Constants.ROLE_*` 标注 `@Deprecated` 或删除；引用统一 RoleEnum；无裸角色字符串新增 |
| **T11** | 推广 IssueStatusEnum + EnableStatus（F2-c） | `IssueService`、`IssueFlowService`、AuthService/ProjectService/ScheduledTaskService | T9 | 裸整型 `status==0..4`、`status==0/1` 替换为枚举；语义与库中 code 一致 |
| **T12** | 抽 `ModuleTreeSupport`（F1-a） | `util/ModuleTreeSupport.java`（新增）、`ModuleService.java` | 无 | static 无状态工具迁入；ModuleService 改为调用工具；纯函数单测通过 |
| **T13** | 抽 `ModuleQueryService`（F1-b） | `service/ModuleQueryService.java`（新增）、`ModuleService.java` | T12 | 只读方法迁入并 `readOnly=true`；ModuleService 委托调用；`tree/pathMap` 对外行为不变 |
| **T14** | 联合回归 + 发布 Checklist 核对 | 全量（配置/鉴权/重构） | T2,T5,T8,T11,T13 | PRD 第七节 Checklist 全绿；ModuleService public API 与外部行为不变；现有测试通过 |

---

## 4. 配置变更矩阵

| 配置项 | `application.yml`（基线/开发） | `application-prod.yml`（生产覆盖） | `docker-compose.yml`（backend env） | 23 号 `.env` |
| --- | --- | --- | --- | --- |
| **JWT 密钥（M1）** | 删除明文值，改 `jwt.secret: ${JWT_SECRET:}`（本地可空/dev 走 application-dev） | `jwt.secret: ${JWT_SECRET}`（**去兜底**） | `JWT_SECRET: ${JWT_SECRET:?JWT_SECRET is required}`（**去 `:-`**） | **新增** `JWT_SECRET=<≥32字节强随机>` |
| **SQL 初始化（M2）** | `spring.sql.init.mode: always`（保留，便于本地起步） | `spring.sql.init.mode: never` | 无 | 无 |
| **MyBatis 日志（M3）** | `log-impl: org.apache.ibatis.logging.stdout.StdOutImpl`（保留） | `log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl` | 无 | 无 |
| **日志级别（M4）** | `logging.level.com.issueflow: debug`、`root: info` | `com.issueflow: info`、`com.issueflow.mapper: info`（**新增显式**）、`root: warn` | 无 | 无 |
| JWT 有效期 | `jwt.expiration: 7200` | `${JWT_EXPIRATION:7200}` | `JWT_EXPIRATION: 7200` | 可选 |
| MySQL/Redis/附件 | 本地默认 | 全量 `${...}` 注入（现状保持） | 现状保持 | 现状保持 |

> 上线顺序硬约束：**T1（.env 注入并验证）→ T2（去兜底代码）**。严禁先合并去兜底代码再注入变量，否则容器直接启动失败。

---

## 5. M5 鉴权方案（方案 A：注解渐进 + Service 双保险）

**开启位置**：`SecurityConfig` 类上新增注解，与 `@EnableWebSecurity` 并列：

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)   // 新增：开启 @PreAuthorize/@PostAuthorize
@RequiredArgsConstructor
public class SecurityConfig { ... }
```

> 前提：JWT filter 需已把角色写入 `Authentication` 的 authorities，且带 `ROLE_` 前缀（如 `ROLE_ADMIN`），`hasRole('ADMIN')` 才生效。若现有 authorities 无前缀，T6 需同步在 filter 或 `GrantedAuthority` 映射处补齐前缀（回归时重点验证）。

**Controller 方法加注解示例**（关键写接口）：

```java
// 问题流转 - 管理员强制操作
@PreAuthorize("hasRole('ADMIN')")
@PostMapping("/api/issues/{id}/force-close")
public Result<Void> forceClose(@PathVariable Long id) { ... }

// 附件删除 - 管理员或归属人（细粒度仍交 Service/PermissionService）
@PreAuthorize("hasRole('ADMIN') or @permissionService.canDeleteAttachment(#id)")
@DeleteMapping("/api/attachments/{id}")
public Result<Void> delete(@PathVariable Long id) { ... }
```

**与现有 Service 判断的共存策略（双保险）**：
- **保留** Service 内 `RoleEnum.ADMIN.getCode().equals(roleCode)` 等判断，**不本次删除**，作为第二道防线与细粒度归属校验落点。
- 注解负责**粗粒度角色门禁**（是否 ADMIN / 是否某角色）；Service 负责**细粒度业务规则**（归属、状态机可达性、字段级权限）。
- 两层判定应语义一致：注解拦截返回 403（`AccessDeniedHandler` 统一 Result）；Service 拦截抛 `BizException(FORBIDDEN)`。回归清单需覆盖"注解通过但 Service 拒绝""注解直接拒绝"两类路径。
- 收口范围本次仅**关键写接口**（问题强制流转、附件写、关系写、系统数据管理端写），只读与低危接口维持 `authenticated()`，后续迭代再推广。

---

## 6. ModuleService 拆分设计（分阶段：先抽 Query + Support）

**本次范围**：仅抽出 `ModuleQueryService`（只读查询+树装配）与 `ModuleTreeSupport`（static 工具）。写命令 / 依赖关系 / 校验（`ModuleDependencyService`、`ModuleValidator`）**留待后续阶段**，本次不动，降低回归面。

**原方法 → 新类映射**：

| 现有方法 | 去向 | 事务边界 |
| --- | --- | --- |
| `tree(Long)` | `ModuleQueryService` | 只读，`@Transactional(readOnly=true)` 或无事务 |
| `pathMap(Collection)` | `ModuleQueryService` | 只读 |
| `loadProjectModules` / `loadDependencies` / `buildNodes` / `toNodeVO` / `buildPath` | `ModuleQueryService`（private） | 只读 |
| `buildChildrenMap` / `collectDescendantIds` / `depthOf` / `subtreeHeight` / `indexById` / `normalizeParentId` / `cleanIds` / `hasSelectedAncestor` / `nextSort` / `reorderSiblings` | `ModuleTreeSupport`（public static，纯函数） | 无（无状态） |
| `create` / `update` / `delete` / `move` / `batchDelete` / `batchMove` / `setDependencies` / `assertModuleBelongsToProject` | **保留在 `ModuleService`** | 写方法 `@Transactional` 仍在 ModuleService |

**调用关系与事务安全**：
```
Controller → ModuleService（public API 不变，编排+写事务入口）
                 ├── 只读委托 → ModuleQueryService（readOnly）
                 └── 纯计算委托 → ModuleTreeSupport（static）
             ModuleQueryService → ModuleTreeSupport（static）
```
- **事务边界**：写事务注解全部保留在 `ModuleService` 的写方法上；`ModuleService` 通过**注入** `ModuleQueryService`（Spring bean）调用只读逻辑，而非自调用，避免同类自调用导致 `@Transactional` 失效。
- **API 稳定**：`ModuleService` 的 10 个 public 方法（`tree/pathMap/assertModuleBelongsToProject/create/update/delete/move/batchDelete/batchMove/setDependencies`）签名与语义不变，Controller 零改动。
- `ModuleTreeSupport` 为无状态 static 工具，直接单元测试，不依赖 Spring 容器。

---

## 7. 风险与回滚

| # | 风险 | 等级 | 回滚方案 |
| --- | --- | --- | --- |
| R1 | **M1 去兜底后变量未注入 → 容器启动失败** | 高 | 严格 T1→T2 顺序：先 `.env` 注入 + `docker compose config` 校验 + 灰度验证登录，确认无误再合并去兜底代码。异常时 `git revert` T2 三处改动 + `docker compose up -d backend` 恢复带兜底旧版；`.env` 中 `JWT_SECRET` 可保留不影响旧版 |
| R2 | **M2 改 never 后生产缺表不再自愈** | 高 | 上线前确认生产库 schema/数据已就绪（DBA 核对结构版本）；回滚将 prod `sql.init.mode` 改回 `always` 并重启（仅限确无覆盖数据风险时） |
| R3 | **M5 角色 authority 无 `ROLE_` 前缀 → `hasRole` 全部 403 误伤** | 中高 | T6 先验证 filter 写入的 authorities 前缀；灰度用管理员+低权限双账号跑回归清单；异常时 `git revert` `@EnableMethodSecurity` 与注解，退回纯 `authenticated()`（Service 双保险仍在，安全不降级） |
| R4 | **枚举化替换裸整型/字符串引入语义偏差**（如 status code 与库不一致） | 中 | 仅做 P1 范围、逐 Service 小步替换 + 单测；枚举 code 与库中 0–4 逐一核对；异常时按文件粒度 revert，`Constants.ROLE_*` 先 `@Deprecated` 保留不删除，可平滑回退 |
| R5 | **ModuleService 拆分改动事务边界 / 自调用失效 → 树/写行为回归** | 中 | 写事务注解不迁出、以 bean 注入替代自调用；只抽只读+工具、写命令不动；T14 联合回归对比 public API 输出；异常时按新增文件粒度 revert（新增类删除、方法搬回） |

---

## 附：23 号服务器环境变量确认清单（T1 前置，务必先做）

- [ ] `.env` 新增 `JWT_SECRET`：HS256 **≥32 字节**强随机（不入 git、不入库）
- [ ] `docker compose config` 校验 `JWT_SECRET` 已解析进 `backend` 服务 environment
- [ ] 灰度重启 backend：启动成功 + 登录可签发/校验 token
- [ ] 拍板密钥是否轮换：沿用旧值（token 不失效）/ 换新值（全员强制重新登录，建议低峰）
- [ ] 确认既有变量 `MYSQL_*`、`REDIS_*`、`ATTACHMENT_BASE_PATH` 不受影响
- [ ] 备份移除兜底前的配置快照（application*.yml、docker-compose.yml）以备回滚
