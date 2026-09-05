package com.itangcent.easyapi.core.headless

import com.google.gson.GsonBuilder
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.itangcent.easyapi.channel.spi.HeadlessExport
import com.itangcent.easyapi.core.config.ConfigReader
import com.itangcent.easyapi.core.dashboard.ApiScanner
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.ExportContext
import com.itangcent.easyapi.core.export.HttpMetadata
import com.itangcent.easyapi.core.export.path
import com.itangcent.easyapi.core.ide.DumbModeHelper
import com.itangcent.easyapi.core.internal.threading.read
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.scan.ApiEndpointScanner
import com.itangcent.easyapi.core.settings.SettingBinder
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.core.settings.update
import com.itangcent.easyapi.format.json.ObjectModelJsonConverter
import com.itangcent.easyapi.headless.core.PreviewEndpoint
import com.itangcent.easyapi.headless.core.PreviewParameter
import com.itangcent.easyapi.headless.core.cli.StandaloneOptions
import com.itangcent.easyapi.headless.core.export.ExportResult
import com.itangcent.easyapi.headless.core.runtime.HeadlessRuntimeProvider
import com.itangcent.easyapi.headless.core.runtime.HeadlessRuntimeResult
import org.jetbrains.idea.maven.buildtool.MavenSyncSpec
import org.jetbrains.idea.maven.project.MavenProjectsManager

/** IDEA-plugin adapter that supplies the PSI/rules/export implementation to the headless CLI. */
class IdeaHeadlessRuntimeProvider : HeadlessRuntimeProvider, IdeaLog {
    override suspend fun configureProject(project: Project, options: StandaloneOptions) {
        options.ideaConfig?.let { config ->
            com.itangcent.easyapi.core.standalone.IdeaYapiSettingsImporter.importInto(
                config,
                SettingBinder.getInstance(project)
            )
        }
        val maven = MavenProjectsManager.getInstance(project)
        maven.generalSettings.isWorkOffline = options.offline
        options.mavenSettings?.let { maven.generalSettings.setUserSettingsFile(it.toString()) }
        SettingBinder.getInstance(project).update(GeneralSettings::class) {
            if (options.channel !in disabledChannels) {
                enabledChannels = (enabledChannels.toList() + options.channel).distinct().toTypedArray()
            }
        }
    }

    override suspend fun execute(project: Project, options: StandaloneOptions): HeadlessRuntimeResult {
        prepareProject(project, options)
        val scanner: ApiEndpointScanner = ApiScanner.getInstance(project)
        val endpoints = if (options.classes.isEmpty()) scanner.scanAll() else {
            val classes = read {
                val facade = JavaPsiFacade.getInstance(project)
                options.classes.map { name ->
                    requireNotNull(facade.findClass(name, GlobalSearchScope.projectScope(project))) {
                        "Class not found in project sources: $name"
                    }
                }
            }
            scanner.scanClasses(classes, EmptyProgressIndicator()).toList()
        }
        check(endpoints.isNotEmpty()) { "No API endpoints found" }
        if (options.mode == "preview") {
            return HeadlessRuntimeResult.Preview(endpoints.map(::toPreviewEndpoint))
        }

        val exported: HeadlessExport = project.getService(com.itangcent.easyapi.core.standalone.StandaloneExportService::class.java)
            .export(options.channel, options.format, options.classes)
        val result = exported.result
        check(result is ExportResult.Success) {
            if (result is ExportResult.Error) result.message else "Export cancelled"
        }
        val content = exported.content ?: GsonBuilder().setPrettyPrinting().create().toJson(
            mapOf("count" to result.count, "target" to result.target, "message" to result.metadata?.formatDisplay())
        )
        return HeadlessRuntimeResult.Export(content, result.count, result.target)
    }

    private suspend fun prepareProject(project: Project, options: StandaloneOptions) {
        val pom = requireNotNull(com.intellij.openapi.vfs.LocalFileSystem.getInstance()
            .refreshAndFindFileByNioFile(options.project.resolve("pom.xml")))
        val maven = MavenProjectsManager.getInstance(project)
        maven.addManagedFilesOrUnignoreNoUpdate(listOf(pom))
        LOG.info("Headless resolving Maven dependencies: ${options.project}")
        maven.updateAllMavenProjects(MavenSyncSpec.full("EasyYapi headless", true))
        com.intellij.platform.backend.observation.Observation.awaitConfiguration(project) { message -> LOG.info("Headless readiness: $message") }
        DumbModeHelper.waitForSmartMode(project)
        ConfigReader.getInstance(project).reload()
        check(maven.projects.isNotEmpty()) { "Maven import produced no projects" }
        val broken = maven.projects.filter { it.hasReadingErrors() || it.hasUnresolvedArtifacts() }
        broken.forEach { model -> LOG.warn("Maven import problems in ${model.displayName}: ${model.problems}") }
        check(broken.isEmpty()) {
            "Maven import has unresolved dependencies or model errors: ${broken.joinToString { it.displayName }}. See idea.log."
        }
        read {
            check(JavaPsiFacade.getInstance(project).findClass("java.lang.Object", GlobalSearchScope.allScope(project)) != null) {
                "Project JDK is not indexed"
            }
        }
    }

    private fun toPreviewEndpoint(endpoint: ApiEndpoint): PreviewEndpoint {
        val meta = endpoint.metadata as? HttpMetadata
        return PreviewEndpoint(
            title = endpoint.name?.takeIf { it.isNotBlank() } ?: endpoint.path,
            group = endpoint.folder?.takeIf { it.isNotBlank() } ?: endpoint.className ?: "Ungrouped",
            method = meta?.method?.name ?: endpoint.metadata.protocol,
            path = endpoint.path,
            description = endpoint.description,
            source = endpoint.className,
            parameters = meta?.parameters?.map { parameter ->
                PreviewParameter(parameter.name, parameter.binding?.javaClass?.simpleName ?: "—", parameter.required, parameter.description)
            } ?: emptyList(),
            requestBodyJson = meta?.body?.let(ObjectModelJsonConverter::toJson),
            responseBodyJson = meta?.responseBody?.let(ObjectModelJsonConverter::toJson)
        )
    }
}
