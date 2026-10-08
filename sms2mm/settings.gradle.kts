rootProject.name = "sms2mm"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// `core` is plain Kotlin/JVM (parsing, OTP filter, categories) and builds anywhere.
// The Android `app` module is added in a later step.
include(":core")
