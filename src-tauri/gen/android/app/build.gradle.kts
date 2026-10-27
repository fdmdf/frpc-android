import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("rust")
}

val tauriProperties = Properties().apply {
    val propFile = file("tauri.properties")
    if (propFile.exists()) {
        propFile.inputStream().use { load(it) }
    }
}

android {
    compileSdk = 36
    namespace = "priv.liujiawei.frpc.desktop"
    defaultConfig {
        manifestPlaceholders["usesCleartextTraffic"] = "false"
        applicationId = "priv.liujiawei.frpc.desktop"
        minSdk = 24
        targetSdk = 36
        versionCode = tauriProperties.getProperty("tauri.android.versionCode", "1").toInt()
        versionName = tauriProperties.getProperty("tauri.android.versionName", "1.0")
    }
    // 正式签名：配置环境变量后自动启用，未配置时 release 产物保持未签名。
    //   KEYSTORE_PATH     jks/keystore 文件路径
    //   KEYSTORE_PASSWORD store 密码
    //   KEY_ALIAS         key 别名
    //   KEY_PASSWORD      key 密码
    signingConfigs {
        create("release") {
            val ksPath = System.getenv("KEYSTORE_PATH")
            if (!ksPath.isNullOrBlank() && file(ksPath).exists()) {
                storeFile = file(ksPath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("debug") {
            manifestPlaceholders["usesCleartextTraffic"] = "true"
            isDebuggable = true
            isJniDebuggable = true
            isMinifyEnabled = false
            packaging {
                jniLibs.keepDebugSymbols.add("*/arm64-v8a/*.so")
                jniLibs.keepDebugSymbols.add("*/armeabi-v7a/*.so")
                jniLibs.keepDebugSymbols.add("*/x86/*.so")
                jniLibs.keepDebugSymbols.add("*/x86_64/*.so")
            }
        }
        getByName("release") {
            optimization {
               enable = true
            }
            val ksPath = System.getenv("KEYSTORE_PATH")
            if (!ksPath.isNullOrBlank() && file(ksPath).exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            // 必须显式关闭：R8 会重命名 Kotlin 插件类，而 Rust 侧靠 Class.forName 反射加载，
            // 混淆后启动时找不到 FrpcServicePlugin 会直接崩溃。
            isMinifyEnabled = false
            isShrinkResources = false
            // 注意：保持 isMinifyEnabled = false。
            // FrpcServicePlugin 等 Kotlin 插件类由 Rust 侧通过 Class.forName 反射加载，
            // 一旦开启代码混淆，类名会被重命名导致启动时找不到类而崩溃。
            proguardFiles(
                *fileTree(".") {
                  include("**/*.pro")
                  exclude("build/**")
                }.files.toTypedArray()
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    // 随包分发的 frpc（libfrpc.so）必须在安装时解压到 nativeLibraryDir，
    // Android 只允许从该目录执行 ELF 文件，不能从 app data 目录 execve。
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    buildFeatures {
        buildConfig = true
    }
}

// `tauri android build` 会把前端产物拷到 app/src/main/assets；直接跑 gradle 时没人做这步。
// 这里自动补上，作为 Rust 侧嵌入资源之外的兜底。
val copyFrontendAssets = tasks.register<Copy>("copyFrontendAssets") {
    // app 模块目录（gen/android/app）往上四级即仓库根目录
    from(file("../../../../dist"))
    into(layout.projectDirectory.dir("src/main/assets"))
}
tasks.named("preBuild") { dependsOn(copyFrontendAssets) }

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}

rust {
    rootDirRel = "../../../"
}

dependencies {
    implementation("androidx.webkit:webkit:1.14.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-process:2.10.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.4")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.0")
}

apply(from = file("tauri.build.gradle.kts"))
