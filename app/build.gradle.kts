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
        ndkVersion = "27.2.12479018"
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
            isShrinkResources = true
            val releaseSigning = signingConfigs.findByName("release")
            if (releaseSigning?.storeFile?.exists() == true &&
                !releaseSigning.storePassword.isNullOrBlank() &&
                !releaseSigning.keyAlias.isNullOrBlank() &&
                !releaseSigning.keyPassword.isNullOrBlank()) {
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
    // Tor support (app-scoped SOCKS proxy via Guardian Project's embedded
    // TorService — this is a plain Service + SOCKS port, NOT a VpnService,
    // so only this app's OkHttp traffic is routed through Tor; other apps
    // are unaffected).
    //
    // Pinned to 0.4.8.21.1, NOT the newer 0.4.9.x line. 0.4.9.9.1 was tried
    // first and failed CI's :app:checkDebugAarMetadata with:
    //   "Dependency 'info.guardianproject:tor-android:0.4.9.9.1' requires
    //    libraries and applications that depend on it to compile against
    //    version 37 or later of the Android APIs. :app is currently
    //    compiled against android-35. Also, the maximum recommended compile
    //    SDK version for Android Gradle plugin 8.7.3 is 35."
    // That is an AAR-metadata compileSdk gate, not a dexing/Java-version
    // issue — no amount of D8/desugaring config fixes it. The two real
    // options are (a) bump compileSdk to 37+ together with a newer AGP that
    // supports it, or (b) stay on this project's current compileSdk/AGP and
    // use a tor-android release built against it. 0.4.8.21.1 uses the same
    // TorService action-string API this app talks to (see TorManager.kt),
    // so (b) was the lower-risk fix. If bumping compileSdk/AGP later,
    // re-verify whether a newer tor-android is needed/available first.
    implementation(libs.guardianproject.tor.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
