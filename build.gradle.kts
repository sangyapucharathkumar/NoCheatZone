plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.example.nocheatzone"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.nocheatzone"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    val isBundleBuild = gradle.startParameter.taskNames.any { taskName ->
        taskName.contains("bundle", ignoreCase = true)
    }

    // Build per-ABI APKs for APK tasks; disable for bundle tasks to avoid prebundle conflicts.
    splits {
        abi {
            isEnable = !isBundleBuild
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true;
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.core.splashscreen)
    implementation(libs.gson)
    implementation(libs.recyclerview)

    // Firebase using Version Catalog & BOM
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    implementation(libs.firebase.analytics)
    
    // CameraX (Synchronized via Version Catalog)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    
    // ML Kit Text Recognition
    implementation(libs.text.recognition)
    implementation(libs.mlkit.face.detection)

    // Unit and instrumentation testing dependencies.
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}