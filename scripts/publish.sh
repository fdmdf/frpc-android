#!/usr/bin/env bash
# 一键把项目发布到 GitHub：替换占位符 → 初始化 git → 关联远端 → 推送 → 同步 Wiki
#
# 用法:
#   bash scripts/publish.sh <owner> <repo>            # 已有远端仓库时
#   bash scripts/publish.sh <owner> <repo> --create   # 用 gh CLI 创建仓库（需已登录）
#
# 前置：已完成一次本地提交（git log 有记录）；替换占位符后请勿重复运行不同 owner

set -euo pipefail

OWNER="${1:-}"
REPO="${2:-}"
MODE="${3:-}"

if [ -z "$OWNER" ] || [ -z "$REPO" ]; then
  cat <<'EOF'
用法: bash scripts/publish.sh <owner> <repo> [--create]

  <owner>    GitHub 用户名或组织名，例如 liao
  <repo>     仓库名，例如 frpc-android
  --create   使用 gh CLI 创建仓库（需先 `gh auth login`）

示例:
  bash scripts/publish.sh myname frpc-android --create
EOF
  exit 1
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "==> 1/5 替换文档中的 OWNER/REPO 占位符"
FILES=$(grep -rl "OWNER/REPO" README.md README.en.md .github 2>/dev/null || true)
if [ -n "$FILES" ]; then
  echo "$FILES" | while IFS= read -r f; do
    sed -i.bak "s|OWNER/REPO|${OWNER}/${REPO}|g" "$f" && rm -f "$f.bak"
    echo "    updated: $f"
  done
else
  echo "    未发现占位符（可能已替换过），跳过"
fi

echo "==> 2/5 初始化 / 校验 git"
if [ ! -d .git ]; then
  git init -q
  git branch -M main
fi
git add -A
if ! git diff --cached --quiet; then
  git commit -q -m "chore: prepare open-source release"
  echo "    已提交改动"
fi

echo "==> 3/5 关联远端"
REMOTE="https://github.com/${OWNER}/${REPO}.git"
if git remote get-url origin >/dev/null 2>&1; then
  git remote set-url origin "$REMOTE"
else
  git remote add origin "$REMOTE"
fi
echo "    origin -> $REMOTE"

if [ "$MODE" = "--create" ]; then
  if command -v gh >/dev/null 2>&1; then
    echo "==> 3.1 使用 gh 创建仓库（若已存在则跳过）"
    gh repo create "${OWNER}/${REPO}" --public --source=. --remote=origin --push || true
  else
    echo "    未安装 gh，请先在 GitHub 网页创建仓库"
  fi
fi

echo "==> 4/5 推送代码"
git push -u origin main

echo "==> 5/5 同步 Wiki"
TMP="$(mktemp -d)"
if git clone "https://github.com/${OWNER}/${REPO}.wiki.git" "$TMP" 2>/dev/null; then
  cp docs/wiki/*.md "$TMP"/
  (
    cd "$TMP"
    git add -A
    git commit -q -m "docs: sync wiki from repository" 2>/dev/null || true
    git push
  )
  echo "    Wiki 已同步"
else
  echo "    跳过：Wiki 仓库尚不存在。请在仓库 Settings → Features 勾选 Wiki 后重跑："
  echo "      git clone https://github.com/${OWNER}/${REPO}.wiki.git wiki && cp docs/wiki/*.md wiki/ && cd wiki && git push"
fi
rm -rf "$TMP"

echo ""
echo "完成: https://github.com/${OWNER}/${REPO}"
echo "后续建议:"
echo "  1. 仓库 Settings → 配置 Secrets（KEYSTORE_BASE64 / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD）以启用签名发版"
echo "  2. Actions 页面手动运行一次 Release 工作流验证流水线"
echo "  3. 打第一个版本标签: git tag v$(grep -oE 'versionName = \"[^\"]+\"' androidApp/build.gradle.kts | head -1 | sed 's/.*\"\\(.*\\)\"/\\1/') && git push origin --tags"
