#!/usr/bin/env bash
# =========================================================
# 一键构建启动 + 自检脚本
# 使用: bash start.sh [--build]
#   --build 强制重新构建镜像
# =========================================================
set -e
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

BUILD_FLAG=""
if [ "$1" = "--build" ]; then
  BUILD_FLAG="--build"
fi

echo "🚀 正在启动写字楼办公桌椅工位绑定管理系统..."
echo "   工作目录: $PROJECT_DIR"
echo ""

# 端口预检
if [ -f .env ]; then
  set -a; source .env; set +a
  for p_name in FRONTEND_PORT BACKEND_PORT MYSQL_PORT REDIS_PORT; do
    p_val=$(eval echo \$$p_name)
    if [ -n "$p_val" ]; then
      occ=$(lsof -nP -iTCP:$p_val -sTCP:LISTEN 2>/dev/null | tail -n +2)
      if [ -n "$occ" ]; then
        # 排除本项目的容器进程（容器名含 wsm）
        non_self=$(echo "$occ" | grep -v "wsm_")
        if [ -n "$non_self" ]; then
          echo "❌ 端口 $p_val ($p_name) 已被非本项目进程占用："
          echo "$non_self"
          echo ""
          echo "请修改 .env 中的 $p_name 为其他空闲端口后再启动。"
          exit 1
        fi
      fi
    fi
  done
fi

# 启动
if [ -n "$BUILD_FLAG" ]; then
  docker compose up --build -d
else
  docker compose up -d
fi

# 自动调用自检输出
bash "$PROJECT_DIR/print-access.sh"
