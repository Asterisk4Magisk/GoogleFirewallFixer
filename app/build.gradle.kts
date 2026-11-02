// Copyright 2026, Asterisk4Magisk contributors
// SPDX-License-Identifier: GPL-3.0

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "org.asterisk.zcc.fixer.redmagicos"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    // Built-in Kotlin inherits this JVM target.
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(26)
        targetCompatibility = JavaVersion.toVersion(26)
    }

    defaultConfig {
        applicationId = "org.asterisk.zcc.fixer.redmagicos"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    // Provided by the Xposed framework at runtime; never packaged.
    compileOnly(libs.xposed.api)
    testImplementation(libs.junit)
}
