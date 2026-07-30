#!/usr/bin/env bash
#
# setup-mysql-24.sh — 在 24 号服务器为 issueFlow 新建专用库 / 用户
#
# 【真实拓扑】
#   24 号的 MySQL 是 docker 容器 mysql-gihtg（mysql:8.0，3306 已映射）。
#   宿主机无 mysql 客户端，故通过 `sudo docker exec mysql-gihtg mysql` 执行 SQL。
#   root 密码从容器环境变量 MYSQL_ROOT_PASSWORD 自动提取，无需手输、不打印明文；
#   仅专用用户 issueflow 的密码会回显（部署 23 时需要）。
#
# 【用途】
#   1) 只读检查：版本、监听端口、已有库/用户；
#   2) 新建专用库 issueflow_db 与专用用户 issueflow（IF NOT EXISTS，不覆盖已有）；
#   3) 输出确认摘要与专用用户密码。
#
# 【执行方式】 bash setup-mysql-24.sh
# 【可调参数】 DB_NAME / DB_USER / DB_PASS / ALLOW_HOST
#
set -euo pipefail

MYSQL_CONTAINER="mysql-gihtg"
DB_NAME="${DB_NAME:-issueflow_db}"
DB_USER="${DB_USER:-issueflow}"
DB_PASS="${DB_PASS:-$(head -c 16 /dev/urandom | base64 | tr -d '/+=' | head -c 16)}"
ALLOW_HOST="${ALLOW_HOST:-%}"

# --- 自动提取 root 密码（来自容器环境变量，全程不打印明文）---
ROOT_PASS="$(sudo docker inspect "$MYSQL_CONTAINER" 2>/dev/null \
  | grep -oP 'MYSQL_ROOT_PASSWORD=\K[^"]+' | head -1)"
if [ -z "$ROOT_PASS" ]; then
  echo "❌ 无法从容器 $MYSQL_CONTAINER 提取 MYSQL_ROOT_PASSWORD，请确认容器名/状态" >&2
  exit 1
fi
MYSQL_CMD=(sudo docker exec -i "$MYSQL_CONTAINER" mysql -uroot -p"$ROOT_PASS")

echo "============================================================"
echo " issueFlow MySQL 初始化 — 24 号 ($(date '+%F %T'))"
echo " 容器: $MYSQL_CONTAINER"
echo "============================================================"

echo "===== [1/3] 只读检查 ====="
"${MYSQL_CMD[@]}" -e "SELECT VERSION();" 2>/dev/null
"${MYSQL_CMD[@]}" -e "SHOW VARIABLES LIKE 'port';" 2>/dev/null
echo "--- 已有数据库 ---"
"${MYSQL_CMD[@]}" -e "SHOW DATABASES;" 2>/dev/null
echo "--- 已有用户 ---"
"${MYSQL_CMD[@]}" -e "SELECT user,host FROM mysql.user;" 2>/dev/null

echo "===== [2/3] 新建专用库与用户（IF NOT EXISTS，不影响已有数据）====="
"${MYSQL_CMD[@]}" <<SQL 2>/dev/null
CREATE DATABASE IF NOT EXISTS \`$DB_NAME\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '$DB_USER'@'$ALLOW_HOST' IDENTIFIED BY '$DB_PASS';
GRANT ALL PRIVILEGES ON \`$DB_NAME\`.* TO '$DB_USER'@'$ALLOW_HOST';
FLUSH PRIVILEGES;
SQL

echo "===== [3/3] 确认摘要 ====="
"${MYSQL_CMD[@]}" -e "SHOW DATABASES LIKE '$DB_NAME'; SELECT user,host FROM mysql.user WHERE user='$DB_USER';" 2>/dev/null
echo
echo "✅ 已确保存在专用数据库: $DB_NAME"
echo "✅ 已确保存在专用用户:   $DB_USER@$ALLOW_HOST"
echo "🔑 专用用户密码（部署 23 时需用到，请保存）: $DB_PASS"
echo "⚠️  均为 CREATE IF NOT EXISTS / GRANT，未修改或删除任何已有库、用户或数据。"
