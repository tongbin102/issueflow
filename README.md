# issueFlow · 软件项目缺陷记录与验证管理平台

> 一句话概述：issueFlow 是一个前后端分离、可一键部署的缺陷全生命周期管理平台，
> 覆盖「问题记录 → 验证流转 → 角色协作 → 数据洞察」的完整闭环，让团队用统一语言追踪每一个缺陷。

- 后端：Spring Boot 3.2 + MyBatis-Plus + MySQL 8 + Redis 7 + JWT + Knife4j
- 前端：Vue3 + Element Plus + Vue Router + Pinia + Axios + ECharts
- 部署：Docker Compose（mysql / redis / backend / frontend 四服务一键编排）

---

## 1. 项目简介

issueFlow 面向研发团队，提供从缺陷**提交、分派、修复、验证到关闭**的全流程管理能力。
核心价值在于：用统一编号与状态机规范协作节奏，用基于角色的访问控制（RBAC）保障数据安全，
用可视化看板把质量趋势变成可决策的指标，并对外提供标准 RESTful API 便于系统集成。

---

## 2. 功能说明

- **问题记录**：标题 / 详细描述 / 严重等级（致命·严重·一般·轻微）/ 分类标签（功能缺陷·性能问题·UI 问题·安全漏洞等）/ 复现步骤 / 发现环境（操作系统·浏览器·应用版本·设备型号）/ 截图与附件上传；自动生成唯一编号 `IS-YYYYMMDD-序号`；支持编辑与删除。
- **验证流程**：五态状态机 `待处理 → 处理中 → 待验证 → 验证通过 → 已关闭`；测试可回退（待验证→处理中），管理员可重开（已关闭→待处理）；每次流转记录操作人 / 时间戳 / 备注，形成完整操作历史链，支持按操作人 + 时间范围查询。
- **用户权限管理（RBAC）**：四类角色——提交者（仅看自己问题）、开发人员（认领 / 更新状态）、测试人员（执行验证）、管理员（全局管理 / 角色分配 / 流程与主题配置）；用户可同时拥有**多个角色**，权限与状态流转按全部角色**取并集**；基于 JWT 的认证与按角色的数据隔离。
- **数据统计看板**：问题趋势图（按时间 / 版本）、状态分布、平均解决周期、解决率、严重等级占比；支持时间范围 + 版本筛选；图表可导出 PNG / Excel。
- **RESTful API**：27 个端点，覆盖创建 / 更新 / 状态变更 / 详情查询，支持分页 + 多条件筛选 + API 认证（详见 `/doc.html`）。
- **部署与界面**：统一登录页、管理后台与用户界面分离、响应式适配桌面与移动端、主题色 / 布局 / 菜单可配置；Docker Compose 一键部署。

---

## 3. 安装步骤

### 3.1 环境依赖

| 场景 | 依赖 |
|---|---|
| 容器部署（推荐） | Docker 20.10+ 与 Docker Compose v2 |
| 本地后端构建 | JDK 17+、Maven 3.8+，本地 MySQL 8 / Redis 7 |
| 本地前端构建 | Node.js 18+ |

### 3.2 克隆仓库

```bash
git clone https://github.com/tongbin102/issueflow.git
cd issueflow
```

### 3.3 安装依赖

- 后端依赖由 Maven 管理，构建时自动下载（`src/backend/pom.xml`）。
- 前端依赖：
  ```bash
  cd src/frontend && npm install
  ```

### 3.4 环境变量配置

部署前复制样例并修改（**生产务必更改密码与 JWT 密钥**）：

```bash
cp .env.example .env
# 编辑 .env：设置 MYSQL_ROOT_PASSWORD、JWT_SECRET（>=32 字节随机串）等
# 注意：.env.example 中的 JWT_SECRET 为空值（仓库不放任何密钥明文），必须自行填入，否则 compose 报错退出
```

`.env.example` 字段说明：

| 变量 | 含义 | 生产是否必填 | 默认值 |
|---|---|---|---|
| `MYSQL_ROOT_PASSWORD` | MySQL root 密码 | ✅ 必填 | `issueflow123` |
| `MYSQL_DB` | 数据库名 | 否 | `issueflow` |
| `MYSQL_USER` | 连接用户名 | 否 | `root` |
| `JWT_SECRET` | JWT 签名密钥 | 🚨 **必填，全链路无兜底** | 无（`prod` 档、`docker-compose.yml`、`.env.example` 均已移除默认值） |
| `JWT_EXPIRATION` | token 有效期（秒） | 否 | `7200` |

#### 🚨 3.4.1 部署前置：`JWT_SECRET`（2026-08-01 安全加固 M1 起为强制项）

生产档 `application-prod.yml` 的 `jwt.secret` 已改为 **`${JWT_SECRET}`（无兜底默认值）**。
**未注入该变量时后端会启动失败**，这是预期行为——宁可不启动，也不带弱密钥对外提供服务。

```bash
# 1) 生成一个 ≥32 字节的强随机密钥
openssl rand -base64 48

# 2) 写入部署环境的 .env（该文件不得提交到 git）
echo "JWT_SECRET=<上一步输出>" >> .env

# 3) 重启后端使其生效
docker compose up -d backend
docker compose logs -f backend     # 确认无 "JWT 密钥" 相关启动异常
```

约束与影响：

- 密钥长度必须 **≥ 32 字节**（HS256 / RFC 7518 下限）。启动期由
  `com.issueflow.security.JwtUtil#init` 校验：为空或过短即抛异常并打印可操作指引。
- **更换密钥会使全部存量 token 立即失效**（所有在线用户需重新登录），请择低峰发布。
- ✅ **M1 已闭环**：`docker-compose.yml` 的硬编码兜底已移除，现为
  `JWT_SECRET: "${JWT_SECRET:?JWT_SECRET 未设置：...}"`。未注入时 `docker compose up` **直接报错退出**
  并打印中文指引，**不会**再静默启动一个用弱密钥的服务。`.env.example` 中该项亦已置空。

#### 3.4.2 部署脚本的 JWT 密钥持久化（`scripts/deploy-23.sh`）

23 号服务器用 `scripts/deploy-23.sh` 部署时，`JWT_SECRET` 按**三级优先**取值，
目的是**避免每次发版都换密钥把在线用户全部踢下线**：

| 优先级 | 来源 | 行为 | 对在线用户的影响 |
|---|---|---|---|
| 1 | 显式传入环境变量 `JWT_SECRET=... bash deploy-23.sh` | 使用该值，并**同步写入**持久化文件 | 等同**主动轮换**，全员需重新登录 |
| 2 | 持久化文件 `/opt/issueflow/.jwt_secret` | 直接复用 | **无影响**，存量 token 继续有效 |
| 3 | 以上都没有（首次部署） | 生成 40 字符强随机密钥并写入该文件（`chmod 600`） | 首次部署，无存量 token |

- 脚本每次运行都会 echo 出「显式传入 / 复用已持久化 / 首次新生成」中的哪一种及密钥长度，
  **但绝不打印密钥内容**，便于运维排查。
- `.jwt_secret` 只存在于 23 号服务器 `/opt/issueflow/` 下，权限 `600`，**不在 git 仓库内**；
  仓库根 `.gitignore` 已追加同名忽略规则，防止本地误生成后被提交。
- 需要**主动轮换**密钥时：`JWT_SECRET="$(openssl rand -base64 48)" DB_PASS='...' bash deploy-23.sh`
  （新值会覆盖持久化文件，后续部署自动沿用新值）。
- 备份 `/opt/issueflow/.jwt_secret` 即可在重装机器后保持存量 token 不失效；
  该文件**严禁**随代码或日志外传。

#### 🚨 3.4.3 部署前置：生产 SQL 初始化与日志行为变更（M2 / M3）

| 项 | 生产（`prod` 档） | 开发（基线 `application.yml`） | 部署侧注意 |
|---|---|---|---|
| `spring.sql.init.mode` | `never` | `always` | **全新空库部署前必须手工导入** `src/backend/src/main/resources/db/schema.sql` + `data.sql`，否则 `ApplicationRunner` 会抛「ADMIN 角色未初始化」而启动失败。已有库无影响。 |
| `mybatis-plus.configuration.log-impl` | `NoLoggingImpl` | `StdOutImpl` | 发布后**生产容器日志不再输出任何 SQL**，请提前周知运维；线上排障改用慢查询日志 / APM。 |

> 改 `never` 的原因：`always` 模式下每次重启都会重跑 `schema.sql` / `data.sql`，存在误重建、
> 覆盖生产数据的风险（2026-08-01 曾发生整库丢失事故，见 `docs/CHANGELOG.md`）。
> 默认管理员 `admin` 由 `IssueFlowApplication#initAdminUser` 写入，**不依赖 `data.sql`**，改动不影响其创建。
>
> 回滚粒度：以上均为**单行配置**，改回原值并重启即可，无需回滚代码。

---

## 4. 使用方法

### 4.1 一键部署（Docker Compose）

```bash
docker compose up -d --build     # 构建并后台启动全部服务
docker compose ps                 # 查看状态
docker compose logs -f backend    # 跟踪后端日志
docker compose down               # 停止并移除容器（数据卷保留）
```

访问地址：

- 前端界面：http://localhost:80
- 后端 API：http://localhost:8080/api
- API 文档（Knife4j）：http://localhost:8080/doc.html

> 首次启动后端自动建表（`db/schema.sql`）+ 初始化 4 个角色，并由 `ApplicationRunner` 写入默认管理员 **admin / admin123**，请登录后立即修改密码。

### 4.2 本地开发（不依赖 Docker）

```bash
# 后端
cd src/backend
# 按需修改 src/main/resources/application-dev.yml 的 MySQL/Redis 连接
mvn clean package -DskipTests
java -jar target/issueflow-backend.jar

# 前端
cd src/frontend
npm run dev      # 开发服务器，默认代理 /api -> http://localhost:8080
npm run build    # 产物输出到 dist/
```

### 4.3 常用脚本（`scripts/`）

```bash
bash scripts/build.sh            # 本地构建前后端
SKIP_FRONTEND=1 bash scripts/build.sh
bash scripts/deploy.sh up        # 等价于 docker compose up -d --build
bash scripts/deploy.sh down
bash scripts/db-init.sh          # 手动将 schema.sql/data.sql 导入本地 MySQL
bash scripts/migrate.sh x.sql    # 执行指定增量迁移 SQL

# —— 数据库备份 / 灾难恢复（详见 4.11）——
bash scripts/backup-issueflow-db.sh   # 备份 issueflow_db（24 号机，cron 每日 02:00 自动执行）
bash scripts/restore-run-order.sh     # 灾难恢复：按正确顺序重建 issueflow_db 结构
bash scripts/verify-other-dbs.sh      # 只读佐证：确认未误伤同实例其他项目库
```

### 4.4 API 快速验证

```bash
# 使用 Postman 导入 tests/api/issueflow.postman_collection.json，
# 或执行：
bash tests/api/test-api.sh
```

### 4.5 角色与默认账号

| 角色 | 说明 | 账号 |
|---|---|---|
| 管理员 ADMIN | 全局管理、角色分配、流程/主题配置 | admin / admin123 |
| 提交者 SUBMITTER | 提交问题、查看自己问题 | 自行创建 |
| 开发人员 DEVELOPER | 认领问题、更新处理状态 | 自行创建 |
| 测试人员 TESTER | 执行验证、记录验证结果 | 自行创建 |

> 管理员在后台「系统管理 → 系统设置」新增用户时，初始密码取自配置项 `site.default_password`（默认 `123456`），建议用户首次登录后自行修改。

### 4.6 站点配置项（`sys_config` 表 `site.*`）

后台「系统管理 → **系统设置**」页（`/admin/system/site`）统一维护以下键：

| 配置键 | 说明 | 默认值 | 是否公开 |
|---|---|---|---|
| `site.name` | 网站名称（登录页大标题、前台/后台侧栏展开态 Logo、浏览器标题） | `issueFlow` | 是 |
| `site.short_name` | 网站简称（侧栏折叠态 Logo） | `IF` | 是 |
| `site.subtitle` | 副标题 | `问题跟踪与流程管理平台` | 是 |
| `site.default_theme` | 前台默认主题 `light/dark/blue/green` | `light` | 是 |
| `site.default_locale` | 默认语言 `zh-CN/en-US` | `zh-CN` | 是 |
| `site.copyright` | 版权信息（前台页脚展示） | `(c) 2026 issueFlow` | 是 |
| `site.icp` | ICP 备案号（前台页脚展示，可为空） | 空 | 是 |
| `site.default_password` | 新增用户默认密码，长度 6~32 | `123456` | **否（敏感）** |

读写接口：

- `GET /api/site/config`：**公开**（登录页可用），仅返回上表 7 个「公开」键，**不下发** `site.default_password`。
- `GET /api/admin/site/config`：需登录 + `site:config:update` 权限，返回全部 8 键，供「系统设置」页回填表单。
- `PUT /api/admin/site/config`：需 `site:config:update` 权限，整表提交保存。

> **生效范围（Phase8 W2 #7）**：后台「用户管理 → 新建用户」已**移除密码输入框**，
> `POST /api/users` 的 `password` 可省略——省略时服务端读取 `site.default_password`
> 并 BCrypt 加密落库。明文默认密码只在服务端与管理端配置接口内流转，前端不持有。
> 编辑用户时密码留空仍表示「保持原密码不变」。

### 4.7 后台菜单结构（`menu` 表驱动）

后台菜单由数据库 `menu` 表驱动（非前端静态路由表），前端按 `path` 映射 i18n 词条渲染，支持中英双语：

```
概览 /admin/index
业务管理 /admin/business ├ 问题列表 /admin/issues  ├ 项目配置 /admin/projects  └ 字典配置 /admin/dicts
                         （「项目配置」页内含模块抽屉，模块维护在此完成）
问题类型 /admin/issue-types
流程管理 └ 流程监控 /admin/flow-monitor  └ 流程配置 /admin/flow-config
系统管理 /admin/system
        ├ 组织管理 /admin/system/organizations   ├ 菜单管理 /admin/system/menus
        ├ 用户管理 /admin/system/users           ├ 角色管理 /admin/system/roles
        ├ 系统设置 /admin/system/site            └ 备份设置 /admin/system/settings
基础设施 /admin/infra（文件配置 / 文件列表 / 配置管理 / Redis 监控 / 定时任务）
```

> 命名说明（Phase8 W1）：`/admin/system/site` 由「网站设置」更名为「**系统设置**」（站点基础配置 + 安全设置）；
> `/admin/system/settings` 由「系统设置」更名为「**备份设置**」（数据初始化 / 数据维护）。路由 `path` 与页面组件均未变更。
> 原独立页「模块配置」（`/admin/modules`）已下线，模块维护统一在「项目配置」页的模块抽屉中完成。
>
> 图标与排序（Phase8 W4）：`menu.icon` 存 **Element Plus 图标组件名**（PascalCase，如 `FolderOpened`）；
> `main.js` 已全量全局注册 `@element-plus/icons-vue`，`SideMenu.vue` 的 `resolveIcon()` 对不存在的名字兜底为 `Grid`，
> 因此在「菜单管理」页填写图标时必须使用真实导出名。W4 已统一全部菜单图标语义并消除 `Grid` 占位；
> 同时将「基础设施」`sort` 调整为 `8`，使其在**根级与「系统管理」平级且排在其下方**（不嵌套）；
> 并清理了两行历史残留菜单墓碑行（id=7 系统设置 `/admin/settings`、id=18 模块配置 `/admin/modules`，均 `deleted=1` 软删、线上不渲染、无用户可见变化）；本次唯二可见改动为 #1（基础设施排序）与 #3（图标统一）。
>
> 前台侧边菜单展开行为（Phase8 W5 #2）：前台（`<SideMenu :type="1" :default-expand-all="true" />`）
> 各层级父菜单**默认全部展开**；用户手动折叠/展开后**刷新保持**（`localStorage['if-menu-closed-type1']` 记录已折叠集合）。
> 后台（`:type="2"`）保持原生默认收起行为，不受影响。
>
> 图标白名单补齐（Phase8 W5 #4）：Phase6 §12.2 的图标自愈白名单已追加 `FolderOpened` / `Share` / `Files` / `SetUp` / `Timer`，
> 并新增幂等迁移 `scripts/V20260805_issueflow_phase6_whitelist_fix.sql` 对受影响菜单图标做守卫式重断言；
> 「单独重跑 Phase6 后需紧接着重跑 W4」的运维约定自此不再必需。

### 4.8 关键字段约定（Phase8 W2）

| 字段 | 表 / DTO | 约束 | 说明 |
|---|---|---|---|
| `org_id` / `orgId` | `user`、`UserReq`、`UserVO` | **可空** | 用户所属组织，关联 `organization.id`；不加外键（组织走逻辑删除）。`UserVO` 额外返回 `orgName`，未归属或组织已删时为 `null`。「存在即覆盖」：编辑时传 `null` 解除归属。 |
| `project_id` / `projectId` | `issue`、`IssueCreateReq`、`IssueUpdateReq` | **必填**（`NOT NULL` + `@NotNull`） | 问题所属项目。自 Phase8 W2 起必填，前端下拉标红星且不可清空。 |

> **破坏性变更提示**：`POST /api/issues` 与 `PUT /api/issues/{id}` 缺少 `projectId` 时将返回 `VALID_ERROR`，
> 历史接口调用方（含 `tests/api/` 下的 Postman 集合与 `test-api.sh`）需补齐该字段。
>
> 存量数据由 `scripts/V20260802_issueflow_phase8_wave2.sql` 回填：
> 未关联项目的问题统一挂到 `MIN(project.id)`（`deleted=0`），回填后才把列收紧为 `NOT NULL`。
> **若 `project` 表无有效行，脚本会自动跳过 `NOT NULL` 改造**（不报错），
> 需先在「项目配置」建至少 1 个项目再重跑脚本。

### 4.9 问题弹窗交互（Phase8 W2 #12）

问题的**提交 / 编辑 / 查看**弹窗统一使用左侧竖形标签页（`components/IssueFormSections.vue`），5 个标签：

`基本信息` · `问题描述` · `附件上传` · `关联信息` · `操作历史`

- 从「基本信息」切走前会校验其必填项（标题 / 类型 / 来源 / 严重等级 / 优先级 / 所属项目），不通过则阻止切换。
- 「问题描述」**非必填**；环境信息四字段（操作系统 / 浏览器 / 应用版本 / 设备型号）并入该标签。
- 窗口宽度 < 768px 时标签自动由左侧竖排切为顶部水平排列。
- 表单组件 `components/IssueForm.vue` 通过 `mode` prop 区分 `submit` / `edit` / `view`（`view` 为只读）。

### 4.10 用户多角色模型（Phase8 W3 #11）

需求 #11 将原来的「单角色」用户模型升级为「多角色」模型：一个用户可同时拥有多个角色，
权限与问题状态流转均按**全部角色的权限并集**判定。

- **数据模型（三处一致，统一由 `UserService` 写入）**：
  - `user.role_id`：保留为**主角色**（关联 `role.id`），兼容既有按单角色判定的逻辑（如 SUBMITTER 仅看自己）。
  - `user.roles`（`VARCHAR(500)` JSON 数组，如 `["ADMIN","TESTER"]`）：是 `user_role` 关系表的**冗余读缓存**，用于列表 / 登录免 N+1 查询；为 `NULL` 时后端自动回落 `user_role` 关系表。
  - `user_role`（新增关系表）：存 `user_id` + `role_code`（**存码不存 id**，JWT / SecurityContext 直接消费，鉴权链路免二次反查）；关联随主体整体替换，无逻辑删除。
- **JWT（jjwt 0.12.5，HS256）**：`roleCode` claim 由单个字符串升级为**角色码数组**；解析侧同时兼容旧版单值 token，升级瞬间存量 token 不失效。
- **主角色语义**（`SecurityUtils.getCurrentRoleCode()`）：持有 ADMIN → 返回 ADMIN；否则返回首个非 SUBMITTER 角色；否则返回首个角色。
- **权限并集**：`PermissionService.requirePermission` 在 ADMIN 直接放行之后，取当前用户全部角色的权限并集做 OR 判定。
- **状态机并集**：`StateMachine.isAllowed(from, to, Collection<String>)` 新增多角色重载，用户任一角色被规则允许即放行（保留原单角色重载委托）。
- **新增接口**：`GET /api/users/{id}/roles` → 返回该用户全部角色码（前端编辑页回显兜底）。
- **前端**：「用户管理」角色下拉由单选改为**多选** `el-select multiple collapse-tags`；表单下发 `roles`（角色码数组）+ 派生 `roleId`（首位对应的主角色 id），列表角色列改为多 `el-tag` 展示。

> 存量数据迁移见 `scripts/V20260803_issueflow_phase8_wave3.sql`（幂等，可重复执行）：
> 建 `user_role` 表、`user` 表加 `roles` 列、按 `user.role_id` 回填两处关系与冗余列、最后执行一组 SELECT 自检。
> **已有数据库必须在 24 号机 MySQL 执行该脚本后再部署后端**，全新库（首次启动自动建表）不受影响。

---

### 4.11 数据库备份与恢复（运维必读）

> **本节源于 2026-08-01 的真实生产事故**：24 号 MySQL 容器 `mysql-gihtg` 被外部项目
> （domainHub）的 reset 脚本重建，`issueflow_db` **整库消失**；而当时
> `/home/jsadmin/db-backups/` 只备份了 `quiz_test` 与 `weekly_report`，
> **从未包含 `issueflow_db`**，导致 7/30 上线以来的业务数据**永久丢失、无法恢复**。
> 最终只能用迁移脚本重建库结构与种子数据。请勿轻视本节。

#### 4.11.1 每日自动备份

| 项 | 值 |
|---|---|
| 脚本 | `scripts/backup-issueflow-db.sh`（部署路径 `/home/jsadmin/backup-issueflow-db.sh`） |
| 执行机 | 24 号（`10.55.3.24`），用户 `jsadmin` |
| cron 计划 | `0 2 * * *`（每日凌晨 **02:00**） |
| 产物 | `/home/jsadmin/db-backups/issueflow_db-YYYYMMDD-HHMMSS.sql.gz` |
| 保留策略 | **最近 7 天**，且**永远至少保留最新 1 份** |
| 日志 | `/home/jsadmin/db-backups/backup.log`（追加，含时间戳）；cron 层致命 stderr 落 `backup.cron.err` |
| 凭据 | `/home/jsadmin/.issueflow-backup.env`（`chmod 600`，属主 `jsadmin`，**不入库**） |

```bash
# 手工立即备份一次（读取受保护凭据文件）
/home/jsadmin/backup-issueflow-db.sh

# 或显式传密码（不会落到 ps / 命令行）
MYSQL_ROOT_PASSWORD='xxx' /home/jsadmin/backup-issueflow-db.sh

# 只跑保留策略、不连库（自检用）
DRY_RUN=1 BACKUP_DIR=/tmp/xxx /home/jsadmin/backup-issueflow-db.sh --prune-only
```

**安全与红线约束（改脚本前务必读懂）**

- `mysql-gihtg` 是**多项目共用实例**（还有 `quiz_test` / `weekly_report` 等）。
  备份只传 `--databases issueflow_db`，**严禁 `--all-databases`**。
- 清理旧备份**只匹配 `issueflow_db-` 前缀**，并额外用
  `^issueflow_db-[0-9]{8}-[0-9]{6}\.sql\.gz$` 严格校验；时间取自**文件名**而非 `mtime`。
  `pre-reinstall-*` / `business-only-*` / `restore-users.sql` 是**其他项目仅存的备份**，
  误删即二次事故。
- 密码**禁止硬编码进仓库**，也**禁止出现在命令行参数**（`ps` 可见）。
  脚本通过 `--defaults-extra-file` 临时配置文件传参，`trap` 保证用完即删。

#### 4.11.2 灾难恢复

**优先用备份恢复**（有备份时永远优先，可恢复业务数据）：

```bash
# 1. 校验备份完整性
gzip -t /home/jsadmin/db-backups/issueflow_db-YYYYMMDD-HHMMSS.sql.gz

# 2. 灌回（dump 内含 CREATE DATABASE / USE，不会波及其他库）
gzip -cd /home/jsadmin/db-backups/issueflow_db-YYYYMMDD-HHMMSS.sql.gz \
  | sudo docker exec -i mysql-gihtg mysql -uroot -p --default-character-set=utf8mb4
```

**无备份时**，才用 `scripts/restore-run-order.sh` 从迁移脚本重建
（⚠ **只能重建表结构与种子数据，业务数据无法找回**）：

```bash
# 把仓库 18 个 SQL 传到 24 号机 /home/jsadmin/issueflow-restore/ 后：
MYSQL_ROOT_PASS='xxx' bash restore-run-order.sh            # 在已存在的空库上灌
MYSQL_ROOT_PASS='xxx' REBUILD=1 bash restore-run-order.sh  # 先 DROP/CREATE issueflow_db 再灌
```

#### 4.11.3 ⚠️ SQL 执行顺序：文件名字母序 ≠ 执行顺序

**这是 2026-08-01 恢复过程中踩过的真实坑，务必遵守。**

`scripts/` 下的文件名日期前缀（`V2026xxxx`）与 phase 逻辑序**并不一致**，
按 `ls` 的字母序执行会因「表不存在 / 字段缺失」而中途失败。
典型反例：`V20260731_issueflow_phase7.sql`（Phase7）字母序排在
`V20260803_issueflow_phase6.sql`（Phase6）**之前**，但 Phase7 依赖 Phase6 建的
`issue.type_id`，先跑 Phase7 必然报错。

**唯一正确顺序**（已固化在 `scripts/restore-run-order.sh` 的 `FILES` 数组中，
请以脚本为准，勿手工拼顺序）：

| # | 文件 | 说明 |
|---|---|---|
| 1 | `db/schema.sql` | Phase1 基线建表 |
| 2 | `db/data.sql` | Phase1 种子：4 条角色字典 |
| 3 | `migrate-add-updated-at.sql` | 补 `updated_at`（须在基线表建好之后） |
| 4 | `V20250730_issueflow_p0.sql` | P0：project / organization / menu + `issue.project_id` |
| 5 | `V20250801_issueflow_phase2.sql` | Phase2：issue_relation / permission / role_permission + `menu.type` |
| 6 | `V20260730_issueflow_phase3.sql` | Phase3：依赖 phase2 的 `menu.type` |
| 7 | `V20260801_issueflow_phase4.sql` | Phase4：module / module_dependency |
| 8 | `V20260802_issueflow_phase5.sql` | Phase5：flow_node / flow_transition |
| 9 | `V20260803_issueflow_phase6.sql` | Phase6：issue_type + `issue.type_id` |
| 10 | `V20260803b_fix_issuetype_unique.sql` | Phase6 补丁，**须紧跟 #9** |
| 11 | `V20260731_issueflow_phase7.sql` | **Phase7 在此，不是第 6 位** —— 依赖 #9 的 `type_id` |
| 12 | `V20260801_issueflow_phase8_wave1.sql` | W1 |
| 13 | `V20260802_issueflow_phase8_wave2.sql` | W2 |
| 14 | `V20260803_issueflow_phase8_wave3.sql` | W3 |
| 15 | `V20260804_issueflow_phase8_wave4.sql` | W4 |
| 16 | `V20260805_issueflow_phase6_whitelist_fix.sql` | W5，**须在 #15 之后** |
| 17 | `V20260806_dynamic_field.sql` | Phase9：动态字段配置四张表 + ISSUE_TYPE 字典化 + `issue.type_code` + 菜单 `field-configs`。依赖 #9 的 `issue_type`、#11 的 `dict/dict_item`、#15/#16 的 menu 终态 |
| 18 | `V20260806b_fieldconfig_permission.sql` | Phase9 补丁：补注册 `field:config:list` / `save` / `delete` 三个权限码 + 授 ADMIN。**须最后执行、且紧跟 #17** —— #17 只插了 menu 记录却漏注册 permission，前端按 `hasPerm('field:config:list')` 过滤后「字段配置」菜单不可见 |

> **另一处坑**：单独重跑 Phase6（#9）会把 W4（#15）设置的
> `FolderOpened / Share / Files / SetUp` 四个菜单图标刷回 `Grid`。
> 任何时候单独重跑 Phase6，**必须紧接着重跑一次 W4**。

#### 4.11.4 字符集：`SET NAMES utf8mb4`

仓库内 **18 个 SQL 已全部**在文件开头声明 `SET NAMES utf8mb4;`。

事故恢复时曾发现其中 7 个缺失该声明（`schema.sql` / `data.sql` /
`migrate-add-updated-at.sql` / `phase8_wave1~4`）。容器内 mysql 客户端的
`default-character-set` 为 `auto`，实测会解析成 **latin1**，导致用
`mysql < xxx.sql` 手工执行时中文被**双重编码**
（`管理员` 正确应为 `E7AEA1E79086E59198`，实测被写成 `C3A7C2AEC2A1...`）。

- 新增迁移脚本时**必须**沿用该声明，位置在注释头之后、第一条业务语句之前。
- `schema.sql` / `data.sql` 由 Spring `spring.sql.init` 启动时加载：项目未覆盖
  `spring.sql.init.separator`，ScriptUtils 使用默认分隔符 `;`，
  `SET NAMES utf8mb4;` 是合法独立语句，**不影响解析**（已实测验证）。
- 🚨 **自 2026-08-01 安全加固 M3 起，生产档 `spring.sql.init.mode = never`**，
  即**生产不再自动加载** `schema.sql` / `data.sql`。全新空库部署必须手工导入，
  详见 [3.4.2 部署前置](#-342-部署前置生产-sql-初始化与日志行为变更m2--m3)。开发档仍为 `always`，本地不受影响。

#### 4.11.5 ⚠️ 权限数据变更后必须刷新 Redis 缓存

> **源于 2026-08-02 真实故障**：Phase9 仅向 `menu` 表插入「字段配置」记录（permission=`field:config:list`），
> 却漏在 `permission` 表注册该码、也未给 ADMIN 授权；补齐 SQL 后，**前端侧边栏仍不显示入口**——
> 根因是 `PermissionService` 把角色权限集缓存在 `perm:role:{roleId}`，**直接写库不会让缓存自动失效**。

凡是**直接改 `permission` / `role_permission` 表**（手动 INSERT 权限码、给角色授权、回收权限）后，
**必须**调用后端刷新接口使缓存失效，否则菜单渲染与接口鉴权仍按旧权限集运行：

```bash
curl -X POST 'http://10.55.3.23:18082/api/roles/permissions/refresh' \
  -H 'Authorization: Bearer <ADMIN_TOKEN>'
```

- 接口前缀以 `RolePermissionController` 实际 `@RequestMapping` 为准（本例为 `/api/roles/permissions/refresh`）；
  生产容器经 `docker-compose` 映射为宿主机 `18082` 端口。
- **前端侧**：`SideMenu.vue` 按 `hasPerm(permission)` 过滤菜单，**admin 不短路放行**；
  只要权限集缺码，菜单即被隐藏。刷新缓存后，**前端必须硬刷新 / 重新登录**才能拉到新 `menus/sidebar`。
- **校验是否生效**：
  - `GET /api/roles/{roleId}/permissions` 应返回新增的权限码；
  - `GET /api/menus/sidebar?type=2` 响应体应包含对应菜单节点（如 `field-configs`）。
- 与之配套：变更类 SQL（含权限 / 菜单）务必登记进 `scripts/restore-run-order.sh` 与本节顺序表（4.11.3），
  避免灾难恢复时漏灌导致权限「幽灵缺失」。

---

### 4.12 前台设计令牌与组件库（任务 #116 前台改版）

任务 #116（A 路，2026-08-01）对 `src/frontend/` 做了深度定制，建立了一套统一的设计令牌与基础组件体系，
便于后续页面复用与四套主题一致适配。**本次仅改动前端，未触碰后端接口契约**（详见 `docs/CHANGELOG.md`）。

**设计令牌（`src/frontend/src/styles/variables.css` 的 `:root`）**：统一 `--if-` 前缀，
含间距阶梯（`--if-space-xs|sm|md|lg|xl` = 4/8/16/24/32px）、字号（`--if-font-h1|h2|h3|base|sm|xs`）、
字重、行高、阴影三级（`--if-shadow-sm|md|lg`）、圆角、过渡、交互态叠加层（`--if-hover-bg`/`--if-active-bg`）、
骨架底色、移动端最小触控热区（`--if-touch-size` = 44px）。**组件层禁止硬编码色值 / 间距 / 阴影，一律 `var(--if-*)`。**

**语义色固定（R4 硬约束）**：状态 / 严重等级 / 优先级语义色
（`--if-color-{success|warning|danger|info|processing}` 及其 `-soft` 浅底）**固定不随主题变化**，
与 `src/frontend/src/utils/format.js` 的 `SEMANTIC_COLORS` 常量一一对齐——保证「红=危险、绿=通过」的认知在任意主题下稳定。
ECharts 无法消费 CSS 变量，故图表中性色由 `utils/chartTheme.js` 在渲染前读取 `body` 计算样式注入。

**四套主题**：`light` / `dark` / `blue` / `green`，由 `themeStore.applyFrontTheme()` 写入
**仅 `body[data-if-theme=...]`**（严禁写 `document.documentElement`，避免污染后台）。`styles/themes.css` 中
语义色不覆盖，仅覆盖随主题变化的令牌（阴影 / 交互底 / 骨架）；dark 主题额外提升语义浅底不透明度。

**基础组件库（`src/frontend/src/components/base/`，前缀 `If*`）**：

| 组件 | 用途 | 关键 props / 事件 |
|---|---|---|
| `IfLoading` | 加载态（骨架屏 / 圆环） | `loading` / `type` / `rows` / `minHeight` |
| `IfCard` | 卡片容器（标题 / 副标题 / 扩展 / 底部插槽） | `title` / `hoverable` / `clickable` / `loading` / `@click` |
| `IfButton` | 按钮封装（移动端 44px 热区 + `block`） | 透传 `el-button` 同名 props / `@click` |
| `IfTag` | 语义标签（固定语义色） | `semantic` / `label` / `dot` / `plain` |
| `IfEmptyState` | 空状态（empty / noResult / error） | `scene` / `title` / `description` / `@action` |
| `IfModal` | 轻量确认 / 信息框（**仅轻量确认**） | `v-model` / `confirmType` / `@confirm` / `@cancel` / `@closed` |
| `IfPageHeader` | 页面级页头 | `title` / `subtitle` / `showBack` / `#actions` / `@back` |

> 边界约定：`IfModal` **只用于轻量确认 / 简短信息**；任何表单编辑、多字段录入、详情浏览一律继续用
> `FormDrawer` / `IssueDetailDrawer`，不混用两套容器。

**响应式断点（PRD §2.5）**：桌面 `≥1280px` / 平板 `768–1279px` / 移动 `<768px`。CSS 媒体查询写字面量
（`styles/theme.css`），与 `:root` 的 `--if-bp-mobile`(768) / `--if-bp-desktop`(1280) 保持一致供 JS 读取；
移动端列表降级为卡片流、弹层近全屏、按钮 / 分页 / 表单控件触控热区 ≥44px。

**i18n 约定**：新增文案一律走现有 `src/frontend/src/locales/{zh-CN,en-US}` 键体系（中英双语成对），
模板不硬编码中文；空状态 / 最近问题 / 列表筛选等场景文案已补齐。

---

## 5. 目录结构说明

```
issueFlow/
├── README.md                  # 项目说明（本文件）
├── LICENSE                    # MIT 许可证
├── .gitignore                 # 忽略规则（编译产物/依赖/临时文件/环境配置）
├── .env.example               # 环境变量样例（复制为 .env 使用）
├── docker-compose.yml         # 一键部署编排（mysql/redis/backend/frontend）
├── src/                       # 源代码（按应用分子目录）
│   ├── backend/               # Spring Boot 后端（含 pom.xml / Dockerfile / .dockerignore）
│   │   ├── src/main/java/com/issueflow/   # 业务源码：controller/service/mapper/entity/config/security/common/enums/util/handler/dto
│   │   ├── src/main/resources/db/         # schema.sql（建表）/ data.sql（初始角色）
│   │   └── src/test/java/com/issueflow/   # JUnit 测试（AuthControllerTest / IssueFlowTest 等）
│   └── frontend/              # Vue3 前端（含 package.json / vite.config.js / Dockerfile / nginx.conf）
│       └── src/               # 前端源码：api / components / views / store / router / layouts / utils / styles
├── docs/                      # 过程文档
│   ├── prd.md                 # 产品需求文档
│   ├── architecture.md        # 架构设计 + 任务分解（含 Mermaid 图）
│   ├── CHANGELOG.md           # 变更日志（Keep a Changelog 格式）
│   ├── design-notes.md        # 设计思路摘要
│   ├── issues/                # 问题记录 / 实现笔记 / 已知限制
│   └── adr/                   # 架构决策记录（ADR），编号命名如 001-技术选型决策.md
├── assets/                    # 静态资源（图片/字体/数据），当前含 images/logo.svg
│   └── images/
├── tests/                     # 测试（与 src/ 对应）
│   └── api/                   # 跨应用 API 集成测试（Postman 集合 + curl 脚本）
└── scripts/                   # 辅助脚本（均含顶部注释：用途/执行方式/参数）
    ├── build.sh               # 本地构建前后端
    ├── deploy.sh              # Docker 部署
    ├── db-init.sh             # 数据库初始化
    └── migrate.sh             # 数据迁移统一入口
```

### 各目录职责

- **`src/`**：全部源代码。后端与前端各自独立可构建（`src/backend` 产出 jar，`src/frontend` 产出静态站点）。
- **`docs/`**：需求、架构、变更、设计与决策的过程资产；`adr/` 存放技术决策记录，`issues/` 存放问题跟踪。
- **`assets/`**：仓库级静态资源（logo、插图、样例数据），随仓库提交。
- **`tests/`**：跨应用测试；单元测试随源码放置以保证可构建，`tests/api` 提供端到端 API 验证。
- **`scripts/`**：构建、部署、数据库初始化与迁移脚本，统一带头部说明，降低使用门槛。

---

## 说明与已知限制

- 本仓库由 AI 协作团队（产品经理 / 架构师 / 工程师 / QA）生成。**前端已在本机 `npm run build` 验证通过**；**后端因生成环境无 JDK 17 未做本地编译**，请在 JDK 17 环境 `mvn package` 或 `docker compose up` 后实测（架构与代码已严格对齐，风险低）。
- 默认管理员 `admin/admin123` 请在首次登录后立即修改密码。
- 认证采用单 JWT + Redis 黑名单（2h，无 refreshToken）；附件默认存于后端容器卷 `/data/attachments`，单文件 ≤20MB，暂未做病毒扫描与扩展名白名单。
- 生产部署务必修改 `.env` 中的 `MYSQL_ROOT_PASSWORD` 与 `JWT_SECRET`。
  自 2026-08-01 安全加固 M1 起 **`JWT_SECRET` 为生产强制项**（`prod` 档、`docker-compose.yml`、
  `.env.example` 三处兜底默认值均已移除，未注入时 compose 直接报错退出），
  完整前置步骤见 [3.4.1 部署前置](#-341-部署前置jwt_secret2026-08-01-安全加固-m1-起为强制项)。
  用 `scripts/deploy-23.sh` 部署时密钥会持久化到服务器 `/opt/issueflow/.jwt_secret` 并在后续发版复用，
  **发版不再踢掉在线用户**，详见 3.4.2。
- 方法级安全（`@EnableMethodSecurity`）已开启，但 `@PreAuthorize` **尚未逐接口铺开**。
  编写表达式时注意：`JwtAuthenticationFilter` 写入的 authority 为**裸角色码**（如 `ADMIN`，
  **不带 `ROLE_` 前缀**），必须使用 `hasAuthority('ADMIN')`，**严禁 `hasRole('ADMIN')`**
  ——后者会自动补 `ROLE_` 前缀，导致**所有请求 403**。
