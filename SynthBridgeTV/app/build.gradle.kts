plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.synthbridgetv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.synthbridgetv"
        minSdk = 23            // android.media.midi.MidiManager needs API 23
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters.clear()
            abiFilters.add("armeabi-v7a")   // 32-bit ARM TV boxes; no native code in this project
        }
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }
}
