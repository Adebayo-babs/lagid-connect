import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Settings come from local.properties (never committed):
//   lagid.apiUrl=http://192.168.0.10:4000
//   lagid.gateKey=lc_test_...   (the estate's API key)
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun prop(key: String, default: String) =
    localProps.getProperty(key, default).replace("\"", "\\\"")

android {
    namespace = "com.lagidconnect.verifier"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lagidconnect.verifier"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField(
            "String", "API_BASE_URL",
            "\"${prop("lagid.apiUrl", "http://10.0.2.2:4000")}\""
        )
        buildConfigField(
            "String", "GATE_API_KEY",
            "\"${prop("lagid.gateKey", "")}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    // The face model is memory-mapped, so keep it uncompressed
    androidResources {
        noCompress += "tflite"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Camera
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")

    // Face engine (FaceMatch): ML Kit + MobileFaceNet
    implementation("com.google.mlkit:face-detection:16.1.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Wave-locked QR scanner (WaveLockedQR)
    implementation("com.google.zxing:core:3.5.3")
}
