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
        versionCode = 4
        versionName = "1.1.0-sandbox-github"
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
