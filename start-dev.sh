#!/usr/bin/env bash
# 智能情报分析平台 - 一键启动开发服务器
# 同时启动：Spring Boot 后端 (8080) + Parser-Service (8100) + Vue3 前端 (5173)

trap 'kill $(jobs -p) 2>/dev/null || true' EXIT INT TERM

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKEND_DIR="$SCRIPT_DIR/backend-springboot"
FRONTEND_DIR="$SCRIPT_DIR/frontend-vue"
PARSER_DIR="$SCRIPT_DIR/parser-service"

# 加载 .env
if [ -f "$SCRIPT_DIR/.env" ]; then
    echo "正在加载 .env 配置..."
    set -a
    source "$SCRIPT_DIR/.env"
    set +a
    echo ".env 配置已加载（LLM_PROVIDER=${LLM_PROVIDER:-siliconflow}）"
fi

echo "========================================="
echo "  正在启动 智能情报分析平台..."
echo "========================================="

# 1. Spring Boot 后端 (8080)
echo "[1/3] 启动 Spring Boot 后端..."
cd "$BACKEND_DIR"
./mvnw spring-boot:run > springboot.log 2>&1 &
BACKEND_PID=$!

echo "等待后端就绪 (端口: 8080)..."
for i in {1..60}; do
    if lsof -i :8080 >/dev/null 2>&1; then
        echo "后端已启动 (PID: $BACKEND_PID)"
        break
    fi
    if ! kill -0 $BACKEND_PID 2>/dev/null; then
        echo "[错误] 后端启动失败: $BACKEND_DIR/springboot.log"
        exit 1
    fi
    sleep 1
done

# 2. Parser-Service (8100) - PaddleOCR-VL 本地 OCR 后备
echo "[2/3] 启动 Parser-Service (本地OCR后备)..."
cd "$PARSER_DIR"
if command -v python3 >/dev/null 2>&1; then
    python3 parse_document.py --port 8100 > parser.log 2>&1 &
    PARSER_PID=$!
    sleep 3
    if lsof -i :8100 >/dev/null 2>&1; then
        echo "Parser-Service 已启动 (PID: $PARSER_PID, 端口: 8100)"
    else
        echo "[警告] Parser-Service 启动缓慢，本地OCR后备可能不可用"
    fi
else
    echo "[警告] 未找到 python3，跳过 Parser-Service"
fi

# 3. Vue 前端 (5173)
echo "[3/3] 启动 Vue 前端..."
cd "$FRONTEND_DIR"

echo ""
echo "========================================="
echo "  服务启动完毕："
echo "  前端 (Vue):       http://localhost:5173"
echo "  后端 (Spring):    http://localhost:8080"
echo "  Parser (OCR后备): http://localhost:8100"
echo "  后端日志:         $BACKEND_DIR/springboot.log"
echo "  Parser日志:       $PARSER_DIR/parser.log"
echo "========================================="
echo ""

npm run dev
