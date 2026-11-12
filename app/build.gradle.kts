plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.duoshell.sonnet"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.duoshell.sonnet"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1)
        versionName = "1.0.0"
    }

    // Stable signing so repeat CI builds update in place without losing data.
    // The keystore is NEVER committed: CI decodes it from a GitHub secret (or a restored
    // cache) into a temp file and passes its location + passwords via environment variables.
    val ksPath = System.getenv("DUOSHELL_KEYSTORE_PATH")
    val hasCiKey = !ksPath.isNullOrBlank() && file(ksPath).exists()
    if (hasCiKey) {
        signingConfigs {
            create("ci") {
                storeFile = file(ksPath!!)
                storePassword = System.getenv("DUOSHELL_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("DUOSHELL_KEY_ALIAS")
                keyPassword = System.getenv("DUOSHELL_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            if (hasCiKey) signingConfig = signingConfigs.getByName("ci")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
        warningsAsErrors = false
        htmlReport = true
        xmlReport = true
    }
    testOptions { unitTests.isReturnDefaultValues = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.window:window:1.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
