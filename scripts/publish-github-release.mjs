#!/usr/bin/env node
/**
 * 将本地构建产物发布到 GitHub（含私有仓库）Release。
 *
 * 用法：
 *   GITHUB_TOKEN=ghp_xxx GITHUB_REPO=owner/repo \
 *     node scripts/publish-github-release.mjs --tag v2.0.0 \
 *       --file path/to/app-arm64-release.apk \
 *       [--name "自定义标题"] [--notes-file notes.md] [--draft] [--prerelease]
 *
 * 环境变量：
 *   GITHUB_TOKEN   必填，需具备 repo 权限（私有仓库还要 contents: write）
 *   GITHUB_REPO    必填，形如 owner/repo
 *   GITHUB_API     可选，自建 GitHub Enterprise 时填 https://ghe.example.com/api/v3
 *
 * 说明：token 只从环境变量读取，绝不写入仓库文件。
 */
import { readFileSync, statSync } from "node:fs";
import { basename, resolve } from "node:path";

const args = process.argv.slice(2);
function take(flag) {
  const i = args.indexOf(flag);
  if (i === -1) return null;
  return args[i + 1] ?? null;
}
const has = (flag) => args.includes(flag);

const token = process.env.GITHUB_TOKEN || process.env.GH_TOKEN;
const repo = process.env.GITHUB_REPO;
const apiBase = (process.env.GITHUB_API || "https://api.github.com").replace(/\/$/, "");
const uploadsBase = apiBase.replace("https://api.github.com", "https://uploads.github.com");

const tag = take("--tag") || process.env.RELEASE_TAG;

function collect(flag) {
  const out = [];
  for (let i = 0; i < args.length; i++) {
    if (args[i] === flag && args[i + 1]) out.push(args[i + 1]);
  }
  return out;
}
const assets = collect("--file").concat(collect("--asset")).map((p) => resolve(p));

if (!token || !repo) {
  console.error("缺少 GITHUB_TOKEN 或 GITHUB_REPO 环境变量");
  process.exit(1);
}
if (!tag) {
  console.error("缺少 --tag，例如 --tag v2.0.0");
  process.exit(1);
}
if (assets.length === 0) {
  console.error("至少要用 --file 指定一个待上传文件");
  process.exit(1);
}
for (const f of assets) {
  try {
    statSync(f);
  } catch {
    console.error(`文件不存在: ${f}`);
    process.exit(1);
  }
}

const headers = {
  Authorization: `Bearer ${token}`,
  Accept: "application/vnd.github+json",
  "X-GitHub-Api-Version": "2022-11-28",
  "User-Agent": "frpc-android-release-script"
};

async function gh(path, init = {}) {
  const res = await fetch(`${apiBase}${path}`, {
    ...init,
    headers: { ...headers, ...(init.headers || {}) }
  });
  const text = await res.text();
  let body = text;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    /* 保持字符串 */
  }
  if (!res.ok) {
    const msg = body && body.message ? body.message : text;
    throw new Error(`GitHub API ${res.status} ${init.method || "GET"} ${path}: ${msg}`);
  }
  return body;
}

const defaultNotes = `## Android

首个 Android 版本，基于 Tauri Mobile，frpc 引擎随包分发（arm64）。

- 内置 frpc 二进制，无需 root，无需额外下载
- 前台服务保活，切后台不掉线
- 移动端界面：底部导航、单列表单、安全区适配
- 自动重连：连接断开后按退避策略重试

> arm64 单架构包，调试/未签名包仅供验证。`;

const notesFile = take("--notes-file");
const notes = notesFile ? readFileSync(resolve(notesFile), "utf8") : defaultNotes;

// 1. 找默认分支，作为 tag 的落点
const repoInfo = await gh(`/repos/${repo}`);
const defaultBranch = repoInfo.default_branch;
console.log(`仓库: ${repoInfo.full_name} (私有: ${repoInfo.private})，默认分支: ${defaultBranch}`);

// 2. 已存在同名 release 则复用，否则新建
let release = null;
try {
  release = await gh(`/repos/${repo}/releases/tags/${encodeURIComponent(tag)}`);
  console.log(`复用已存在 Release #${release.id}`);
} catch {
  release = await gh(`/repos/${repo}/releases`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      tag_name: tag,
      name: take("--name") || tag,
      body: notes,
      draft: has("--draft"),
      prerelease: has("--prerelease"),
      target_commitish: defaultBranch
    })
  });
  console.log(`已创建 Release #${release.id}`);
}

// 3. 上传产物（同名先删，便于重复执行）
const existing = new Map((release.assets || []).map((a) => [a.name, a.id]));
for (const file of assets) {
  const name = take("--rename") && assets.length === 1 ? take("--rename") : basename(file);
  if (existing.has(name)) {
    await gh(`/repos/${repo}/releases/assets/${existing.get(name)}`, { method: "DELETE" });
    console.log(`  已删除同名旧产物: ${name}`);
  }
  const buf = readFileSync(file);
  const ext = name.split(".").pop().toLowerCase();
  const contentType =
    ext === "apk"
      ? "application/vnd.android.package-archive"
      : ext === "zip"
        ? "application/zip"
        : ext === "json"
          ? "application/json"
          : "application/octet-stream";

  const res = await fetch(
    `${uploadsBase}/repos/${repo}/releases/${release.id}/assets?name=${encodeURIComponent(name)}`,
    {
      method: "POST",
      headers: { ...headers, "Content-Type": contentType },
      body: buf
    }
  );
  const text = await res.text();
  if (!res.ok) {
    throw new Error(`上传失败 ${name}: ${res.status} ${text}`);
  }
  const size = (statSync(file).size / 1024 / 1024).toFixed(1);
  console.log(`  已上传 ${name} (${size} MB)`);
}

console.log(`\n发布完成: ${release.html_url}`);
