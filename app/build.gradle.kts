import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

// Load dev token from local.properties (not committed)
val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localPropsFile.inputStream().use { input -> localProps.load(input) }
}
val simsDevAuthToken: String = (localProps.getProperty("SIMS_DEV_AUTH_TOKEN") ?: "").trim()

android {
    namespace = "com.simsapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.simsapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Release 不注入敏感信息
            buildConfigField("String", "DEV_AUTH_TOKEN", "\"\"")
            // 为 UAT/开发场景提供可安装的签名：使用调试签名（debug keystore）
            // 注意：仅用于联调与内测，不应用于正式发版
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            // 仅调试构建注入本地令牌（来自 local.properties）
            buildConfigField("String", "DEV_AUTH_TOKEN", "\"$simsDevAuthToken\"")
        }
    }
    compileOptions {
        // Ensure Java 17 language level
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        // Keep bytecode target to 17
        jvmTarget = "17"
    }
    // Configure Java toolchain to decouple from system JAVA_HOME
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Lint 配置：UAT/Release 构建不因 lint 错误失败，且避免触发 Lint Vital Analyze 的 OOM
    lint {
        // 关闭 release 构建的 lint 检查（会跳过 lintVital* 任务）
        checkReleaseBuilds = false
        // 即使存在 lint 问题也不终止构建
        abortOnError = false
    }

    /**
     * 文件级注释：为并存安装添加产品风味。
     * 作者：SIMS Android 团队
     * 说明：新增 uat 风味，使用不同的 applicationId 与应用名，支持与正式版并行安装。
     */
    // 定义风味维度
    flavorDimensions += "env"
    // 定义产品风味：prod（默认正式版）、uat（联调/UAT）
    productFlavors {
        create("prod") {
            dimension = "env"
            // 使用 defaultConfig 的 applicationId 与默认资源即可
            // 注入正式环境 BASE_URL 到 BuildConfig，用于 DI 中的 Retrofit
            buildConfigField("String", "BASE_URL", "\"https://sims.ink-stone.win/zuul/sims-master/\"")
            // 覆盖应用名称（桌面显示），Manifest 使用 @string/app_name
            resValue("string", "app_name", "SIMS")
        }
        create("uat") {
            dimension = "env"
            // 通过后缀生成唯一包名，避免与正式版冲突
            applicationIdSuffix = ".uat"
            // 版本名添加后缀，便于识别
            versionNameSuffix = "-uat"
            // 覆盖应用名称（桌面显示），Manifest 使用 @string/app_name
            resValue("string", "app_name", "SIMS-uat")
            // 注入 UAT 环境 BASE_URL 到 BuildConfig，用于 DI 中的 Retrofit
            buildConfigField("String", "BASE_URL", "\"https://sims-uat.ink-stone.win/zuul/sims-master/\"")
        }
        // 新增 dev 风味：用于开发联调
        create("dev") {
            dimension = "env"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            // 覆盖应用名称（桌面显示），Manifest 使用 @string/app_name
            resValue("string", "app_name", "SIMS-dev")
            // 注入 DEV 环境 BASE_URL；如需调整，可在后续共识文档中更新
            buildConfigField("String", "BASE_URL", "\"https://sims.ink-stone.win/zuul/sims-master/\"")
        }
    }
}

// Configure Kotlin toolchain explicitly
kotlin {
    jvmToolchain(17)
}

// Configure KSP arguments for Room schema generation
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.expandProjection", "true")
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.text)

    // ViewModel and Navigation
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Retrofit + OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // AndroidX Hilt - WorkManager integration
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // WorkManager
    implementation(libs.work.runtime.ktx)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Coil image loading
    implementation(libs.coil.compose)

    // CameraX for QR code scanning
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")
    
    // ML Kit for barcode scanning
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    
    // Permission handling
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")

    // 移除 ComposeReorderable 库依赖
    // implementation("org.burnoutcrew.composereorderable:reorderable:0.9.6")

    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito:mockito-android:5.8.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}