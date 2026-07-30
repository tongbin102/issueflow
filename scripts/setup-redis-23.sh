#!/usr/bin/env bash
#
# setup-redis-23.sh — 在 23 号服务器起 issueFlow 专用 Redis 容器
#
# 【真实拓扑】Redis 由本脚本用 sudo docker run 起独立 redis 容器（镜像 redis:7-alpine，
#   独立数据卷，与 23 上的 frontend/backend 应用同机）。
#   backend 通过 REDIS_HOST=10.55.3.23 访问本容器（deploy-23.sh 已默认设为该值）。
#
# 【执行方式】 bash setup-redis-23.sh
# 【可调参数】 REDIS_PORT（默认 6379，若被占用自动 +1）
#
set -euo pipefail
REDIS_PORT="${REDIS_PORT:-6379}"
is_free() { ! (sudo ss -tlnp 2>/dev/null | awk '{print $4}' | grep -qE "[:.]${1}\$"); }
while ! is_free "$REDIS_PORT"; do REDIS_PORT=$((REDIS_PORT+1)); done
sudo docker run -d --name issueflow-redis --restart unless-stopped \
  -p "$REDIS_PORT:6379" \
  -v issueflow-redis-data:/data \
  redis:7-alpine redis-server --appendonly yes
echo "✅ Redis 已启动，对外端口 $REDIS_PORT（容器内 6379），地址 10.55.3.23"
echo "   在 deploy-23.sh 中 REDIS_HOST 默认已设为 10.55.3.23（若非 6379 需同步 REDIS_PORT）"
