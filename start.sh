#!/usr/bin/env bash
# ============================================================
#  智能情报分析平台 - 统一启动脚本 v3.0
# ============================================================
#  用法:
#    ./start.sh web     后端 + 前端 (默认)
#    ./start.sh dev     后端 + 解析服务 + 前端
#    ./start.sh prod    仅后端
# ============================================================
set -e

# ---- 颜色定义 ----
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# ---- 工具函数 ----
print_success() { echo -e "${GREEN}[✓]${NC} $1"; }
print_error()   { echo -e "${RED}[✗]${NC} $1"; }
print_warning() { echo -e "${YELLOW}[!]${NC} $1"; }
print_info()    { echo -e "${BLUE}[i]${NC} $1"; }

# ---- 配置 ----
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKEND_DIR="$SCRIPT_DIR/backend-springboot"
FRONTEND_DIR="$SCRIPT_DIR/frontend-vue"
PARSER_DIR="$SCRIPT_DIR/parser-service"
BACKEND_JAR="$BACKEND_DIR/target/backend.jar"
BACKEND_PORT=8080

# 全局 PID 数组
PIDS=()

# ---- 参数解析 ----
MODE="${1:-web}"
case "$MODE" in
    web|dev|prod) ;;
    *)
        echo "用法: $0 {web|dev|prod}"
        echo "  web   - 后端 + 前端"
        echo "  dev   - 后端 + 解析服务(提示) + 前端"
        echo "  prod  - 仅后端"
        exit 1
        ;;
esac

# ---- 依赖检查 ----
check_dependencies() {
    print_info "正在检查运行环境..."

    local has_error=0

    # Java 21+ (Spring Boot 4.1 要求)
    if command -v java &>/dev/null; then
        local java_ver
        java_ver=$(java -version 2>&1 | head -1 | sed 's/.*"\(.*\)".*/\1/')
        local major
        major=$(echo "$java_ver" | cut -d. -f1)
        if [ -z "$major" ] || [ "$major" = "1" ]; then
            # "1.8.0" 格式 -> 取第二个数字
            major=$(echo "$java_ver" | cut -d. -f2)
        fi
        if [ -n "$major" ] && [ "$major" -ge 21 ]; then
            print_success "Java: $java_ver"
        else
            print_error "Java 版本过低或无法识别: 需要 21+, 当前为 ${java_ver:-未知}"
            has_error=1
        fi
    else
        print_error "未检测到 Java 环境，请安装 JDK 21+"
        has_error=1
    fi

    # Node 18+
    if [ "$MODE" != "prod" ]; then
        if command -v node &>/dev/null; then
            local node_ver
            node_ver=$(node --version | tr -d 'v')
            local major
            major=$(echo "$node_ver" | cut -d. -f1)
            if [ "$major" -ge 18 ]; then
                print_success "Node.js: v$node_ver"
            else
                print_error "Node.js 版本过低: 需要 18+, 当前为 v$node_ver"
                has_error=1
            fi
        else
            print_error "未检测到 Node.js 环境，请安装 Node.js 18+"
            has_error=1
        fi
    fi

    # MySQL 运行中
    if command -v mysqladmin &>/dev/null; then
        if mysqladmin ping -h localhost --silent 2>/dev/null; then
            print_success "MySQL: 运行中"
        else
            print_warning "MySQL 未运行，尝试启动..."
            if command -v brew &>/dev/null; then
                brew services start mysql 2>/dev/null && sleep 3
                if mysqladmin ping -h localhost --silent 2>/dev/null; then
                    print_success "MySQL 已启动"
                else
                    print_error "MySQL 启动失败，请手动启动: brew services start mysql"
                    has_error=1
                fi
            else
                print_error "MySQL 未运行，请手动启动"
                has_error=1
            fi
        fi
    else
        print_warning "未找到 mysqladmin，无法检测 MySQL 状态（跳过）"
    fi

    if [ "$has_error" -eq 1 ]; then
        echo ""
        print_error "环境检查未通过，请修复上述问题后重试"
        exit 1
    fi

    echo ""
}

# ---- 加载 .env ----
load_env() {
    if [ -f "$SCRIPT_DIR/.env" ]; then
        print_info "正在加载 .env 配置..."
        set -a
        source "$SCRIPT_DIR/.env"
        set +a
        print_info "LLM_PROVIDER=${LLM_PROVIDER:-未设置}"
    else
        print_warning "未找到 .env 文件（可执行 'cp .env.example .env' 创建）"
    fi
    echo ""
}

# ---- 启动图谱计算服务 ----
start_kg_compute() {
    print_info "启动 图谱计算服务 (kg-compute on 8101)..."
    cd "$SCRIPT_DIR/kg-compute"
    if [ ! -f "target/release/kg-compute" ]; then
        if command -v cargo &>/dev/null; then
            print_info "正在编译 kg-compute Rust 项目..."
            cargo build --release -q
        else
            print_warning "未检测到 cargo 命令，无法编译 kg-compute。图谱社区计算将降级运行。"
            cd "$SCRIPT_DIR"
            return 0
        fi
    fi
    if [ -f "target/release/kg-compute" ]; then
        ./target/release/kg-compute > /tmp/kg-compute.log 2>&1 &
        KG_COMPUTE_PID=$!
        PIDS+=($KG_COMPUTE_PID)
        print_success "图谱计算服务已启动 (PID: $KG_COMPUTE_PID)"
    else
        print_warning "图谱计算服务编译失败，降级运行。"
    fi
    cd "$SCRIPT_DIR"
}

# ---- 启动后端 ----
start_backend() {
    print_info "启动 Spring Boot 后端 (端口: $BACKEND_PORT)..."

    cd "$BACKEND_DIR"

    if [ -f "$BACKEND_JAR" ]; then
        java -jar "$BACKEND_JAR" > /tmp/backend.log 2>&1 &
        BACKEND_PID=$!
        print_info "后端 PID: $BACKEND_PID (使用已构建 jar)"
    elif [ -f "./mvnw" ]; then
        print_info "未检测到已构建的 backend.jar，正在使用 mvnw 打包..."
        ./mvnw package -DskipTests -q
        if [ -f "$BACKEND_JAR" ]; then
            java -jar "$BACKEND_JAR" > /tmp/backend.log 2>&1 &
            BACKEND_PID=$!
            print_success "后端构建成功并启动 (PID: $BACKEND_PID)"
        else
            print_warning "打包失败，尝试通过 mvnw 直接运行..."
            ./mvnw spring-boot:run -q > /tmp/backend.log 2>&1 &
            BACKEND_PID=$!
            print_info "后端 PID: $BACKEND_PID (使用 mvnw)"
        fi
    else
        print_error "未找到后端构建产物 (target/backend.jar) 或 mvnw"
        exit 1
    fi

    PIDS+=($BACKEND_PID)
    cd "$SCRIPT_DIR"

    # 等待后端就绪
    print_info "等待后端就绪..."
    for i in $(seq 1 60); do
        if curl -s http://localhost:$BACKEND_PORT/api/health > /dev/null 2>&1; then
            print_success "后端已就绪 (端口 $BACKEND_PORT)"
            return 0
        fi
        # 如果进程已死，提前退出
        if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
            print_error "后端进程意外退出，请检查日志: /tmp/backend.log"
            tail -30 /tmp/backend.log
            exit 1
        fi
        sleep 1
    done

    print_error "后端启动超时，请检查日志: /tmp/backend.log"
    exit 1
}

# ---- 启动前端 ----
start_frontend() {
    print_info "启动 Vue 前端..."
    cd "$FRONTEND_DIR"
    if [ ! -d "node_modules" ]; then
        print_warning "未找到 node_modules，正在安装前端依赖 (npm install)..."
        npm install
    fi
    npm run dev -- --host > /tmp/frontend.log 2>&1 &
    FRONTEND_PID=$!
    PIDS+=($FRONTEND_PID)
    cd "$SCRIPT_DIR"
    print_success "前端已启动 (PID: $FRONTEND_PID)"
}

# ---- Parser 提示 ----
note_parser() {
    print_info "解析服务 (Parser-Service): 需要时请手动启动"
    print_info "  路径: $PARSER_DIR"
    print_info "  启动: cd parser-service && python3 parse_document.py --port 8100"
    echo ""
}

# ---- 清理函数 ----
cleanup() {
    echo ""
    print_warning "正在停止所有服务..."
    for pid in "${PIDS[@]}"; do
        kill "$pid" 2>/dev/null && print_info "已停止 PID: $pid"
    done
    print_success "所有服务已停止"
    exit 0
}

trap cleanup INT TERM

# ============================================================
#  主流程
# ============================================================

START_TIME=$(date +%s)

clear
echo -e "${BLUE}=========================================${NC}"
echo -e "${BLUE}  智能情报分析平台 v3.0${NC}"
echo -e "${BLUE}  模式: ${GREEN}$MODE${NC}"
echo -e "${BLUE}=========================================${NC}"
echo ""

check_dependencies
load_env

# 按模式启动
case "$MODE" in
    web)
        start_kg_compute
        echo ""
        start_backend
        echo ""
        start_frontend
        ;;
    dev)
        start_kg_compute
        echo ""
        start_backend
        echo ""
        note_parser
        start_frontend
        ;;
    prod)
        start_kg_compute
        echo ""
        start_backend
        ;;
esac

# 计算耗时
END_TIME=$(date +%s)
ELAPSED=$((END_TIME - START_TIME))

# ---- 启动摘要 ----
echo ""
echo -e "${GREEN}=========================================${NC}"
echo -e "${GREEN}  启动完成！ 耗时: ${ELAPSED}s${NC}"
echo -e "${GREEN}=========================================${NC}"
echo ""
case "$MODE" in
    web)
        echo -e "  ${GREEN}◆${NC} 前端 (Vue):      http://localhost:5173"
        echo -e "  ${GREEN}◆${NC} 后端 (Spring):   http://localhost:$BACKEND_PORT"
        echo -e "  ${GREEN}◆${NC} 图谱计算服务:    http://localhost:8101"
        echo -e "  ${GREEN}◆${NC} 健康检查:        http://localhost:$BACKEND_PORT/api/health"
        ;;
    dev)
        echo -e "  ${GREEN}◆${NC} 前端 (Vue):      http://localhost:5173"
        echo -e "  ${GREEN}◆${NC} 后端 (Spring):   http://localhost:$BACKEND_PORT"
        echo -e "  ${GREEN}◆${NC} 图谱计算服务:    http://localhost:8101"
        echo -e "  ${GREEN}◆${NC} 解析服务(手动):  http://localhost:8100"
        echo -e "  ${GREEN}◆${NC} 健康检查:        http://localhost:$BACKEND_PORT/api/health"
        ;;
    prod)
        echo -e "  ${GREEN}◆${NC} 后端 (Spring):   http://localhost:$BACKEND_PORT"
        echo -e "  ${GREEN}◆${NC} 图谱计算服务:    http://localhost:8101"
        echo -e "  ${GREEN}◆${NC} 健康检查:        http://localhost:$BACKEND_PORT/api/health"
        ;;
esac
echo ""
echo -e "  ${YELLOW}日志${NC}: /tmp/backend.log  /tmp/frontend.log  /tmp/kg-compute.log"
echo -e "  ${YELLOW}按 Ctrl+C 停止所有服务${NC}"
echo ""

wait
