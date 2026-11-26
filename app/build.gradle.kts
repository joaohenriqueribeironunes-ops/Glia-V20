import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.glia.assistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.glia.assistant"
        minSdk = 26
        targetSdk = 35
        versionCode = 26
        versionName = "2.5.0"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        create("release") {
            val releaseKeystorePropertiesFile = rootProject.file("release.keystore.properties")
            val releaseKeystoreProperties = Properties().apply {
                if (releaseKeystorePropertiesFile.exists()) {
                    releaseKeystorePropertiesFile.inputStream().use { load(it) }
                }
            }
            val storeFilePath = releaseKeystoreProperties.getProperty("storeFile") ?: "release.keystore"
            storeFile = rootProject.file(storeFilePath)
            storePassword = releaseKeystoreProperties.getProperty("storePassword") ?: ""
            keyAlias = releaseKeystoreProperties.getProperty("keyAlias") ?: "glia"
            keyPassword = releaseKeystoreProperties.getProperty("keyPassword") ?: releaseKeystoreProperties.getProperty("storePassword") ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("androidx.datastore:datastore-preferences:1.2.0")
    // Voz neural pt-BR feminina (VITS/Piper Dii) com runtime sherpa-onnx embutido.
    implementation(files("libs/sherpa-onnx-static-link-onnxruntime-1.12.40.aar"))

    implementation("androidx.core:core-ktx:1.15.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.hamcrest:hamcrest-core:1.3")
}