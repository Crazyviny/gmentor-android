plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val configuredStartUrl = providers.gradleProperty("START_URL")
    .orElse("https://gmentor.ru/")

android {
    namespace = "com.example.offlinewebview"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.offlinewebview"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "START_URL", "\"${configuredStartUrl.get()}\"")
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
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
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.12.3")
    implementation("androidx.webkit:webkit:1.15.0")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
}
