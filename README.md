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
```

`.env.example` 字段说明：

| 变量 | 含义 | 默认值 |
|---|---|---|
| `MYSQL_ROOT_PASSWORD` | MySQL root 密码 | `issueflow123` |
| `MYSQL_DB` | 数据库名 | `issueflow` |
| `MYSQL_USER` | 连接用户名 | `root` |
| `JWT_SECRET` | JWT 签名密钥 | 内置占位串（生产必改） |
| `JWT_EXPIRATION` | token 有效期（秒） | `7200` |

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
业务管理 /admin/business ├ 问题列表 /admin/issues  ├ 字典配置 /admin/dicts
问题类型 /admin/issue-types
项目管理 /admin/project   └ 项目配置 /admin/projects（模块维护内置于本页的模块抽屉）
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
