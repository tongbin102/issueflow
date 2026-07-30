#!/usr/bin/env bash
#
# issueFlow API 冒烟脚本（bash + curl）
# 依赖：curl、jq（apt install jq / brew install jq）
# 用法：
#   1) 启动依赖：docker compose up -d   （提供 mysql + redis）
#   2) 启动后端：mvn -f backend spring-boot:run   （或 java -jar backend/target/issueflow-backend.jar）
#   3) 赋予权限并运行：chmod +x docs/api-tests/test-api.sh && ./docs/api-tests/test-api.sh
#
# 脚本依次：login(取 token) → createIssue → pageIssues → changeStatus(多条) → dashboard/overview → export
# 每步打印 HTTP 状态与关键返回；任一关键步骤失败即退出。
#
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
USERNAME="${USERNAME:-admin}"
PASSWORD="${PASSWORD:-admin123}"

AUTH_HEADER=""

http_status() {
  # 从 curl -w 输出中解析状态；这里用变量返回
  echo "$1"
}

echo "=============================================="
echo "[1] 登录 login"
LOGIN_RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"${USERNAME}\",\"password\":\"${PASSWORD}\"}")
LOGIN_CODE=$(echo "${LOGIN_RESP}" | tail -n1)
LOGIN_BODY=$(echo "${LOGIN_RESP}" | sed '$d')
echo "HTTP ${LOGIN_CODE}"
echo "${LOGIN_BODY}"
if [ "${LOGIN_CODE}" != "200" ]; then
  echo "登录失败，终止。请确认后端已启动且账号正确。" >&2
  exit 1
fi
TOKEN=$(echo "${LOGIN_BODY}" | jq -r '.data.token')
if [ -z "${TOKEN}" ] || [ "${TOKEN}" = "null" ]; then
  echo "未获取到 token，终止。" >&2
  exit 1
fi
AUTH_HEADER="Authorization: Bearer ${TOKEN}"
echo "token=${TOKEN:0:24}..."

echo "=============================================="
echo "[2] 创建问题 createIssue"
CREATE_RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/issues" \
  -H "${AUTH_HEADER}" \
  -F "issue={\"title\":\"冒烟测试问题\",\"severity\":2,\"description\":\"描述\",\"tags\":\"smoke,qa\"};type=application/json")
CREATE_CODE=$(echo "${CREATE_RESP}" | tail -n1)
CREATE_BODY=$(echo "${CREATE_RESP}" | sed '$d')
echo "HTTP ${CREATE_CODE}"
echo "${CREATE_BODY}"
ISSUE_ID=$(echo "${CREATE_BODY}" | jq -r '.data.id')
echo "issueId=${ISSUE_ID}"

echo "=============================================="
echo "[3] 分页查询 pageIssues"
curl -s -w "\nHTTP %{http_code}\n" "${BASE_URL}/api/issues?page=1&size=10" -H "${AUTH_HEADER}"

echo "=============================================="
echo "[4] 状态流转 changeStatus (0→1→2→3→4)"
for TO in 1 2 3 4; do
  REMARK=""
  if [ "${TO}" = "4" ]; then REMARK="验证通过，关闭"; fi
  RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/issues/${ISSUE_ID}/status" \
    -H "${AUTH_HEADER}" -H "Content-Type: application/json" \
    -d "{\"toStatus\":${TO},\"remark\":\"${REMARK}\"}")
  CODE=$(echo "${RESP}" | tail -n1)
  BODY=$(echo "${RESP}" | sed '$d')
  echo "toStatus=${TO} HTTP ${CODE} -> $(echo "${BODY}" | jq -c '{code:.code,status:.data.status}')"
done

echo "=============================================="
echo "[5] 看板概览 dashboard/overview"
curl -s -w "\nHTTP %{http_code}\n" "${BASE_URL}/api/dashboard/overview" -H "${AUTH_HEADER}" | head -c 800
echo

echo "=============================================="
echo "[6] 看板导出 dashboard/export"
curl -s -w "HTTP %{http_code}\n" -o dashboard.xlsx "${BASE_URL}/api/dashboard/export" -H "${AUTH_HEADER}"
echo "已保存 dashboard.xlsx（大小：$(wc -c < dashboard.xlsx) 字节）"

echo "=============================================="
echo "全部步骤完成。"
