#!/bin/bash

##############################################################################
# FOTA 版本号更新脚本
#
# 用法:
#   ./scripts/update-version.sh <version> [options]
#
# 参数:
#   version        目标版本号 (如: 0.2.0, 1.0.0, 0.2.0-SNAPSHOT)
#
# 选项:
#   --no-backend   跳过后端版本更新
#   --no-frontend  跳过前端版本更新
#   --no-maven     不使用 Maven 插件，直接修改文件
#
# 示例:
#   ./scripts/update-version.sh 0.2.0
#   ./scripts/update-version.sh 0.2.0-SNAPSHOT
#   ./scripts/update-version.sh 1.0.0 --no-frontend
#
##############################################################################

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 项目根目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# 默认选项
UPDATE_BACKEND=true
UPDATE_FRONTEND=true
USE_MAVEN=true

# 解析参数
VERSION=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --no-backend)
            UPDATE_BACKEND=false
            shift
            ;;
        --no-frontend)
            UPDATE_FRONTEND=false
            shift
            ;;
        --no-maven)
            USE_MAVEN=false
            shift
            ;;
        -*)
            echo -e "${RED}错误: 未知选项 $1${NC}"
            echo "用法: $0 <version> [--no-backend] [--no-frontend] [--no-maven]"
            exit 1
            ;;
        *)
            if [[ -z "$VERSION" ]]; then
                VERSION="$1"
            fi
            shift
            ;;
    esac
done

# 验证版本号
if [[ -z "$VERSION" ]]; then
    echo -e "${RED}错误: 请提供版本号${NC}"
    echo "用法: $0 <version> [--no-backend] [--no-frontend] [--no-maven]"
    exit 1
fi

# 验证语义化版本格式 (允许带 -SNAPSHOT 等后缀)
if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?$ ]]; then
    echo -e "${RED}错误: 版本号格式无效，应为语义化版本格式 (如: 1.0.0, 0.2.0-SNAPSHOT)${NC}"
    exit 1
fi

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  FOTA 版本更新工具${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "目标版本: ${GREEN}${VERSION}${NC}"

echo ""
echo -e "${BLUE}----------------------------------------${NC}"
echo -e "准备更新以下内容:"
echo -e "${BLUE}----------------------------------------${NC}"

FILES_TO_UPDATE=()

if [[ "$UPDATE_BACKEND" == true ]]; then
    if [[ "$USE_MAVEN" == true ]]; then
        echo -e "  ${GREEN}✓${NC} 后端 (Maven 多模块)"
    else
        FILES_TO_UPDATE+=("$PROJECT_ROOT/pom.xml")
        echo -e "  ${GREEN}✓${NC} pom.xml"
    fi
fi

if [[ "$UPDATE_FRONTEND" == true ]]; then
    FILES_TO_UPDATE+=("$PROJECT_ROOT/fota-ui/package.json")
    echo -e "  ${GREEN}✓${NC} fota-ui/package.json"
fi

echo ""
echo -e "${YELLOW}即将更新版本号${NC}"
read -p "确认继续? (y/N) " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}已取消${NC}"
    exit 0
fi

echo ""
echo -e "${BLUE}----------------------------------------${NC}"
echo -e "开始更新..."
echo -e "${BLUE}----------------------------------------${NC}"

# 更新后端版本
if [[ "$UPDATE_BACKEND" == true ]]; then
    if [[ "$USE_MAVEN" == true ]]; then
        echo -e "使用 ${GREEN}Maven Versions Plugin${NC} 更新后端..."
        cd "$PROJECT_ROOT"
        mvn versions:set -DnewVersion="${VERSION}" -q
        mvn versions:commit -q
        echo -e "  ${GREEN}✓${NC} 后端版本已更新为: ${VERSION}"
    else
        echo -e "更新 ${GREEN}pom.xml${NC}..."
        if [[ "$OSTYPE" == "darwin"* ]]; then
            sed -i '' "s|<version>[^<]*</version>|<version>${VERSION}</version>|g" "$PROJECT_ROOT/pom.xml"
        else
            sed -i "s|<version>[^<]*</version>|<version>${VERSION}</version>|g" "$PROJECT_ROOT/pom.xml"
        fi
        echo -e "  ${GREEN}✓${NC} 后端版本已更新为: ${VERSION}"
    fi
fi

# 更新前端版本
if [[ "$UPDATE_FRONTEND" == true ]]; then
    echo -e "更新 ${GREEN}fota-ui/package.json${NC}..."

    if [[ "$OSTYPE" == "darwin"* ]]; then
        sed -i '' "s|\"version\": \"[^\"]*\"|\"version\": \"${VERSION}\"|g" "$PROJECT_ROOT/fota-ui/package.json"
    else
        sed -i "s|\"version\": \"[^\"]*\"|\"version\": \"${VERSION}\"|g" "$PROJECT_ROOT/fota-ui/package.json"
    fi

    echo -e "  ${GREEN}✓${NC} 前端版本已更新为: ${VERSION}"
fi

echo ""
echo -e "${BLUE}----------------------------------------${NC}"
echo -e "${GREEN}版本更新完成!${NC}"
echo -e "${BLUE}----------------------------------------${NC}"
echo ""
echo -e "下一步操作建议:"
echo -e "  1. 检查修改: ${YELLOW}git diff${NC}"
echo -e "  2. 提交代码: ${YELLOW}git commit -m \"chore: bump version to ${VERSION}\"${NC}"
echo -e "  3. 更新 docs/06-releases/CHANGELOG.md"
echo ""

# 返回原目录
cd "$SCRIPT_DIR/.."
