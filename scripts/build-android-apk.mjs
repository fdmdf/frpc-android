// 打包 Android APK。
//
// 绕过 `tauri android build` 的原因：Windows 上它会创建符号链接，需要开发者模式。
// 这里手动走完链路，注意两点不能漏：
//   1. cargo 必须带 --features custom-protocol，否则 WebView 会去连 devUrl
//      （localhost:3344）而不是加载随包编译进去的前端资源；
//   2. Rust 产物要先 strip，否则 debug 库 200MB+，打进 APK 会爆炸。
//
// 用法：node scripts/build-android-apk.mjs [aarch64-linux-android]
import { spawnSync } from "node:child_process";
import { existsSync, mkdirSync, copyFileSync, readdirSync } from "node:fs";
import { homedir, platform } from "node:os";
import { join, resolve } from "node:path";

const ABI_DIR = {
  "aarch64-linux-android": "arm64-v8a",
  "armv7-linux-androideabi": "armeabi-v7a",
  "x86_64-linux-android": "x86_64",
  "i686-linux-android": "x86"
};

const target = process.argv[2] || "aarch64-linux-android";
const abi = ABI_DIR[target];
if (!abi) {
  console.error(`不支持的 target: ${target}\n可选: ${Object.keys(ABI_DIR).join(", ")}`);
  process.exit(1);
}

const repoRoot = resolve(import.meta.dirname, "..");
const tauriDir = join(repoRoot, "src-tauri");
const androidDir = join(tauriDir, "gen", "android");

const sdk =
  process.env.ANDROID_HOME || join(homedir(), "AppData", "Local", "Android", "Sdk");
const ndkHome = process.env.NDK_HOME || findNdk(sdk);
if (!ndkHome) {
  console.error("找不到 NDK，请设置 NDK_HOME 或 ANDROID_HOME");
  process.exit(1);
}

const host = {
  win32: "windows-x86_64",
  darwin: "darwin-x86_64",
  linux: "linux-x86_64"
}[platform()];
const ndkBin = join(ndkHome, "toolchains", "llvm", "prebuilt", host, "bin");
const exe = platform() === "win32" ? ".exe" : "";
const cmd = platform() === "win32" ? ".cmd" : "";
const apiLevel = process.env.ANDROID_API_LEVEL || "24";

function run(cmd, args, opts = {}) {
  console.log(`\n> ${cmd} ${args.join(" ")}`);
  const r = spawnSync(cmd, args, {
    stdio: "inherit",
    shell: platform() === "win32",
    cwd: opts.cwd || repoRoot,
    env: opts.env || process.env
  });
  if (r.status !== 0) process.exit(r.status ?? 1);
}

function findNdk(sdk) {
  const dir = join(sdk, "ndk");
  if (!existsSync(dir)) return undefined;
  const versions = readdirSync(dir).sort().reverse();
  return versions.length ? join(dir, versions[0]) : undefined;
}

function proxyArgs() {
  const raw = process.env.HTTPS_PROXY || process.env.https_proxy;
  if (!raw) return [];
  const url = new URL(raw);
  return [
    `-Dhttp.proxyHost=${url.hostname}`,
    `-Dhttp.proxyPort=${url.port || "80"}`,
    `-Dhttps.proxyHost=${url.hostname}`,
    `-Dhttps.proxyPort=${url.port || "443"}`
  ];
}

// 1. 编译 Rust（必须带 custom-protocol）
const env = {
  ...process.env,
  ANDROID_HOME: sdk,
  NDK_HOME: ndkHome,
  CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER: join(
    ndkBin,
    `aarch64-linux-android${apiLevel}-clang${cmd}`
  ),
  CC_aarch64_linux_android: join(ndkBin, `aarch64-linux-android${apiLevel}-clang${cmd}`),
  AR_aarch64_linux_android: join(ndkBin, `llvm-ar${exe}`),
  JAVA_HOME:
    process.env.JAVA_HOME ||
    join("C:", "Program Files", "Microsoft", "jdk-17.0.20.101-hotspot")
};

run(
  "cargo",
  [
    "build",
    "--features",
    "custom-protocol",
    "--target",
    target,
    ...(process.argv.includes("--release") ? ["--release"] : [])
  ],
  { cwd: tauriDir, env }
);

// 2. strip 后放进 jniLibs
const profile = process.argv.includes("--release") ? "release" : "debug";
const built = join(tauriDir, "target", target, profile, "libfrpc_android_lib.so");
const jniDir = join(androidDir, "app", "src", "main", "jniLibs", abi);
mkdirSync(jniDir, { recursive: true });
const stripped = join(jniDir, "libfrpc_android_lib.so");
copyFileSync(built, stripped);
run(join(ndkBin, `llvm-strip${platform() === "win32" ? ".exe" : ""}`), [
  "--strip-unneeded",
  stripped
]);

// 3. Gradle 打包（跳过它自带的 rust 任务，用我们上面编好的产物）
// Gradle 的 ABI 任务后缀：arm64-v8a -> Arm64，armeabi-v7a -> Arm，x86_64 -> X86_64
const TASK_ABI = {
  "arm64-v8a": "Arm64",
  "armeabi-v7a": "Arm",
  x86: "X86",
  "x86_64": "X86_64"
};
const apkAbi = abi.replace("-v8a", "");
const taskAbi = TASK_ABI[abi];
const variant = profile === "release" ? "Release" : "Debug";
// Linux/macOS 上 gradlew 可能没有可执行位（Windows 上 clone/拷贝过来的），
// 用 bash 显式调用比依赖 exec bit 稳。
const gradleCmd = platform() === "win32" ? "gradlew.bat" : "bash";
const gradlePrefix = platform() === "win32" ? [] : ["./gradlew"];
run(
  gradleCmd,
  [
    ...gradlePrefix,
    `assemble${taskAbi}${variant}`,
    "--no-daemon",
    "--no-configuration-cache",
    ...["Arm", "Arm64", "X86", "X86_64"].flatMap((a) => ["-x", `rustBuild${a}${variant}`]),
    ...proxyArgs()
  ],
  { cwd: androidDir, env }
);

console.log(
  `\n✅ 产物: ${join(
    androidDir,
    "app",
    "build",
    "outputs",
    "apk",
    apkAbi,
    profile,
    `app-${apkAbi}-${profile}.apk`
  )}`
);
console.log("提示：打包前记得先跑 npm run build，前端资源是编译期嵌进 Rust 库的。");
console.log(
  profile === "release"
    ? "正式签名需设置 KEYSTORE_PATH / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD，未设置则产出未签名包。"
    : ""
);
