#!/bin/bash
# ============================================================
# 智能情报分析平台 - 交付包构建脚本
# 用法: ./build-delivery.sh
# 产出: intel-platform-delivery.tar.gz
# ============================================================

set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
DELIVERY_NAME="intel-platform-delivery"
DELIVERY_DIR="${PROJECT_DIR}/${DELIVERY_NAME}"
OUTPUT_FILE="${PROJECT_DIR}/${DELIVERY_NAME}.tar.gz"

# 镜像名称
BACKEND_IMAGE="intel-platform-backend:latest"
FRONTEND_IMAGE="intel-platform-frontend:latest"

echo "============================================"
echo "  智能情报分析平台 - 交付包构建"
echo "============================================"
echo ""

# 清理旧的交付目录
if [ -d "${DELIVERY_DIR}" ]; then
    echo "[1/6] 清理旧交付目录..."
    rm -rf "${DELIVERY_DIR}"
else
    echo "[1/6] 准备交付目录..."
fi

# 创建交付目录结构
mkdir -p "${DELIVERY_DIR}"/{images,init-db,config}

# 构建后端镜像
echo "[2/6] 构建后端镜像..."
cd "${PROJECT_DIR}/backend-springboot"
docker build -t "${BACKEND_IMAGE}" -f Dockerfile .
echo "   ✓ 后端镜像构建完成: ${BACKEND_IMAGE}"

# 构建前端镜像
echo "[3/6] 构建前端镜像..."
cd "${PROJECT_DIR}/frontend-vue"
docker build -t "${FRONTEND_IMAGE}" -f Dockerfile .
echo "   ✓ 前端镜像构建完成: ${FRONTEND_IMAGE}"

# 导出镜像为tar文件
echo "[4/6] 导出Docker镜像..."
docker save "${BACKEND_IMAGE}" -o "${DELIVERY_DIR}/images/backend.tar"
docker save "${FRONTEND_IMAGE}" -o "${DELIVERY_DIR}/images/frontend.tar"
echo "   ✓ 镜像已导出到 images/"

# 复制交付文件
echo "[5/6] 复制交付文件..."
cd "${PROJECT_DIR}"

# docker-compose（交付版）
cp docker-compose.yml "${DELIVERY_DIR}/"

# 数据库初始化脚本
if [ -d "init-db" ]; then
    cp -r init-db/* "${DELIVERY_DIR}/init-db/" 2>/dev/null || true
fi

# .env 模板
cat > "${DELIVERY_DIR}/.env.example" << 'ENVEOF'
# 智能情报分析平台 - 环境配置
# 复制此文件为 .env 并修改配置

# MySQL root密码（请修改为安全密码）
MYSQL_ROOT_PASSWORD=intel2026secure

# MySQL数据库名
MYSQL_DATABASE=intelligence_platform

# 后端JVM参数
JAVA_OPTS=-Xms512m -Xmx2g -XX:+UseG1GC
ENVEOF

# 使用说明书
cat > "${DELIVERY_DIR}/README.txt" << 'READMEEOF'
============================================================
  智能情报分析平台 - 部署说明
============================================================

【前置要求】
  - Docker 20.10 或更高版本
  - Docker Compose 2.0 或更高版本
  - 至少 4GB 可用内存
  - 至少 10GB 可用磁盘空间

【快速部署】（推荐）
  chmod +x load-and-run.sh
  ./load-and-run.sh

  部署完成后访问: http://localhost

【手动部署】
  1. 加载镜像:
     docker load -i images/backend.tar
     docker load -i images/frontend.tar

  2. 配置环境:
     cp .env.example .env
     # 按需编辑 .env 文件

  3. 启动服务:
     docker-compose up -d

  4. 查看状态:
     docker-compose ps

【服务端口】
  - 前端界面: http://localhost (端口 80)
  - 后端API:  http://localhost:8080
  - MySQL:    localhost:3306

【常用命令】
  查看日志:   docker-compose logs -f
  停止服务:   docker-compose down
  重启服务:   docker-compose restart
  查看状态:   docker-compose ps

【注意事项】
  - 首次启动需要约1-2分钟初始化数据库
  - 数据存储在Docker卷中，docker-compose down 不会丢失数据
  - 如需彻底清除数据: docker-compose down -v
============================================================
READMEEOF

# 接收方启动脚本
cat > "${DELIVERY_DIR}/load-and-run.sh" << 'LOADEOF'
#!/bin/bash
# ============================================================
# 智能情报分析平台 - 一键加载并启动
# ============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "${SCRIPT_DIR}"

echo "============================================"
echo "  智能情报分析平台 - 一键部署"
echo "============================================"
echo ""

# 检查Docker
if ! command -v docker &> /dev/null; then
    echo "❌ 未检测到Docker，请先安装Docker"
    echo "   下载地址: https://www.docker.com/get-started"
    exit 1
fi

if ! docker compose version &> /dev/null && ! docker-compose version &> /dev/null; then
    echo "❌ 未检测到Docker Compose，请先安装"
    exit 1
fi

# 选择docker compose命令
if docker compose version &> /dev/null; then
    COMPOSE_CMD="docker compose"
else
    COMPOSE_CMD="docker-compose"
fi

# 创建.env文件（如果不存在）
if [ ! -f .env ]; then
    echo "[1/4] 创建环境配置..."
    cp .env.example .env
    echo "   ✓ 已创建 .env 配置文件"
else
    echo "[1/4] 环境配置已存在，跳过"
fi

# 加载Docker镜像
echo "[2/4] 加载Docker镜像..."
if [ -f "images/backend.tar" ]; then
    docker load -i images/backend.tar
    echo "   ✓ 后端镜像加载完成"
else
    echo "   ⚠ 未找到后端镜像文件 images/backend.tar"
fi

if [ -f "images/frontend.tar" ]; then
    docker load -i images/frontend.tar
    echo "   ✓ 前端镜像加载完成"
else
    echo "   ⚠ 未找到前端镜像文件 images/frontend.tar"
fi

# 启动服务
echo "[3/4] 启动服务..."
${COMPOSE_CMD} up -d
echo "   ✓ 所有服务已启动"

# 等待服务就绪
echo "[4/4] 等待服务就绪..."
sleep 5

echo ""
echo "============================================"
echo "  ✅ 部署完成！"
echo "============================================"
echo ""
echo "  访问地址:"
echo "    前端界面: http://localhost"
echo "    后端API:  http://localhost:8080"
echo ""
echo "  常用命令:"
echo "    查看日志: ${COMPOSE_CMD} logs -f"
echo "    停止服务: ${COMPOSE_CMD} down"
echo "    重启服务: ${COMPOSE_CMD} restart"
echo ""
${COMPOSE_CMD} ps
LOADEOF

chmod +x "${DELIVERY_DIR}/load-and-run.sh"

# 打包为tar.gz
echo "[6/6] 打包交付文件..."
cd "${PROJECT_DIR}"
tar -czf "${OUTPUT_FILE}" "${DELIVERY_NAME}/"

# 计算文件大小
FILE_SIZE=$(du -h "${OUTPUT_FILE}" | cut -f1)

echo ""
echo "============================================"
echo "  ✅ 交付包构建完成！"
echo "============================================"
echo ""
echo "  文件: ${OUTPUT_FILE}"
echo "  大小: ${FILE_SIZE}"
echo ""
echo "  交付方式: 将 ${DELIVERY_NAME}.tar.gz 发送给对方"
echo "  对方操作:"
echo "    1. tar -xzf ${DELIVERY_NAME}.tar.gz"
echo "    2. cd ${DELIVERY_NAME}"
echo "    3. ./load-and-run.sh"
echo ""

# 询问是否清理交付目录
read -p "是否清理交付目录（保留tar.gz）？[y/N]: " confirm
if [[ "${confirm}" =~ ^[Yy]$ ]]; then
    rm -rf "${DELIVERY_DIR}"
    echo "   ✓ 交付目录已清理"
fi
