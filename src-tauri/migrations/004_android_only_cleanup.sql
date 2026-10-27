-- 004_android_only_cleanup.sql
-- 桌面端遗留：frpc 版本管理（在线下载/导入本地版本）已从产品中移除。
-- Android 端 frpc 由 APK 内置（jniLibs/<abi>/libfrpc.so），不再需要记录下载版本，
-- 也不需要「自定义 frpc 路径」——Android 10+ 只允许用户从 nativeLibraryDir 执行 ELF。
DROP TABLE IF EXISTS t_frpcd_versions;
ALTER TABLE t_frpcd_servers DROP COLUMN frpc_version;
ALTER TABLE t_frpcd_servers DROP COLUMN custom_frpc_path;
