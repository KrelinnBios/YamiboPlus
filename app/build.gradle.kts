plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun buildValue(name: String, defaultValue: String? = null): String? {
    val value = providers.gradleProperty(name)
        .orElse(providers.environmentVariable(name))
        .orNull
        ?.trim()
        ?.takeIf(String::isNotEmpty)
    return value ?: defaultValue
}

val appUpdateUrl = buildValue(
    "APP_UPDATE_URL",
    "https://api.github.com/repos/KrelinnBios/YamiboPlus/releases?per_page=20"
)!!
val appVersionName = buildValue("APP_VERSION_NAME", "1.0.0")!!
val appVersionCode = buildValue("APP_VERSION_CODE", "3")!!.toInt()
val signingKeystorePath = buildValue("ANDROID_KEYSTORE_PATH")
val signingKeyAlias = buildValue("ANDROID_KEY_ALIAS")
val signingKeyPassword = buildValue("ANDROID_KEY_PASSWORD")
val signingStorePassword = buildValue("ANDROID_STORE_PASSWORD")
val hasStableSigning = listOf(
    signingKeystorePath,
    signingKeyAlias,
    signingKeyPassword,
    signingStorePassword
).all { !it.isNullOrBlank() }

android {
    namespace = "org.shirakawatyu.yamibo.novel"
    compileSdk = 36

    signingConfigs {
        if (hasStableSigning) {
            create("stable") {
                storeFile = file(signingKeystorePath!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.krelinnbios.yamiboplus"
        minSdk = 24
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField(
            "String",
            "APP_UPDATE_URL",
            "\"${appUpdateUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        ndk {
            abiFilters.add("arm64-v8a")
            abiFilters.add("armeabi-v7a")
        }
    }

    buildTypes {
        debug {
            // 有正式签名材料时用 stable，否则回退到 AGP 自带的 debug 签名，
            // 保证 debug 包始终被签名、可安装；不会因 stable 缺失而被置 null 变成未签名。
            signingConfig = signingConfigs.findByName("stable")
                ?: signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            // release 只用正式签名；stable 缺失时保持 null（CI 会先校验 secrets，本地无密钥则产出未签名包）。
            signingConfig = signingConfigs.findByName("stable")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

}

// 固定 APK 名。AGP 8.13 的公开 VariantOutput 未暴露 outputFileName，故需 cast 到 impl。
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            (output as? com.android.build.api.variant.impl.VariantOutputImpl)
                ?.outputFileName?.set("300-Plus.apk")
        }
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.13.0")

    implementation(platform("androidx.compose:compose-bom:2026.06.00"))

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.runtime:runtime-livedata")
    implementation("androidx.compose.runtime:runtime-rxjava2")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.animation:animation-core")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")

    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("org.jsoup:jsoup:1.21.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-brotli:4.12.0")
    implementation("com.alibaba.fastjson2:fastjson2:2.0.61.android5")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil:2.7.0")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("me.saket.telephoto:zoomable-image-coil:0.6.2")
    implementation("com.github.qichuan:android-opencc:1.2.0")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
