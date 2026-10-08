rootProject.name = "sms2mm"

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

// Plain-Kotlin parsing library (OTP filter, bank layouts, keyword rules).
// A separate build so `./gradlew -p core test` works without the Android SDK.
includeBuild("core")

// The Android app.
include(":app")
