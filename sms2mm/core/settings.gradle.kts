// `core` is its own build so its tests run anywhere (no Android SDK needed):
//   ./gradlew -p core test
// The Android app pulls it in with includeBuild("core").
rootProject.name = "core"

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
