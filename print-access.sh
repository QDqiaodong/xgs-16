#!/usr/bin/env bash
# =========================================================
# 构建完成提示脚本 - 自动打印访问地址并做自检
# 使用：bash print-access.sh
# =========================================================
set -e

# 读取 .env 文件中的端口配置
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${PROJECT_DIR}/.env"

if [ ! -f "$ENV_FILE" ]; then
  echo "❌ 未找到 .env 文件：$ENV_FILE"
  exit 1
fi

# 解析 .env
set -a
source "$ENV_FILE"
set +a

FRONTEND_PORT="${FRONTEND_PORT:-3016}"
BACKEND_PORT="${BACKEND_PORT:-8016}"
MYSQL_PORT="${MYSQL_PORT:-3316}"
REDIS_PORT="${REDIS_PORT:-6316}"

echo ""
echo "================================================================"
echo "  ✅ 写字楼办公桌椅工位绑定管理系统 构建启动成功"
echo "================================================================"
echo ""
echo "🌐 访问地址 (浏览器打开以下任一地址)："
echo ""
echo "   ➜  http://localhost:${FRONTEND_PORT}"
echo "   ➜  http://127.0.0.1:${FRONTEND_PORT}"
echo ""
echo "----------------------------------------------------------------"
echo "📦 服务端口清单 (统一绑定 127.0.0.1)："
echo ""
printf "   %-12s %-10s %-15s\n" "服务" "宿主端口" "容器名"
printf "   %-12s %-10s %-15s\n" "------------" "----------" "---------------"
printf "   %-12s %-10s %-15s\n" "前端 Nginx"  "${FRONTEND_PORT}"  "wsm_frontend"
printf "   %-12s %-10s %-15s\n" "后端 Spring" "${BACKEND_PORT}"   "wsm_backend"
printf "   %-12s %-10s %-15s\n" "MySQL"       "${MYSQL_PORT}"     "wsm_mysql"
printf "   %-12s %-10s %-15s\n" "Redis"       "${REDIS_PORT}"     "wsm_redis"
echo "----------------------------------------------------------------"
echo "🔌 后端 API 文档: http://localhost:${BACKEND_PORT}/api/furniture/statistics"
echo ""
echo "🩺 自检中..."
echo ""

# 自检 IPv4 与 localhost 一致性
sleep 2
IPV4_HTML=$(curl -sS -m 5 http://127.0.0.1:${FRONTEND_PORT} 2>/dev/null | head -c 300 || echo "FAIL")
LOCAL_HTML=$(curl -sS -m 5 http://localhost:${FRONTEND_PORT} 2>/dev/null | head -c 300 || echo "FAIL")

if [ "$IPV4_HTML" = "FAIL" ]; then
  echo "⚠️  前端 (127.0.0.1:${FRONTEND_PORT}) 未响应，请稍候再试或查看日志：docker compose logs -f frontend"
else
  if [ "$IPV4_HTML" = "$LOCAL_HTML" ]; then
    echo "✅ 自检通过：127.0.0.1 与 localhost 返回的页面内容完全一致"
  else
    echo "❌ 自检失败：127.0.0.1 与 localhost 返回的页面不一致，请检查"
    echo "  - 127.0.0.1 返回: $(echo "$IPV4_HTML" | head -c 80)"
    echo "  - localhost  返回: $(echo "$LOCAL_HTML" | head -c 80)"
  fi
fi

# 检查后端
BACKEND_RESP=$(curl -sS -m 5 "http://127.0.0.1:${BACKEND_PORT}/api/furniture/floors" 2>/dev/null || echo "FAIL")
if [[ "$BACKEND_RESP" == *"\"code\":200"* ]]; then
  echo "✅ 后端接口正常：${BACKEND_PORT}/api/furniture/floors 返回成功"
else
  echo "⚠️  后端接口未就绪，可能仍在启动中。查看日志：docker compose logs -f backend"
fi

echo ""
echo "📋 常用命令："
echo "   查看日志:   docker compose logs -f"
echo "   重启服务:   docker compose restart"
echo "   停止服务:   docker compose down"
echo "   重新构建:   docker compose up --build -d"
echo "================================================================"
echo ""
