#!/usr/bin/env bash
# ===========================================================================
# 事故恢复佐证脚本：证明 issueFlow 灌库未误伤同实例其他项目库
# 只读：全程仅 SELECT / SHOW，不含任何写操作
# ===========================================================================
set -uo pipefail

CONTAINER="mysql-gihtg"
ROOT_PASS="${MYSQL_ROOT_PASS:?需通过环境变量 MYSQL_ROOT_PASS 提供 root 密码}"
MYSQL=(sudo docker exec -i "$CONTAINER" mysql -uroot -p"$ROOT_PASS" --default-character-set=utf8mb4)

# 注意 `< /dev/null`：docker exec -i 会吞掉调用方循环的 stdin，
# 导致 while read 只跑一轮就退出（首次实测只统计到 1 张表）。
run_sql() { "${MYSQL[@]}" "$@" < /dev/null 2>/dev/null; }

for db in quiz_test weekly_report; do
  echo "=============================================="
  echo " 数据库: $db"
  echo "=============================================="
  tables="$(run_sql -N -e "SELECT table_name FROM information_schema.TABLES WHERE table_schema='$db' ORDER BY table_name;")"
  cnt=0
  total=0
  while IFS= read -r t; do
    [ -z "$t" ] && continue
    rows="$(run_sql -N -e "SELECT COUNT(*) FROM \`$db\`.\`$t\`;")"
    printf '  %-34s %8s 行\n' "$t" "$rows"
    cnt=$((cnt + 1))
    total=$((total + rows))
  done <<< "$tables"
  echo "  ----------------------------------------------"
  echo "  表数量: $cnt   总行数: $total"
  echo
done
