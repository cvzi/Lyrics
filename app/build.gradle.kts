plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "sexy.lyrics"
    compileSdk = 35

    defaultConfig {
        applicationId = "sexy.lyrics"
        minSdk = 21
        targetSdk = 35
        versionCode = 10
        versionName = "6.0"
    }
    signingConfigs {
        create("release") {
            storeFile = rootProject.file("mykey.jks")
            storePassword = "password"
            keyAlias = "key0"
            keyPassword = "spassword"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfigs.findByName("release")?.let {
                signingConfig = it
            }
        }
        debug {
            isMinifyEnabled = false
            isDebuggable = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    viewBinding {
        enable = true
    }

}

dependencies {
    implementation(libs.appcompat)
    implementation(platform(libs.org.jetbrains.kotlin.kotlin.bom))

}