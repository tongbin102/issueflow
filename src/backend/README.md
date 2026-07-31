# issueFlow 后端（issueflow-backend）

> 包路径根：`com.issueflow`
> 启动类：`IssueFlowApplication`（含默认管理员初始化 `ApplicationRunner`）

## 一、技术栈

| 维度 | 选型 |
|---|---|
| 框架 | Spring Boot 3.2（Java 17） |
| 持久层 | MyBatis-Plus 3.5.x（`mybatis-plus-spring-boot3-starter`） |
| 数据库 | MySQL 8（utf8mb4） |
| 缓存 / 黑名单 | Redis 7 |
| 认证 | Spring Security 6 + JWT（jjwt 0.12.x，HS256） |
| 接口文档 | Knife4j / OpenAPI3（`/doc.html`） |
| 导出 | EasyExcel（看板 Excel） |
| 工具 | Lombok |

## 二、包结构总览

| 包 | 一句话职责 |
|---|---|
| `controller` | REST 端点、参数校验、鉴权注解（按模块：鉴权/问题/流转/看板/用户/标签/系统配置/附件） |
| `service` | 业务逻辑、事务、`@Transactional`、状态机调用、编号生成、数据范围过滤 |
| `mapper` | MyBatis-Plus Mapper 接口；`IssueMapper`/`IssueHistoryMapper` 含自定义 `@Select` 聚合 SQL |
| `entity` | 7 张表实体（`@TableName`），多数继承 `BaseEntity` |
| `enums` | 4 个枚举：`RoleEnum` / `IssueStatusEnum` / `SeverityEnum` / `HistoryActionEnum` |
| `dto` | 请求 `req` 与响应 `resp` 对象（登录/问题/分页/状态变更/历史/用户/看板/配置） |
| `security` | `JwtUtil` / `JwtAuthenticationFilter` / `RestAuthenticationEntryPoint` |
| `config` | `MybatisPlusConfig` / `RedisConfig` / `Knife4jConfig` / `WebMvcConfig` / `SecurityConfig` |
| `common` | `Result` / `PageResult` / `ResultCode` / `BizException` / `BaseEntity` / `Constants` |
| `util` | `IssueNoGenerator` / `FileUtil` / `ExcelExportUtil` / `SecurityUtils` / `DateTimeUtils` |
| `handler` | `GlobalExceptionHandler`（统一异常→Result）、`StateMachine`（状态流转规则） |

> 注：`role` 表与 `sys_config` 表无逻辑删除字段，其对应实体（`Role`/`SysConfig`）**不继承** `BaseEntity`。

## 三、本地构建与运行

```bash
cd src/backend

# 1) 构建（跳过测试）
mvn clean package -DskipTests

# 2) 运行（finalName = issueflow-backend）
java -jar target/issueflow-backend.jar
```

- 启动后自动建表（`db/schema.sql`）+ 初始化 4 个角色（`db/data.sql`），并由 `ApplicationRunner` 写入默认管理员。
- 接口地址：`http://localhost:8080/api`；文档：`http://localhost:8080/doc.html`。
- 单元测试：`src/test/java/com/issueflow/`（AuthControllerTest、IssueFlowTest）。

## 四、配置要点（application.yml）

| 配置项 | 说明 | 默认值 / 来源 |
|---|---|---|
| `spring.datasource.*` | MySQL 连接（位于 `application-dev.yml` / `application-prod.yml`） | `host=${MYSQL_HOST:127.0.0.1}`、`db=${MYSQL_DB:issueflow}`、`user=${MYSQL_USER:root}`、`password=${MYSQL_PASSWORD:root}` |
| `spring.data.redis.*` | Redis 连接 | `host=${REDIS_HOST:127.0.0.1}`、`port=${REDIS_PORT:6379}` |
| `mybatis-plus.global-config.db-config` | 逻辑删除字段 `deleted`，自增 `id` | `logic-delete-value:1` / `logic-not-delete-value:0` |
| `jwt.secret` | JWT 签名密钥（HS256） | `issueflow-secret-key-...`（生产改 `JWT_SECRET`） |
| `jwt.expiration` | token 有效期（秒） | `7200`（2h，可由 `JWT_EXPIRATION` 覆盖） |
| `app.attachment-base-path` | 附件落盘根目录 | `${ATTACHMENT_BASE_PATH:/data/attachments}` |
| `spring.servlet.multipart` | 上传上限 | `max-file-size:20MB`、`max-request-size:60MB` |

## 五、默认账号

| 账号 | 密码 | 角色 |
|---|---|---|
| `admin` | `admin123` | ADMIN（首次启动由 `ApplicationRunner` 自动写入） |

> ⚠️ 首次登录后请立即修改密码；生产部署务必修改 `JWT_SECRET` 与数据库密码。

## 六、如何新增一个业务模块

1. **Entity**：在 `entity/` 新建实体类，`@TableName("xxx")`；含逻辑删除的继承 `BaseEntity`，否则自行声明字段（参考 `Role`/`SysConfig`）。
2. **Mapper**：在 `mapper/` 新建 `XxxMapper extends BaseMapper<Xxx>`（已被 `@MapperScan("com.issueflow.mapper")` 扫描）；复杂 SQL 用 `@Select` 注解或 `resources/mapper/*.xml`。
3. **Service**：在 `service/` 新建 `@Service` + `@RequiredArgsConstructor` 类，注入 Mapper / 其他 Service；写操作加 `@Transactional`。
4. **Controller**：在 `controller/` 新建 `@RestController` + `@RequestMapping("/api/xxx")`，注入 Service；公开接口放行白名单见 `SecurityConfig.WHITE_LIST`。
5. **登记**：若为新表，在 `db/schema.sql` 增加建表语句；如需 RBAC 控制，在 Controller 内用 `Constants.ROLE_ADMIN` 校验或前端 `v-permission` + 路由 `meta.roles` 配合。

## 七、依赖关系（顶层）

```
controller → service → mapper / entity / util / security
service    → handler.StateMachine（状态机）、SysConfigService（开关）
security   → JwtUtil ↔ Redis（黑名单）
common     → 被所有层依赖（Result / BizException / Constants）
config     → 装配 Security / MyBatis-Plus / Redis / Knife4j / WebMvc
```
