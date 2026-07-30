#!/usr/bin/env bash
#
# deploy-23.sh — issueFlow 应用部署到 23 号服务器（诊断 + 部署 + 确认）
#
# 【用途】
#   1) 只读扫描 23 号服务器端口占用，排查 nginx/tomcat/node/java/docker 等运行服务，
#      检查工作目录与日志目录是否与本应用冲突；
#   2) 自动从已占用端口中挑选空闲端口部署 issueFlow（前端 / 后端）；
#   3) 在 23 上用 Docker Compose 启动 frontend + backend（MySQL 在 24 号，Redis 在 23 号）；
#   4) 输出端口占用情况与服务运行状态确认信息。
#
# 【前置条件】
#   - 23 号已安装 docker 与 docker compose v2（docker --version / docker compose version）。
#   - 项目代码已就位：
#       * 方式A（默认）：脚本自动 git clone https://github.com/tongbin102/issueflow 到 PROJECT_DIR；
#       * 方式B：你已把代码 scp 到 PROJECT_DIR，并设置 USE_LOCAL_DIR=1。
#   - MySQL 在 24 号（先跑 setup-mysql-24.sh）、Redis 在 23 号（先跑 setup-redis-23.sh），
#     得到专用库 / 用户 / 密码，并填入下方 DB_*/REDIS_* 变量。
#
# 【执行方式】
#   DB_PASS='专用用户密码' bash deploy-23.sh
#   （建议先 bash -n deploy-23.sh 做语法自检）
#
# 【可调参数（环境变量覆盖）】
#   WEB_PORT    前端对外端口（默认 18080，自动避开占用）
#   API_PORT    后端对外端口（默认 18081，自动避开占用）
#   PROJECT_DIR 项目目录（默认 /opt/issueflow）
#   DB_HOST     MySQL 地址（24 号，默认 10.55.3.24）
#   DB_PORT     24 号 MySQL 端口（默认 3306）
#   DB_NAME     专用库名（默认 issueflow_db）
#   DB_USER     专用用户（默认 issueflow）
#   DB_PASS     专用用户密码（必须设置，脚本不硬编码）
#   REDIS_HOST  Redis 地址（23 号，默认 10.55.3.23）
#   REDIS_PORT  Redis 端口（默认 6379）
#   JWT_SECRET  生产 JWT 密钥（默认随机生成）
#
set -euo pipefail

echo "============================================================"
echo " issueFlow 部署诊断与上线 — 23 号服务器 ($(date '+%F %T'))"
echo "============================================================"

command -v docker >/dev/null || { echo "❌ 未安装 docker"; exit 1; }
sudo docker compose version >/dev/null 2>&1 || { echo "❌ 未安装 docker compose v2"; exit 1; }

# ---------- 0. 清理旧容器（释放端口，保证端口稳定可预测）----------
echo; echo "===== [0/5] 清理旧 issueflow 容器（释放端口）====="
sudo docker rm -f issueflow-backend issueflow-frontend 2>/dev/null || true

# ---------- 1. 端口占用扫描 ----------
echo; echo "===== [1/5] 端口占用扫描（监听中的 TCP 端口）====="
if command -v ss >/dev/null; then
  sudo ss -tlnp | awk 'NR>1 {print $4, $NF}'
else
  netstat -tlnp 2>/dev/null | awk 'NR>1 {print $4, $NF}'
fi

# ---------- 2. 运行服务排查 ----------
echo; echo "===== [2/5] 运行服务排查（nginx/tomcat/node/java/docker/mysql/redis）====="
ps -eo pid,user,comm,args --sort=comm 2>/dev/null | grep -E "nginx|tomcat|node|java|dockerd|mysqld|redis" | grep -v grep || echo "（未发现上述关键进程）"
echo "--- 关键进程工作目录（排查工作目录冲突）---"
for pid in $(pgrep -f "nginx|tomcat|node|java|mysqld|redis" 2>/dev/null); do
  cwd=$(readlink "/proc/$pid/cwd" 2>/dev/null || echo "?")
  echo "pid=$pid cwd=$cwd (常见日志目录: $cwd/logs)"
done
echo "--- 本应用目录冲突检查 ---"
for d in /opt/issueflow /var/log/issueflow /data/issueflow-attachments; do
  [ -e "$d" ] && echo "⚠️  已存在: $d" || echo "✓ 不存在: $d"
done

# ---------- 3. 选择空闲端口 ----------
echo; echo "===== [3/5] 选择空闲端口 ====="
is_free() { ! (sudo ss -tlnp 2>/dev/null | awk '{print $4}' | grep -qE "[:.]${1}\$"); }
pick_port() { local p=${1:-18080}; while ! is_free "$p"; do p=$((p+1)); done; echo "$p"; }
WEB_PORT=$(pick_port "${WEB_PORT:-18080}")
API_PORT=$(pick_port "${API_PORT:-$((WEB_PORT+1))}")
echo "✓ 前端端口 = $WEB_PORT ，后端端口 = $API_PORT"

# ---------- 4. 准备代码与 23 专用 compose ----------
echo; echo "===== [4/5] 准备代码与 23 专用 compose ====="
PROJECT_DIR="${PROJECT_DIR:-/opt/issueflow}"
DB_HOST="${DB_HOST:-10.55.3.24}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-issueflow_db}"
DB_USER="${DB_USER:-issueflow}"
DB_PASS="${DB_PASS:-}"
REDIS_HOST="${REDIS_HOST:-10.55.3.23}"
REDIS_PORT="${REDIS_PORT:-6379}"

if [ -z "$DB_PASS" ]; then
  echo "❌ 未设置 DB_PASS（issueFlow 专用用户密码）。请先在 24 号跑 setup-mysql-24.sh 得到密码后："
  echo "   DB_PASS='你的密码' bash deploy-23.sh"
  exit 1
fi
JWT_SECRET="${JWT_SECRET:-$(head -c 48 /dev/urandom | base64 | tr -d '/+=' | head -c 40)}"

if [ "${USE_LOCAL_DIR:-0}" != "1" ]; then
  if [ ! -d "$PROJECT_DIR/.git" ]; then
    echo "→ git clone 项目到 $PROJECT_DIR"
    if [ ! -d "$PROJECT_DIR" ]; then
      sudo mkdir -p "$PROJECT_DIR"
      sudo chown "$(id -un)" "$PROJECT_DIR"
    fi
    git clone --depth 1 https://github.com/tongbin102/issueflow "$PROJECT_DIR"
  else
    echo "→ 更新已有仓库"; git -C "$PROJECT_DIR" pull --ff-only
  fi
else
  [ -d "$PROJECT_DIR" ] || { echo "❌ USE_LOCAL_DIR=1 但 $PROJECT_DIR 不存在，请先 scp 代码"; exit 1; }
fi

cat > "$PROJECT_DIR/docker-compose.23.yml" <<EOF
services:
  backend:
    build:
      context: ./src/backend
      dockerfile: Dockerfile
    container_name: issueflow-backend
    environment:
      SPRING_PROFILES_ACTIVE: prod
      MYSQL_HOST: ${DB_HOST}
      MYSQL_PORT: ${DB_PORT}
      MYSQL_DB: ${DB_NAME}
      MYSQL_USER: ${DB_USER}
      MYSQL_PASSWORD: ${DB_PASS}
      REDIS_HOST: ${REDIS_HOST}
      REDIS_PORT: ${REDIS_PORT}
      REDIS_PASSWORD: ""
      REDIS_DB: 0
      ATTACHMENT_BASE_PATH: /data/attachments
      JWT_SECRET: ${JWT_SECRET}
      JWT_EXPIRATION: 7200
    ports:
      - "${API_PORT}:8080"
    volumes:
      - issueflow-attachments:/data/attachments
    restart: unless-stopped
    networks:
      - issueflow-net

  frontend:
    build:
      context: ./src/frontend
      dockerfile: Dockerfile
    container_name: issueflow-frontend
    depends_on:
      - backend
    ports:
      - "${WEB_PORT}:80"
    restart: unless-stopped
    networks:
      - issueflow-net

volumes:
  issueflow-attachments:
networks:
  issueflow-net:
    driver: bridge
EOF
echo "✓ 已生成 $PROJECT_DIR/docker-compose.23.yml（MySQL 指向 24 号、Redis 指向 23 号，不启本地 mysql/redis 容器）"

# ---------- 5. 部署并确认 ----------
echo; echo "===== [5/5] 部署并确认 ====="
cd "$PROJECT_DIR"
sudo docker compose -f docker-compose.23.yml up -d --build
sleep 8
echo "--- 容器状态 ---"
sudo docker compose -f docker-compose.23.yml ps
echo "--- 端口监听确认 ---"
ss -tlnp 2>/dev/null | grep -E ":$WEB_PORT|$API_PORT" || echo "(端口未在监听，请查看日志: docker compose -f docker-compose.23.yml logs backend)"
echo "--- 访问地址 ---"
echo "前端: http://$(hostname -I 2>/dev/null | awk '{print $1}'):$WEB_PORT"
echo "API:  http://$(hostname -I 2>/dev/null | awk '{print $1}'):$API_PORT/api"
echo "完成 ✅"
