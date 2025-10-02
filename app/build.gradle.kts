plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "sexy.lyrics"
    compileSdk = 36

    defaultConfig {
        applicationId = "sexy.lyrics"
        minSdk = 21
        targetSdk = 36
        versionCode = 12
        versionName = "8.0"
    }
    if (project.hasProperty("keystore")) {
        signingConfigs {
            create("release") {
                storeFile = file(project.property("keystore") as String)
                storePassword = project.property("keystorepassword") as String
                keyAlias = project.property("keystorealias") as String
                keyPassword = project.property("keystorekeypassword") as String
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    } else {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file("mykey.jks")
                storePassword = "password"
                keyAlias = "key0"
                keyPassword = "password"
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
           }
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
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(platform(libs.org.jetbrains.kotlin.kotlin.bom))
    implementation(libs.material)
}
