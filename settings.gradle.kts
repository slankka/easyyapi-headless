rootProject.name = "idea-plugin"

// The IntelliJ plugin remains the compatibility shell for now. Headless code is
// split out incrementally so each module can be built and tested independently.
include(":headless-core", ":headless-cli")

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("org.jetbrains.changelog") version "2.5.0"
    }
}
