plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.hanziime"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.hanziime"
        minSdk = 23
        targetSdk = 36
        versionCode = 6
        versionName = "0.4.1"
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
}

dependencies {
    implementation("com.google.mlkit:digital-ink-recognition:19.0.0")
    testImplementation("junit:junit:4.13.2")
}
