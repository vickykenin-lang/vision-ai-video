plugins {
    id("com.android.application")
}

android {
    namespace = "com.vicky.personalai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vicky.personalai"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.2.0-omnix-copy-markdown"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
