package com.itangcent.easyapi.core.standalone

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ProjectRootManager
import com.itangcent.easyapi.core.internal.threading.IdeDispatchers
import com.itangcent.easyapi.core.internal.threading.write
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.headless.core.runtime.HeadlessRuntimeProviders
import com.itangcent.easyapi.headless.core.runtime.HeadlessRuntimeResult
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.system.exitProcess

/** Version-pinned Platform entry point. Owns project startup, SDK lifecycle and process shutdown. */
@Suppress("DEPRECATION")
class StandaloneStarter : ApplicationStarter, IdeaLog {
    override val requiredModality: Int get() = ApplicationStarter.NOT_IN_EDT
    override val isHeadless: Boolean get() = true

    override fun main(args: List<String>) {
        val arguments = args.drop(1)
        if (arguments == listOf("--help")) {
            System.out.write((StandaloneOptions.USAGE + "\n").toByteArray())
            exitProcess(0)
        }
        val code = try {
            val options = StandaloneOptions.parse(arguments)
            runBlocking(IdeDispatchers.Background) {
                withTimeout(options.timeoutSeconds * 1000) { execute(options) }
            }
            0
        } catch (e: Exception) {
            LOG.warn("Standalone export failed", e)
            System.err.write(("EasyYapi: ${e.message}\n").toByteArray())
            1
        }
        exitProcess(code)
    }

    /** @requires Background context; project model mutations use internal write actions. */
    private suspend fun execute(options: StandaloneOptions) {
        Files.createDirectories(options.output.parent)
        val pendingOutput = if (options.mode == "export") {
            Files.createTempFile(options.output.parent, ".easyyapi-", ".tmp")
        } else null
        try {
            executeProject(options, pendingOutput)
        } finally {
            pendingOutput?.let(Files::deleteIfExists)
        }
    }

    private suspend fun executeProject(options: StandaloneOptions, pendingOutput: Path?) {
        LOG.info("Standalone opening Maven project: ${options.project}")
        val manager = ProjectManagerEx.getInstanceEx()
        val sdkReference = StandaloneProjectSdkReference.capture(options.project)
        val sdkName = selectSdkName(options, null)
        sdkReference.clearSdkNameForOpen()
        val project = try {
            requireNotNull(manager.openProjectAsync(options.project, OpenProjectTask {
                isNewProject = !Files.isDirectory(options.project.resolve(".idea"))
                runConfigurators = false
                showWelcomeScreen = false
                beforeOpen = { openingProject ->
                    HeadlessRuntimeProviders.requireProvider(openingProject).configureProject(openingProject, options)
                    true
                }
            })) { "Could not open project: ${options.project}" }
        } catch (e: Exception) {
            sdkReference.restore()
            throw e
        }
        val sdkOverride = configureSdk(project, options, sdkName)
        try {
            when (val result = HeadlessRuntimeProviders.requireProvider(project).execute(project, options)) {
                is HeadlessRuntimeResult.Preview -> {
                    ApiPreviewRenderer.renderModel(result.endpoints, options.output)
                    System.err.write(("Generated API preview with ${result.endpoints.size} endpoints at ${options.output}\n").toByteArray())
                }
                is HeadlessRuntimeResult.Export -> {
                    requireNotNull(pendingOutput) { "Export output staging file is missing" }
                    Files.writeString(pendingOutput, result.content, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
                    Files.move(pendingOutput, options.output)
                    System.err.write(("Exported ${result.endpointCount} endpoints to ${options.output}\n").toByteArray())
                }
            }
        } finally {
            withContext(NonCancellable) {
                withTimeout(30_000) {
                    restoreSdk(project, sdkOverride)
                    manager.forceCloseProjectAsync(project, true)
                    sdkReference.restore()
                }
            }
        }
    }

    /** @requires Background context; SDK changes are published after the project is open. */
    private suspend fun configureSdk(project: Project, options: StandaloneOptions, projectSdkName: String?): StandaloneSdkOverride = write {
        val projectRootManager = ProjectRootManager.getInstance(project)
        val original = projectRootManager.projectSdk
        val table = ProjectJdkTable.getInstance()
        val existing = table.allJdks.firstOrNull { it.homePath == options.jdk.toString() }
        val configured = existing ?: JavaSdk.getInstance().createJdk(
            projectSdkName?.takeIf { it.isNotBlank() } ?: "EasyYapi ${options.jdk}", options.jdk.toString(), false
        ).also { table.addJdk(it) }
        if (original !== configured) projectRootManager.projectSdk = configured
        StandaloneSdkOverride(original, configured, addedToTable = existing == null)
    }

    /** Chooses a project-model name that cannot collide with another SDK in the isolated table. */
    private fun selectSdkName(options: StandaloneOptions, originalName: String?): String {
        val table = ProjectJdkTable.getInstance()
        table.allJdks.firstOrNull { it.homePath == options.jdk.toString() }?.let { return it.name }
        val preferred = originalName?.takeIf { it.isNotBlank() } ?: "EasyYapi ${options.jdk}"
        if (table.allJdks.none { it.name == preferred }) return preferred
        val base = "EasyYapi ${options.jdk}"
        if (table.allJdks.none { it.name == base }) return base
        return generateSequence(2) { it + 1 }
            .map { "$base ($it)" }
            .first { candidate -> table.allJdks.none { it.name == candidate } }
    }

    /** @requires Background context; restores the project state before IntelliJ saves the project. */
    private suspend fun restoreSdk(project: Project, override: StandaloneSdkOverride) {
        write {
            ProjectRootManager.getInstance(project).projectSdk = override.original
            if (override.shouldRemoveConfiguredSdk()) {
                ProjectJdkTable.getInstance().removeJdk(override.configured!!)
            }
        }
    }
}
