#!/usr/bin/env bash
# ===========================================================================
# 用途：基于 Docker Compose 一键部署（构建并启动全部服务：mysql/redis/backend/frontend）
# 执行方式：bash scripts/deploy.sh [up|down|logs|ps]
# 所需参数：$1 动作，默认 up
# 环境变量：可复制 .env.example 为 .env 后修改（MYSQL_ROOT_PASSWORD / JWT_SECRET 等）
# 前置依赖：Docker 与 Docker Compose v2
# ===========================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ACTION="${1:-up}"
cd "$ROOT"

case "$ACTION" in
  up)    docker compose up -d --build ;;
  down)  docker compose down ;;
  logs)  docker compose logs -f ;;
  ps)    docker compose ps ;;
  *)     echo "未知动作: $ACTION （支持 up|down|logs|ps）"; exit 1 ;;
esac
