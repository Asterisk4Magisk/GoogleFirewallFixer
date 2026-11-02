// Copyright 2026, Asterisk4Magisk contributors
// SPDX-License-Identifier: GPL-3.0

buildscript {
    dependencies {
        // AGP's built-in Kotlin uses this plugin version; the bundled one cannot target JVM 26.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
}
