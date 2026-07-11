#!/bin/bash
# ============================================================
# 智能情报分析平台 - 创建交付压缩包
# 将完整项目打包为可交付的tar.gz文件
# ============================================================

set -e

PROJECT_DIR="/Users/xiaotianxue/Desktop/個人/软件所/范_副本"
DELIVERY_NAME="intel-platform-delivery"
DELIVERY_DIR="${PROJECT_DIR}/${DELIVERY_NAME}"
OUTPUT_FILE="${PROJECT_DIR}/${DELIVERY_NAME}.tar.gz"

echo "============================================"
echo "  智能情报分析平台 - 创建交付包"
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
mkdir -p "${DELIVERY_DIR}"/{backend-springboot,frontend-vue,init-db,config}

# 复制后端代码
echo "[2/6] 复制后端代码..."
cp -r "${PROJECT_DIR}/backend-springboot/src" "${DELIVERY_DIR}/backend-springboot/"
cp -r "${PROJECT_DIR}/backend-springboot/pom.xml" "${DELIVERY_DIR}/backend-springboot/"
cp -r "${PROJECT_DIR}/backend-springboot/Dockerfile" "${DELIVERY_DIR}/backend-springboot/"
cp -r "${PROJECT_DIR}/backend-springboot/mvnw" "${DELIVERY_DIR}/backend-springboot/" 2>/dev/null || true
cp -r "${PROJECT_DIR}/backend-springboot/mvnw.cmd" "${DELIVERY_DIR}/backend-springboot/" 2>/dev/null || true
cp -r "${PROJECT_DIR}/backend-springboot/.mvn" "${DELIVERY_DIR}/backend-springboot/" 2>/dev/null || true
echo "   ✓ 后端代码复制完成"

# 复制前端代码
echo "[3/6] 复制前端代码..."
cp -r "${PROJECT_DIR}/frontend-vue/src" "${DELIVERY_DIR}/frontend-vue/"
cp -r "${PROJECT_DIR}/frontend-vue/package.json" "${DELIVERY_DIR}/frontend-vue/"
cp -r "${PROJECT_DIR}/frontend-vue/package-lock.json" "${DELIVERY_DIR}/frontend-vue/" 2>/dev/null || true
cp -r "${PROJECT_DIR}/frontend-vue/vite.config.ts" "${DELIVERY_DIR}/frontend-vue/" 2>/dev/null || true
cp -r "${PROJECT_DIR}/frontend-vue/tsconfig.json" "${DELIVERY_DIR}/frontend-vue/" 2>/dev/null || true
cp -r "${PROJECT_DIR}/frontend-vue/index.html" "${DELIVERY_DIR}/frontend-vue/" 2>/dev/null || true
cp -r "${PROJECT_DIR}/frontend-vue/Dockerfile" "${DELIVERY_DIR}/frontend-vue/"
cp -r "${PROJECT_DIR}/frontend-vue/nginx.conf" "${DELIVERY_DIR}/frontend-vue/"
echo "   ✓ 前端代码复制完成"

# 复制数据库初始化脚本
echo "[4/6] 复制数据库初始化脚本..."
cp -r "${PROJECT_DIR}/init-db/"* "${DELIVERY_DIR}/init-db/" 2>/dev/null || true
echo "   ✓ 数据库脚本复制完成"

# 复制配置文件
echo "[5/6] 复制配置文件..."
cp "${PROJECT_DIR}/docker-compose.yml" "${DELIVERY_DIR}/"
cp "${PROJECT_DIR}/.dockerignore" "${DELIVERY_DIR}/"
cp "${PROJECT_DIR}/.env.example" "${DELIVERY_DIR}/" 2>/dev/null || true
cp "${PROJECT_DIR}/load-and-run.sh" "${DELIVERY_DIR}/"
cp "${PROJECT_DIR}/DELIVERY-GUIDE.md" "${DELIVERY_DIR}/README.md" 2>/dev/null || true

# 创建.env.example（如果不存在）
if [ ! -f "${DELIVERY_DIR}/.env.example" ]; then
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
fi

chmod +x "${DELIVERY_DIR}/load-and-run.sh"
echo "   ✓ 配置文件复制完成"

# 打包为tar.gz
echo "[6/6] 打包交付文件..."
cd "${PROJECT_DIR}"
tar -czf "${OUTPUT_FILE}" "${DELIVERY_NAME}/"

# 计算文件大小
FILE_SIZE=$(du -h "${OUTPUT_FILE}" | cut -f1)

echo ""
echo "============================================"
echo "  ✅ 交付包创建完成！"
echo "============================================"
echo ""
echo "  文件: ${OUTPUT_FILE}"
echo "  大小: ${FILE_SIZE}"
echo ""
echo "  交付方式:"
echo "    1. 将 ${DELIVERY_NAME}.tar.gz 发送给对方"
echo "    2. 对方解压: tar -xzf ${DELIVERY_NAME}.tar.gz"
echo "    3. 对方进入: cd ${DELIVERY_NAME}"
echo "    4. 对方运行: ./load-and-run.sh"
echo ""
echo "  接收方要求:"
echo "    - Docker 20.10+ 和 Docker Compose 2.0+"
echo "    - 至少 4GB 内存"
echo "    - 首次构建需要 5-10 分钟"
echo ""

# 询问是否清理交付目录
read -p "是否清理交付目录（保留tar.gz）？[y/N]: " confirm
if [[ "${confirm}" =~ ^[Yy]$ ]]; then
    rm -rf "${DELIVERY_DIR}"
    echo "   ✓ 交付目录已清理"
fi

echo ""
echo "完成！交付文件位于:"
echo "  ${OUTPUT_FILE}"
echo ""
