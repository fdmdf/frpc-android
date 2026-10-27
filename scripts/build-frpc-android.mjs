#!/usr/bin/env node
/**
 * 交叉编译 frpc，产出 Android 可执行的引擎文件。
 *
 * 产物写入 src-tauri/gen/android/app/src/main/jniLibs/<abi>/libfrpc.so。
 * 必须以 lib*.so 命名并放在 jniLibs 下：安装时系统会把它解压到
 * nativeLibraryDir，那是 Android 上少数允许 execve 的目录（Android 10+ W^X）。
 *
 * 用法：
 *   node scripts/build-frpc-android.mjs                 # 默认 arm64 + armv7 + x86_64
 *   FRP_VERSION=v0.71.1 node scripts/build-frpc-android.mjs
 *   ABIS=arm64-v8a node scripts/build-frpc-android.mjs  # 只编一个架构
 *   FRP_WEB=0 node scripts/build-frpc-android.mjs       # 不编 frpc 自带仪表盘（noweb）
 */

import { execFileSync, spawnSync } from "node:child_process";
import { createWriteStream, existsSync, mkdirSync, copyFileSync, rmSync } from "node:fs";
import { mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const FRP_VERSION = process.env.FRP_VERSION || "v0.71.1";
const GOPROXY = process.env.GOPROXY || "https://goproxy.cn,direct";
// frp 0.62+ 的 frpc 内置了一个 Web 仪表盘，静态资源不进源码包、也不在 git 里，
// 需要本地 npm 构建；构建不了时退回 -tags noweb（仪表盘不可用，其他功能照常）。
const WANT_WEB = process.env.FRP_WEB !== "0";
const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const JNI_LIBS = join(ROOT, "src-tauri/gen/android/app/src/main/jniLibs");

// Go 只支持 android/arm64 的纯静态链接；arm32 与 x86_64 用 linux 目标，
// 二者同为 ELF + 相同系统调用，真机/模拟器均可运行。
const TARGETS = {
  "arm64-v8a": { goos: "android", goarch: "arm64" },
  "armeabi-v7a": { goos: "linux", goarch: "arm", goarm: "7" },
  "x86_64": { goos: "linux", goarch: "amd64" }
};

const selected = (process.env.ABIS || Object.keys(TARGETS).join(","))
  .split(",")
  .map(s => s.trim())
  .filter(Boolean);

function run(cmd, args, env = {}, cwd = ROOT, quiet = false) {
  execFileSync(cmd, args, {
    cwd,
    // Windows 的 npm.cmd / .bat 必须经 shell 才能执行
    shell: /\.(cmd|bat)$/i.test(cmd),
    stdio: quiet ? "pipe" : "inherit",
    env: { ...process.env, ...env }
  });
}

// Windows 上 npm 是 npm.cmd，spawnSync 不带 shell 解析不了 .cmd
const NPM = process.platform === "win32" ? "npm.cmd" : "npm";

/** 命令是否可用（用于挑选解压方式） */
function hasCommand(cmd) {
  const probe = process.platform === "win32" ? "where" : "which";
  const res = spawnSync(probe, [cmd], { stdio: "ignore" });
  return res.status === 0;
}

/**
 * 解压源码压缩包。
 * 优先用 unzip（Git Bash / macOS / Linux 自带）；没有 unzip 的 Windows 退回
 * PowerShell 的 Expand-Archive。
 */
function unzip(zipPath, dest) {
  if (hasCommand("unzip")) {
    run("unzip", ["-q", "-o", zipPath, "-d", dest]);
    return;
  }

  if (process.platform === "win32") {
    run("powershell", [
      "-NoProfile",
      "-NonInteractive",
      "-Command",
      `Expand-Archive -Force -LiteralPath '${zipPath}' -DestinationPath '${dest}'`
    ]);
    return;
  }

  throw new Error("未找到 unzip，请先安装 unzip 后重试");
}

/**
 * 构建 frpc 自带的 Web 仪表盘（web/frpc/dist）。
 * 返回是否成功；失败时调用方改用 -tags noweb。
 */
function buildWebDashboard(moduleDir) {
  if (!WANT_WEB) return false;

  const webDir = join(moduleDir, "web", "frpc");
  if (!existsSync(join(webDir, "package.json"))) return false;
  if (!hasCommand(NPM)) {
    console.log("==> 未找到 npm，跳过 frpc 内置仪表盘（改用 noweb）");
    return false;
  }

  console.log("==> 构建 frpc 内置仪表盘 (web/frpc)");
  try {
    run(NPM, ["install", "--no-audit", "--no-fund"], {}, webDir, true);
    run(NPM, ["run", "build-only"], {}, webDir, true);
  } catch (err) {
    console.log(`    ! 仪表盘构建失败，改用 noweb：${err.message}`);
    return false;
  }

  return existsSync(join(webDir, "dist", "index.html"));
}

async function download(url, dest) {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`download failed: ${res.status} ${url}`);
  const file = createWriteStream(dest);
  for await (const chunk of res.body) file.write(chunk);
  await new Promise((ok, fail) => file.end(err => (err ? fail(err) : ok())));
}

async function main() {
  // CI 里 jniLibs 是空的（so 不入库），直接建出来，不要因为目录缺失就报错。
  if (!existsSync(JNI_LIBS)) {
    mkdirSync(JNI_LIBS, { recursive: true });
  }

  const work = mkdtempSync(join(tmpdir(), "frpc-android-"));
  const zipPath = join(work, "frp.zip");
  const srcRoot = join(work, "src");
  const moduleDir = join(srcRoot, "github.com", "fatedier", `frp@${FRP_VERSION}`);

  console.log(`==> frp ${FRP_VERSION}`);
  console.log("==> 下载源码 (goproxy 模块压缩包)");
  mkdirSync(srcRoot, { recursive: true });
  await download(`${GOPROXY.split(",")[0]}/github.com/fatedier/frp/@v/${FRP_VERSION}.zip`, zipPath);
  unzip(zipPath, srcRoot);
  if (!existsSync(join(moduleDir, "go.mod"))) {
    throw new Error(`解压后未找到源码（${moduleDir}），请检查网络或 FRP_VERSION 是否正确。`);
  }

  const goTags = buildWebDashboard(moduleDir) ? [] : ["-tags", "noweb"];

  for (const abi of selected) {
    const target = TARGETS[abi];
    if (!target) {
      throw new Error(`未知 ABI: ${abi}（可选 ${Object.keys(TARGETS).join(", ")}）`);
    }
    const outDir = join(JNI_LIBS, abi);
    mkdirSync(outDir, { recursive: true });
    const outFile = join(outDir, "libfrpc.so");
    console.log(`==> 编译 ${abi} (GOOS=${target.goos} GOARCH=${target.goarch})`);
    run(
      "go",
      ["build", ...goTags, "-trimpath", "-ldflags", "-s -w", "-o", outFile, "./cmd/frpc"],
      {
        GOOS: target.goos,
        GOARCH: target.goarch,
        GOARM: target.goarm || "",
        CGO_ENABLED: "0",
        GOFLAGS: "-mod=mod",
        GOPROXY
      },
      moduleDir
    );
    console.log(`    -> ${outFile}`);
  }

  rmSync(work, { recursive: true, force: true });
  console.log("完成。记得同步更新 src/utils/platform.ts 中的 bundledFrpcVersion。");
}

main().catch(err => {
  console.error(err.message || err);
  process.exit(1);
});
