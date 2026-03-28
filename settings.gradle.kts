pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "mobile-observability-platform-java"
include(":apps:api", ":apps:worker")
