#!/usr/bin/env bash
# ===========================================================================
# 用途：本地一键构建前后端（不依赖 Docker）
# 执行方式：bash scripts/build.sh
# 所需参数：无；可选环境变量 SKIP_FRONTEND=1 跳过前端构建
# 前置依赖：JDK 17+、Maven 3.8+、Node 18+
# ===========================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "==> 构建后端 (Maven) ..."
( cd "$ROOT/src/backend" && mvn -B clean package -DskipTests )

if [ "${SKIP_FRONTEND:-0}" != "1" ]; then
  echo "==> 构建前端 (Vite) ..."
  ( cd "$ROOT/src/frontend" && npm install && npm run build )
fi

echo "==> 构建完成。后端 jar: src/backend/target/issueflow-backend.jar；前端产物: src/frontend/dist/"
