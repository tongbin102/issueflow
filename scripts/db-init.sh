#!/usr/bin/env bash
# ===========================================================================
# 用途：将后端建表/初始化 SQL 手动导入本地 MySQL
#       （适用于不想依赖 Spring Boot 启动时自动初始化 schema 的场景）
# 执行方式：bash scripts/db-init.sh
# 所需参数（环境变量）：
#   MYSQL_HOST     默认 127.0.0.1
#   MYSQL_PORT     默认 3306
#   MYSQL_USER     默认 root
#   MYSQL_PASSWORD 必填（无默认值）
#   MYSQL_DB       默认 issueflow
# 前置依赖：本地已安装 mysql 客户端（mysql CLI）
# ===========================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SCHEMA="$ROOT/src/backend/src/main/resources/db/schema.sql"
DATA="$ROOT/src/backend/src/main/resources/db/data.sql"

: "${MYSQL_HOST:=127.0.0.1}"
: "${MYSQL_PORT:=3306}"
: "${MYSQL_USER:=root}"
: "${MYSQL_DB:=issueflow}"

if [ -z "${MYSQL_PASSWORD:-}" ]; then
  echo "错误：请通过环境变量 MYSQL_PASSWORD 提供 MySQL 密码" >&2
  exit 1
fi

echo "==> 导入表结构: $SCHEMA"
mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DB" < "$SCHEMA"
echo "==> 导入初始数据（4 个角色）: $DATA"
mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DB" < "$DATA"
echo "==> 数据库初始化完成"
