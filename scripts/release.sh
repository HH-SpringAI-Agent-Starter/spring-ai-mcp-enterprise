#!/bin/bash
# ===========================================
# MCP Enterprise — Maven Central 发布脚本
# ===========================================
#
# 使用方法：
#   ./scripts/release.sh          # 交互式发布
#   ./scripts/release.sh patch    # 自动 patch 版本发布
#   ./scripts/release.sh minor    # 自动 minor 版本发布
#
# 前提条件：
#   1. 注册 Sonatype: https://central.sonatype.com/
#   2. 创建命名空间 com.mcp.enterprise
#   3. 设置环境变量:
#      - SONATYPE_USERNAME
#      - SONATYPE_PASSWORD
#      - GPG_KEY_NAME
#      - GPG_PASSPHRASE
#      - GITHUB_TOKEN (备用)
#   4. 生成 GPG 密钥并上传到 keyserver
# ===========================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_DIR"

echo "========================================"
echo "  MCP Enterprise - Maven Central 发布"
echo "========================================"
echo ""

# ---- 检查环境变量 ----
check_env() {
    local missing=0
    for var in "SONATYPE_USERNAME" "SONATYPE_PASSWORD" "GPG_KEY_NAME" "GPG_PASSPHRASE"; do
        if [ -z "${!var}" ]; then
            echo "❌ 缺少环境变量: $var"
            missing=1
        fi
    done
    if [ "$missing" -eq 1 ]; then
        echo ""
        echo "请设置以下环境变量:"
        echo "  export SONATYPE_USERNAME=<Sonatype 用户名>"
        echo "  export SONATYPE_PASSWORD=<Sonatype 密码/Token>"
        echo "  export GPG_KEY_NAME=<GPG 密钥名称>"
        echo "  export GPG_PASSPHRASE=<GPG 密码>"
        exit 1
    fi
    echo "✅ 环境变量检查通过"
}

# ---- 版本号处理 ----
get_current_version() {
    grep '<version>' pom.xml | head -1 | sed 's/.*<version>\(.*\)<\/version>.*/\1/' | sed 's/-SNAPSHOT//'
}

bump_version() {
    local version="$1"
    local type="$2"
    
    IFS='.' read -r major minor patch <<< "$version"
    
    case "$type" in
        patch)
            patch=$((patch + 1))
            ;;
        minor)
            minor=$((minor + 1))
            patch=0
            ;;
        major)
            major=$((major + 1))
            minor=0
            patch=0
            ;;
        *)
            echo "$version"
            return
            ;;
    esac
    
    echo "$major.$minor.$patch"
}

# ---- 检查未提交变更 ----
check_clean() {
    if [ -n "$(git status --porcelain)" ]; then
        echo "❌ 工作区有未提交的变更，请先提交或 stash"
        echo ""
        git status --short
        exit 1
    fi
    echo "✅ Git 工作区干净"
}

# ---- 发布流程 ----
do_release() {
    local version="$1"
    local tag="v$version"
    
    echo ""
    echo "📦 发布版本: $version"
    echo "   标签: $tag"
    echo ""
    
    # 确认
    read -p "继续发布？(y/N) " confirm
    if [ "$confirm" != "y" ] && [ "$confirm" != "Y" ]; then
        echo "取消发布"
        exit 0
    fi
    
    echo ""
    echo "🔢 步骤 1/5: 更新版本号 (SNAPSHOT -> RELEASE)"
    mvn versions:set -DnewVersion="$version" -q
    git add pom.xml */pom.xml
    git commit -m "Release v$version: 版本号更新"
    
    echo ""
    echo "🏷️  步骤 2/5: 创建 Git 标签"
    git tag -a "$tag" -m "Release v$version"
    
    echo ""
    echo "🔨 步骤 3/5: 编译并测试"
    mvn clean test -q
    echo "✅ 测试通过"
    
    echo ""
    echo "📤 步骤 4/5: 发布到 Maven Central"
    mvn clean deploy -P release -DskipTests \
        -Dgpg.keyname="$GPG_KEY_NAME" \
        -Dgpg.passphrase="$GPG_PASSPHRASE"
    echo "✅ Maven Central 发布成功"
    
    echo ""
    echo "🔖 步骤 5/5: 推送到 GitHub"
    git push origin main --tags
    echo "✅ GitHub 推送成功"
    
    echo ""
    echo "🎉 发布完成: $version"
    echo "   GitHub Release: https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise/releases/tag/$tag"
    echo "   Maven Central: https://central.sonatype.com/artifact/com.mcp.enterprise/mcp-core/$version"
    
    # 恢复 SNAPSHOT 版本
    local next_version=$(bump_version "$version" "patch")
    echo ""
    echo "🔄 切换到下一个快照版本: $next_version-SNAPSHOT"
    mvn versions:set -DnewVersion="${next_version}-SNAPSHOT" -q
    git add pom.xml */pom.xml
    git commit -m "Prepare for next development iteration: $next_version-SNAPSHOT"
    git push origin main
}

# ---- 主流程 ----
if [ $# -eq 0 ]; then
    check_env
    check_clean
    
    current=$(get_current_version)
    echo "当前版本: $current"
    echo ""
    echo "选择发布类型:"
    echo "  1) patch ($(bump_version "$current" "patch")) [默认]"
    echo "  2) minor ($(bump_version "$current" "minor"))"
    echo "  3) major ($(bump_version "$current" "major"))"
    echo "  4) 手动输入发布版本"
    echo ""
    read -p "请选择 (1-4, 默认 1): " choice
    
    case "$choice" in
        2) new_version=$(bump_version "$current" "minor") ;;
        3) new_version=$(bump_version "$current" "major") ;;
        4) read -p "输入版本号: " new_version ;;
        *) new_version=$(bump_version "$current" "patch") ;;
    esac
    
    do_release "$new_version"
elif [ "$1" = "patch" ] || [ "$1" = "minor" ] || [ "$1" = "major" ]; then
    check_env
    check_clean
    
    current=$(get_current_version)
    new_version=$(bump_version "$current" "$1")
    do_release "$new_version"
else
    # 直接指定版本号
    check_env
    check_clean
    do_release "$1"
fi
