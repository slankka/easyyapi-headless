package com.itangcent.easyapi.core.standalone

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.itangcent.easyapi.channel.spi.ChannelRegistry
import com.itangcent.easyapi.channel.spi.HeadlessChannel
import com.itangcent.easyapi.channel.spi.HeadlessExport
import com.itangcent.easyapi.core.dashboard.ApiScanner
import com.itangcent.easyapi.core.export.ExportContext
import com.itangcent.easyapi.headless.core.export.ExportResult
import com.itangcent.easyapi.core.internal.threading.read
import com.itangcent.easyapi.core.logging.console
import com.itangcent.easyapi.core.scan.ApiEndpointScanner

/** Shared execution boundary for standalone clients; never invokes channel result UI. */
@Service(Service.Level.PROJECT)
class StandaloneExportService(private val project: Project) {
    /** @requires Background context; PSI access uses internal read actions. */
    suspend fun export(channelId: String, format: String, classNames: List<String>): HeadlessExport {
        val registry = ChannelRegistry.getInstance(project)
        val channel = registry.getChannel(channelId)
            ?: return HeadlessExport(ExportResult.Error("Unknown channel: $channelId"))
        if (!registry.isEnabled(channel)) return HeadlessExport(ExportResult.Error("Channel is disabled: $channelId"))
        val headless = channel as? HeadlessChannel
            ?: return HeadlessExport(ExportResult.Error("Channel does not support headless export: $channelId"))
        project.console.info("Standalone export: channel=$channelId, classes=$classNames")
        val scanner: ApiEndpointScanner = ApiScanner.getInstance(project)
        val endpoints = if (classNames.isEmpty()) scanner.scanAll() else {
            val classes = read {
                val facade = JavaPsiFacade.getInstance(project)
                classNames.map { name ->
                    requireNotNull(facade.findClass(name, GlobalSearchScope.projectScope(project))) {
                        "Class not found in project sources: $name"
                    }
                }
            }
            scanner.scanClasses(classes, EmptyProgressIndicator()).toList()
        }
        if (endpoints.isEmpty()) return HeadlessExport(ExportResult.Error("No API endpoints found"))
        return headless.exportHeadless(ExportContext(project, endpoints, channelId = channelId), format)
    }
}
