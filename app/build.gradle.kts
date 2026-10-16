plugins {
    id("com.android.application")
}

val includePersonalSkin = providers.gradleProperty("includePersonalSkin").orNull == "true"

android {
    namespace = "com.example.hanziime"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.hanziime"
        minSdk = 23
        targetSdk = 36
        versionCode = 21
        versionName = "0.4.14"
        ndk {
            // x86_64 is the MuMu image. arm64-v8a stays for phones.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
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

    sourceSets {
        getByName("main") {
            if (includePersonalSkin) res.srcDir("../skins/personal/res")
        }
    }


    packaging {
        jniLibs {
            useLegacyPackaging = true
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
