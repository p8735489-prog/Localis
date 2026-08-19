plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.localaisearch"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.localaisearch"
        minSdk = 26
        targetSdk = 35
        // versionCode auto-increments from git tag count or build number
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 210)
        versionName = "2.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17", "-fexceptions", "-frtti")
                arguments("-DANDROID_STL=c++_shared")
            }
        }
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("SIGNING_KEYSTORE_PATH") ?: ""
            if (keystorePath.isNotBlank()) {
                val keystoreFile = file(keystorePath)
                if (keystoreFile.exists()) {
                    storeFile = keystoreFile
                    storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: ""
                    keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: ""
                    keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: ""
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            val releaseSigning = signingConfigs.findByName("release")
            if (releaseSigning?.storeFile?.exists() == true) {
                signingConfig = releaseSigning
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
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
    // kotlinCompilerExtensionVersion is managed by the kotlin.compose plugin (Kotlin 2.x).
    // Do not set composeOptions.kotlinCompilerExtensionVersion when using the Compose Compiler Gradle Plugin.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.androidx.browser)
    implementation(libs.kotlinx.coroutines.android)
    // Embedded Tor service for optional app-only Tor routing and custom bridge configuration.
    // 0.4.9.5 is the latest release with minCompileSdk=1 (no API-level constraint).
    // Versions 0.4.9.5.1+ require compileSdk 36/37 which is incompatible with AGP 8.7.3 / compileSdk 35.
    // NOTE: tor-android:0.4.9.5 transitively depends on kotlin-stdlib:2.3.0 (incompatible with
    // our Kotlin 2.1.21 compiler). We exclude it and let the project's own kotlin-stdlib version win.
    implementation("info.guardianproject:tor-android:0.4.9.5") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }
    implementation("info.guardianproject:jtorctl:0.4.5.7")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
