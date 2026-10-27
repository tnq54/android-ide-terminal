plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.termide.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.termide.android"
        minSdk = 24
        targetSdk = 35
        versionCode = 5
        versionName = "4.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        val debugKeystore = file("/home/ubuntu/.android/debug.keystore")
        if (debugKeystore.exists()) {
            create("releaseSign") {
                storeFile = debugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val debugKeystore = file("/home/ubuntu/.android/debug.keystore")
            if (debugKeystore.exists()) {
                signingConfig = signingConfigs.getByName("releaseSign")
            }
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
    implementation("com.github.mwiede:jsch:0.2.20")
    testImplementation("junit:junit:4.13.2")
}
