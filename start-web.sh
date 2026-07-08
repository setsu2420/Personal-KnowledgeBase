#!/bin/bash
# Web 模式启动脚本
# 同时启动 Spring Boot 后端 (8080) 和 Vue 前端 (5173)

set -e

echo "======================================"
echo "  智能情报分析平台 - Web 模式启动"
echo "======================================"

# 检查 MySQL 服务
echo ""
echo "[1/4] 检查 MySQL 服务..."
if ! mysqladmin ping -h localhost --silent 2>/dev/null; then
    echo "  ⚠️  MySQL 未运行，尝试启动..."
    brew services start mysql 2>/dev/null || {
        echo "  ❌ MySQL 启动失败，请手动启动："
        echo "     brew services start mysql"
        exit 1
    }
    sleep 3
fi
echo "  ✅ MySQL 服务运行中"

# 检查数据库是否存在
echo ""
echo "[2/4] 检查数据库..."
if ! mysql -u root -e "USE intelligence_platform" 2>/dev/null; then
    echo "  ⚠️  数据库不存在，创建中..."
    mysql -u root -e "CREATE DATABASE intelligence_platform CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    echo "  ✅ 数据库已创建"
else
    echo "  ✅ 数据库已存在"
fi

# 启动后端
echo ""
echo "[3/4] 启动 Spring Boot 后端..."
cd backend-springboot
./mvnw spring-boot:run > /tmp/backend.log 2>&1 &
BACKEND_PID=$!
cd ..
echo "  ✅ 后端已启动 (PID: $BACKEND_PID)"
echo "     日志: /tmp/backend.log"

# 等待后端就绪
echo "  等待后端就绪..."
for i in {1..30}; do
    if curl -s http://localhost:8080/api/health > /dev/null 2>&1; then
        echo "  ✅ 后端已就绪"
        break
    fi
    sleep 1
done

# 启动前端
echo ""
echo "[4/4] 启动 Vue 前端..."
cd frontend-vue
npm run dev > /tmp/frontend.log 2>&1 &
FRONTEND_PID=$!
cd ..
echo "  ✅ 前端已启动 (PID: $FRONTEND_PID)"
echo "     日志: /tmp/frontend.log"

# 显示访问信息
echo ""
echo "======================================"
echo "  ✅ 启动完成！"
echo "======================================"
echo ""
echo "  🌐 前端地址: http://localhost:5173"
echo "  🔧 后端地址: http://localhost:8080"
echo "  📊 健康检查: http://localhost:8080/api/health"
echo ""
echo "  后端 PID: $BACKEND_PID"
echo "  前端 PID: $FRONTEND_PID"
echo ""
echo "  停止服务:"
echo "    kill $BACKEND_PID  # 停止后端"
echo "    kill $FRONTEND_PID  # 停止前端"
echo ""
echo "  按 Ctrl+C 停止所有服务..."
echo "======================================"

# 等待用户中断
trap 'echo ""; echo "正在停止服务..."; kill $BACKEND_PID $FRONTEND_PID 2>/dev/null; echo "已停止"; exit 0' INT

# 保持脚本运行
wait
