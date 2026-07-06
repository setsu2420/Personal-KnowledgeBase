#!/usr/bin/env bash
# Unified Build Script for 智能情报分析平台
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

OS_TYPE="$(uname -s 2>/dev/null || echo "Windows")"

echo "========================================="
echo "  检测运行环境并启动一键打包程序..."
echo "========================================="

case "$OS_TYPE" in
    Darwin)
        echo "检测到系统环境: macOS"
        bash "$SCRIPT_DIR/build-macos.sh"
        ;;
    Linux)
        echo "检测到系统环境: Linux"
        bash "$SCRIPT_DIR/build-linux.sh"
        ;;
    *Windows*|*MINGW*|*CYGWIN*|*MSYS*)
        echo "检测到系统环境: Windows"
        powershell.exe -ExecutionPolicy Bypass -File "$SCRIPT_DIR/build-windows.ps1"
        ;;
    *)
        # 默认回退 Windows（有些 shell 探测不到 uname）
        if [[ "$OS" == "Windows_NT" ]]; then
            echo "检测到系统环境: Windows (fallback)"
            powershell.exe -ExecutionPolicy Bypass -File "$SCRIPT_DIR/build-windows.ps1"
        else
            echo "[错误] 无法识别当前操作系统类型: $OS_TYPE"
            exit 1
        fi
        ;;
esac
