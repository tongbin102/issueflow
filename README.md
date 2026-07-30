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
- **用户权限管理（RBAC）**：四类角色——提交者（仅看自己问题）、开发人员（认领 / 更新状态）、测试人员（执行验证）、管理员（全局管理 / 角色分配 / 流程与主题配置）；基于 JWT 的认证与按角色的数据隔离。
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
