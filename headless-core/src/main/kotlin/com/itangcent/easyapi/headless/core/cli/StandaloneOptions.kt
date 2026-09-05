package com.itangcent.easyapi.headless.core.cli

import java.nio.file.Files
import java.nio.file.Path

/** Validated, platform-neutral command-line contract for headless execution. */
data class StandaloneOptions(
    val project: Path,
    val jdk: Path,
    val output: Path,
    val channel: String,
    val format: String,
    val mode: String,
    val offline: Boolean,
    val classes: List<String>,
    val timeoutSeconds: Long,
    val mavenSettings: Path? = null,
    val ideaConfig: Path? = null,
) {
    companion object {
        const val USAGE = "easyyapi --project <Maven directory> --output <file> " +
            "[--jdk <JDK home>] [--mode export|preview] [--offline] [--channel openapi|yapi] [--format json|yaml] " +
            "[--class <qualified.name>] [--timeout <seconds>] [--maven-settings <settings.xml>] [--idea-config <IDEA config>]"

        /** Parses strict options before opening or changing any project. */
        fun parse(args: List<String>): StandaloneOptions {
            val values = linkedMapOf<String, String>()
            val classes = mutableListOf<String>()
            val allowed = setOf("project", "jdk", "mode", "output", "channel", "format", "class", "timeout", "maven-settings", "idea-config")
            val normalizedArgs = args.toList()
            require(normalizedArgs.none { it == "--offline" && normalizedArgs.indexOf(it) % 2 == 1 }) { "--offline does not take a value. $USAGE" }
            val offline = normalizedArgs.contains("--offline")
            val valueArgs = normalizedArgs.filterNot { it == "--offline" }
            require(valueArgs.size % 2 == 0) { "Every option requires a value. $USAGE" }
            valueArgs.chunked(2).forEach { (key, value) ->
                require(key.startsWith("--") && key.removePrefix("--") in allowed) { "Unknown option: $key" }
                require(value.isNotBlank() && !value.startsWith("--")) { "Missing value for $key" }
                if (key == "--class") classes.add(value)
                else require(values.put(key.removePrefix("--"), value) == null) { "Duplicate option: $key" }
            }
            val project = Path.of(requireNotNull(values["project"]) { "--project is required" }).toAbsolutePath().normalize()
            require(Files.isRegularFile(project.resolve("pom.xml"))) { "A Maven project directory containing pom.xml is required: $project" }
            val jdk = Path.of(values["jdk"] ?: System.getProperty("java.home")).toAbsolutePath().normalize()
            require(Files.isRegularFile(jdk.resolve("bin/java")) || Files.isRegularFile(jdk.resolve("bin/java.exe"))) { "Invalid JDK home: $jdk" }
            val output = Path.of(requireNotNull(values["output"]) { "--output is required" }).toAbsolutePath().normalize()
            require(!Files.exists(output)) { "Output already exists: $output. Choose a new path." }
            val channel = values["channel"] ?: "openapi"
            require(channel in setOf("openapi", "yapi")) { "Unsupported headless channel: $channel" }
            val format = values["format"] ?: "json"
            require(format in setOf("json", "yaml")) { "Unsupported format: $format" }
            val mode = values["mode"] ?: "export"
            require(mode in setOf("export", "preview")) { "Unsupported mode: $mode" }
            val timeout = (values["timeout"] ?: "600").toLongOrNull()
            require(timeout != null && timeout in 1..86400) { "--timeout must be between 1 and 86400 seconds" }
            val mavenSettings = values["maven-settings"]?.let { Path.of(it).toAbsolutePath().normalize() }
            require(mavenSettings == null || Files.isRegularFile(mavenSettings)) { "Maven settings file does not exist: $mavenSettings" }
            val ideaConfig = values["idea-config"]?.let { Path.of(it).toAbsolutePath().normalize() }
            require(ideaConfig == null || Files.isDirectory(ideaConfig)) { "IDEA config directory does not exist: $ideaConfig" }
            return StandaloneOptions(project, jdk, output, channel, format, mode, offline, classes.distinct(), timeout, mavenSettings, ideaConfig)
        }
    }
}
