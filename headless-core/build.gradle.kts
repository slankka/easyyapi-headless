plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform") version "2.11.0"
}

description = "Headless contracts and PSI/type infrastructure for EasyYapi"

dependencies {
    // IntelliJ supplies Kotlin at runtime. Do not embed a second stdlib in
    // the plugin sandbox, otherwise the platform and project Kotlin versions
    // can produce linkage errors such as NoSuchMethodError.
    compileOnly(kotlin("stdlib"))

    intellijPlatform {
        intellijIdeaCommunity("2025.2.1")
        bundledPlugins(
            "com.intellij.java",
            "org.jetbrains.kotlin"
        )
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

repositories {
    intellijPlatform {
        defaultRepositories()
    }
}
