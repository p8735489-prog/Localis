#!/bin/bash
#
# Localis GitHub Secrets 自动配置脚本
# 前提：已安装 gh CLI 并已认证 (gh auth login)
#
# 用法：
#   chmod +x setup_github_secrets.sh
#   ./setup_github_secrets.sh
#

set -euo pipefail

REPO="p8735489-prog/Localis"
KEYSTORE_FILE="localis-release.jks"

# 验证 gh CLI
if ! command -v gh &>/dev/null; then
    echo "ERROR: gh CLI 未安装"
    echo "  安装指南: https://cli.github.com/"
    echo "  认证命令: gh auth login"
    exit 1
fi

if ! gh auth status &>/dev/null; then
    echo "ERROR: gh CLI 未认证"
    echo "  运行: gh auth login"
    exit 1
fi

# 验证 keystore 文件存在
if [ ! -f "$KEYSTORE_FILE" ]; then
    echo "ERROR: 未找到 $KEYSTORE_FILE"
    echo "  请将 localis-release.jks 放在当前目录下"
    exit 1
fi

echo "=== Localis GitHub Secrets 配置 ==="
echo "仓库: $REPO"
echo "密钥库: $KEYSTORE_FILE"
echo ""

# 生成 Base64
KEYSTORE_B64=$(base64 -w 0 "$KEYSTORE_FILE")
echo "Base64 编码长度: ${#KEYSTORE_B64} 字符"
echo ""

# 设置 Secrets
echo "正在设置 SIGNING_KEYSTORE_B64..."
gh secret set SIGNING_KEYSTORE_B64 --repo "$REPO" --body "$KEYSTORE_B64"

read -r -s -p "Keystore password: " STORE_PASSWORD
echo
printf '%s' "$STORE_PASSWORD" | gh secret set SIGNING_STORE_PASSWORD --repo "$REPO"
unset STORE_PASSWORD

echo "正在设置 SIGNING_KEY_ALIAS..."
gh secret set SIGNING_KEY_ALIAS --repo "$REPO" --body "localis"

read -r -s -p "Key password: " KEY_PASSWORD
echo
printf '%s' "$KEY_PASSWORD" | gh secret set SIGNING_KEY_PASSWORD --repo "$REPO"
unset KEY_PASSWORD

echo ""
echo "=== 配置完成 ==="
echo "已设置以下 Secrets:"
gh secret list --repo "$REPO"
echo ""
echo "下一步：推送 tag 触发构建"
echo "  git tag v2.1.0 && git push origin v2.1.0"
echo "  或在 Actions 页面手动触发 workflow"
