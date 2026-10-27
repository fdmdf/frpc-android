/**
 * 运行环境判定。
 *
 * WebView 里没有可靠的 Tauri 平台 API（未装 os 插件），Android 系统 WebView 的
 * UA 一定包含 "Android"，用它区分「安卓端」即可。
 * 布局层面的窄屏适配统一走 CSS 媒体查询，这里只处理需要逻辑分支的地方。
 */
export const isAndroidApp = /Android/i.test(navigator.userAgent);

/**
 * 随 APK 打包的 frpc 版本，需要与 scripts/build-frpc-android.mjs 的
 * FRP_VERSION 默认值保持一致，仅用于界面展示。
 */
export const bundledFrpcVersion = "v0.71.1";

/** 需要按屏幕宽度做逻辑判断时使用（例如只渲染一份列表）。 */
export const isNarrowScreen = () =>
  typeof window !== "undefined" &&
  window.matchMedia("(max-width: 768px)").matches;
