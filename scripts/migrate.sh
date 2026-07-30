#!/usr/bin/env bash
# ===========================================================================
# 用途：执行数据库增量迁移脚本（统一入口）
#       当前版本由 Spring Boot 在启动时自动应用 schema.sql（mybatis-plus 初始化），
#       尚无独立迁移文件；本脚本预留为后续版本「增量迁移」的统一执行入口。
# 执行方式：bash scripts/migrate.sh <migration-file.sql>
# 所需参数：$1 待执行的迁移 SQL 文件路径
# 环境变量：MYSQL_HOST / MYSQL_PORT / MYSQL_USER / MYSQL_PASSWORD / MYSQL_DB（同 db-init.sh）
# 前置依赖：本地已安装 mysql 客户端
# ===========================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

if [ $# -lt 1 ]; then
  echo "用法：bash scripts/migrate.sh <migration-file.sql>" >&2
  exit 1
fi

MIGRATION="$1"
if [ ! -f "$MIGRATION" ]; then
  echo "错误：迁移文件不存在: $MIGRATION" >&2
  exit 1
fi

: "${MYSQL_HOST:=127.0.0.1}"
: "${MYSQL_PORT:=3306}"
: "${MYSQL_USER:=root}"
: "${MYSQL_DB:=issueflow}"

if [ -z "${MYSQL_PASSWORD:-}" ]; then
  echo "错误：请通过环境变量 MYSQL_PASSWORD 提供 MySQL 密码" >&2
  exit 1
fi

echo "==> 执行迁移: $MIGRATION"
mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DB" < "$MIGRATION"
echo "==> 迁移完成"
