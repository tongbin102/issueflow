# 部署现状与 23 号线上真相（DEPLOYMENT）

> 本文档集中记录 issueFlow 的**生产部署真相**，专门防一个坑：
> **只看仓库通用模板 `docker-compose.yml`（含 `:-` 兜底写法）就误判线上「裸奔弱密钥」**。
> 详细部署步骤见 README §3.4 系列（特别是 §3.4.2 `scripts/deploy-23.sh`）。
>
> 最后更新：2026-08-06

## 1. 23 号线上部署方式（权威）

- **部署入口**：`scripts/deploy-23.sh`（README §3.4.2 详述），**不是**手动 `docker compose up`。
- **动态生成 compose**：脚本每次运行都用 `cat > /opt/issueflow/docker-compose.23.yml` **整体重写**该文件
  （含内联 `JWT_SECRET`、MySQL 指向 24 号 `10.55.3.24`、Redis 在 23 号 `10.55.3.23`）。
  - ⚠️ **因此 `docker-compose.23.yml` 是脚本产物，手动修改会在下次部署被覆盖，请勿手改。**
- **代码来源**：23 号 `/opt/issueflow` 当前不是 git 仓库（早期拷贝部署），但 `deploy-23.sh` 默认会
  `git clone` / `git pull` 更新代码（脚本「方式 A」），后续可规整为 git 部署。
- **容器名 / 网络**：`issueflow-backend`、`issueflow-frontend`、`issueflow-attachments` 卷、`issueflow-net` 网络。

## 2. JWT 密钥（M1 安全加固已达标）

- **密钥类型**：自定义 **40 字符强随机密钥**（≥32 字节，满足 HS256 / RFC 7518 下限），
  **非弱默认 `issueflow-secret-key-2024-...`**。
- **持久化**：存于 23 号 `/opt/issueflow/.jwt_secret`（权限 `600`，**不进 git**，根 `.gitignore` 已忽略）。
- **三级优先**（详见 README §3.4.2）：

  | 优先级 | 来源 | 行为 | 对在线用户 |
  |---|---|---|---|
  | 1 | 显式 `JWT_SECRET=... bash deploy-23.sh` | 使用该值并写入 `.jwt_secret` | 主动轮换，全员重登 |
  | 2 | 复用 `.jwt_secret` | 直接读取 | **无影响**，存量 token 继续有效 |
  | 3 | 首次部署 | 新生成 40 字符密钥并持久化 | 首次部署，无存量 |

- **M1 结论**：密钥不进 git + `600` 权限 + 自定义强密钥 + 持久化复用 → **M1 安全目标已达标**。
  **无需换密钥**；换密钥只会让全员重登且无安全收益。

## 3. 实际端口（实测 2026-08-06）

- 前端：`18081`
- 后端 API：`18082`（README 示例写的 `18080/18081` 是脚本 `pick_port` 默认值，实际由端口占用扫描选定）
- 访问地址：前端 `http://10.55.3.23:18081`、API `http://10.55.3.23:18082/api`

## 4. ⚠️ 已知坑：手动改 compose 不持久（2026-08-06 记录）

- 曾手动把 `docker-compose.23.yml` 的 `JWT_SECRET` 改为 `${JWT_SECRET:?...}` + 新增 `.env`
  （密钥值不变，未触发全员重登，backend 仅短暂重启）。
- **该改动当前运行态生效，但下次 `deploy-23.sh` 会 `cat >` 覆盖回内联（从 `.jwt_secret` 读），不持久。**
- 同时引入 `.env` 与 `.jwt_secret` 两套密钥文件并存（值相同，无害）。
- **持久合规建议（二选一）**：
  - **A. 回滚手动改动**：恢复 compose 内联标准态 + 删 `.env`，让 23 号回到 `deploy-23.sh` 单一管理
    （M1 仍达标，密钥值不变不重登）。
  - **B. 改 `scripts/deploy-23.sh`**：让其生成的 compose 用 `JWT_SECRET: "${JWT_SECRET:?...}"`
    （heredoc 内转义 `$` 保留字面 `:?`），并写 `.env` / `env_file` 提供变量来源，使 `:?` 规范在 23 号
    **持久**生效。改后 `commit` + `push`，下次部署自动采用。

## 5. 运维速查命令

```bash
# SSH 到 23 号
ssh -i .deploy_key -p 52113 jsadmin@10.55.3.23
cd /opt/issueflow

# 看运行态密钥是否为弱默认（弱默认含 'issueflow-secret-key-2024' 子串）
S=$(sudo docker exec issueflow-backend printenv JWT_SECRET)
echo "len=${#S}"; echo "$S" | grep -q 'issueflow-secret-key-2024' && echo 'WEAK' || echo 'STRONG_OR_CUSTOM'

# 看容器状态
sudo docker ps --format '{{.Names}}\t{{.Status}}'

# 标准部署（复用 .jwt_secret，不重登）
DB_PASS='<专用用户密码>' bash scripts/deploy-23.sh
```
