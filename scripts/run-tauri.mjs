import { spawn } from "node:child_process";
import { existsSync, readdirSync } from "node:fs";
import { homedir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const cargoBin = join(homedir(), ".cargo", "bin");
const sep = process.platform === "win32" ? ";" : ":";
const env = {
  ...process.env,
  PATH: `${cargoBin}${sep}${process.env.PATH || ""}`
};

const args = process.argv.slice(2);

// Android 端的 frpc 引擎是随包的 jniLibs/libfrpc.so，缺失时应用能装上但无法连接，
// 这里提前拦一下，避免构建出「看起来正常」的 APK。
if (args[0] === "android" && (args[1] === "build" || args[1] === "dev")) {
  const jniLibs = join(root, "src-tauri/gen/android/app/src/main/jniLibs");
  const ready =
    existsSync(jniLibs) &&
    readdirSync(jniLibs).some(abi => existsSync(join(jniLibs, abi, "libfrpc.so")));
  if (!ready) {
    console.error(
      "\n[android] 缺少内置 frpc 引擎：src-tauri/gen/android/app/src/main/jniLibs/<abi>/libfrpc.so\n" +
        "          请先执行 `npm run android:frpc`（需要 Go 1.21+）。\n"
    );
    process.exit(1);
  }
}

const cmd = process.platform === "win32" ? "npx.cmd" : "npx";

const child = spawn(cmd, ["tauri", ...args], {
  stdio: "inherit",
  env,
  shell: process.platform === "win32"
});

child.on("exit", code => {
  process.exit(code ?? 0);
});
