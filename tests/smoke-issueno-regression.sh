#!/usr/bin/env bash
# Phase6 回归冒烟：问题编号软删回退修复 + 问题类型停用负向用例
# 用法：在 23 号服务器本机执行 bash smoke-issueno-regression.sh
set -u
API=http://127.0.0.1:18082

for i in $(seq 1 12); do
  CODE=$(curl -s -o /dev/null -w '%{http_code}' "$API/api/site/config")
  [ "$CODE" = "200" ] && break
  sleep 5
done
echo "HEALTH=$CODE"
sudo docker ps --filter name=issueflow-backend --format '{{.Names}} {{.Status}}'
if [ "$CODE" != "200" ]; then exit 1; fi

TOKEN=$(curl -s -X POST "$API/api/auth/login" -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}' | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
AUTH="Authorization: Bearer $TOKEN"

echo "=== [1] 建 A：期望不复用已软删的 0001 ==="
R1=$(curl -s -X POST "$API/api/issues" -H "$AUTH" -F 'issue={"title":"回归-建删再建-A","typeId":1,"severity":2};type=application/json')
N1=$(echo "$R1" | grep -o '"issueNo":"[^"]*"'); I1=$(echo "$R1" | sed -n 's/.*"id":\([0-9]*\).*/\1/p' | head -1)
echo "A: $N1 (id=$I1)"

echo "=== [2] 软删 A ==="
curl -s -X DELETE "$API/api/issues/$I1" -H "$AUTH" | grep -o '"code":[0-9]*'

echo "=== [3] 再建 B：序号必须继续递增，不得 500 ==="
R2=$(curl -s -X POST "$API/api/issues" -H "$AUTH" -F 'issue={"title":"回归-建删再建-B","typeId":2,"severity":1};type=application/json')
N2=$(echo "$R2" | grep -o '"issueNo":"[^"]*"'); I2=$(echo "$R2" | sed -n 's/.*"id":\([0-9]*\).*/\1/p' | head -1)
echo "B: $N2 (id=$I2) code=$(echo "$R2" | grep -o '"code":[0-9]*')"

echo "=== [4] 清理 B、建删 C 二次确认 ==="
curl -s -X DELETE "$API/api/issues/$I2" -H "$AUTH" | grep -o '"code":[0-9]*'
R3=$(curl -s -X POST "$API/api/issues" -H "$AUTH" -F 'issue={"title":"回归-C","typeId":1,"severity":2};type=application/json')
N3=$(echo "$R3" | grep -o '"issueNo":"[^"]*"'); I3=$(echo "$R3" | sed -n 's/.*"id":\([0-9]*\).*/\1/p' | head -1)
echo "C: $N3 code=$(echo "$R3" | grep -o '"code":[0-9]*')"
curl -s -X DELETE "$API/api/issues/$I3" -H "$AUTH" | grep -o '"code":[0-9]*'

echo "=== [5] 类型停用（JSON body）回归 ==="
NC=$(curl -s -X POST "$API/api/issue-types" -H "$AUTH" -H "Content-Type: application/json" -d '{"name":"回归临时","code":"SMOKE_TMP2","sort":99,"enabled":1}')
NID=$(echo "$NC" | sed -n 's/.*"data":\([0-9]*\).*/\1/p'); echo "type_id=$NID"
curl -s -X PUT "$API/api/issue-types/$NID/status" -H "$AUTH" -H "Content-Type: application/json" -d '{"enabled":false}' | grep -o '"code":[0-9]*'
echo -n "options(expect 6)="; curl -s "$API/api/issue-types/options" -H "$AUTH" | grep -o '"id":' | wc -l
echo -n "list(expect 7)=";   curl -s "$API/api/issue-types" -H "$AUTH" | grep -o '"id":' | wc -l

echo "=== [6] 用停用类型建问题应被业务拒绝（非500系统错误） ==="
curl -s -X POST "$API/api/issues" -H "$AUTH" -F "issue={\"title\":\"停用类型\",\"typeId\":$NID,\"severity\":2};type=application/json" | head -c 180; echo

echo "=== [7] 清理临时类型 ==="
curl -s -X DELETE "$API/api/issue-types/$NID" -H "$AUTH" | grep -o '"code":[0-9]*'
echo -n "final list(expect 6)="; curl -s "$API/api/issue-types" -H "$AUTH" | grep -o '"id":' | wc -l
