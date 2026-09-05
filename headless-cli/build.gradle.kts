plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform") version "2.11.0"
}

description = "Headless command-line integration boundary for EasyYapi"

dependencies {
    implementation(kotlin("stdlib"))
    // Core is provided once by the main plugin at runtime. The CLI depends on
    // that plugin in plugin.xml, so embedding a second Core JAR would split
    // the EP interface across plugin classloaders.
    compileOnly(project(":headless-core"))

    intellijPlatform {
        intellijIdeaCommunity("2025.2.1")
        bundledPlugins(
            "com.intellij.java",
            "org.jetbrains.idea.maven",
            "org.jetbrains.plugins.gradle",
            "org.jetbrains.kotlin",
            "org.intellij.groovy",
            "org.intellij.intelliLang"
        )
        plugin("org.intellij.scala:2025.2.51")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Plugin.Java)
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

intellijPlatform {
    pluginConfiguration {
        id = "com.itangcent.easyapi.headless-cli"
        name = "EasyYapi Headless CLI"
        version = rootProject.version.toString()
        ideaVersion {
            sinceBuild = "252"
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testCompileOnly(project(":headless-core"))
    testRuntimeOnly(project(":headless-core"))
}
