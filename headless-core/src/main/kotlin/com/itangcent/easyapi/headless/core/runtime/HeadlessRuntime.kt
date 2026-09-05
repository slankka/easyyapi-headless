package com.itangcent.easyapi.headless.core.runtime

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.headless.core.PreviewEndpoint
import com.itangcent.easyapi.headless.core.cli.StandaloneOptions

/**
 * Runtime boundary between the IntelliJ bootstrap process and the API
 * extraction implementation.
 *
 * The CLI owns process lifecycle and output files. The IDEA plugin owns PSI,
 * rules, framework exporters, settings and channels. Only this contract crosses
 * that boundary.
 */
interface HeadlessRuntimeProvider {
    /** Applies project-scoped headless configuration before Maven import. */
    suspend fun configureProject(project: Project, options: StandaloneOptions)

    /** Scans and exports the opened project without invoking any UI. */
    suspend fun execute(project: Project, options: StandaloneOptions): HeadlessRuntimeResult
}

sealed interface HeadlessRuntimeResult {
    data class Preview(val endpoints: List<PreviewEndpoint>) : HeadlessRuntimeResult

    data class Export(
        val content: String,
        val endpointCount: Int,
        val target: String
    ) : HeadlessRuntimeResult
}

object HeadlessRuntimeProviders {
    private val EP = ExtensionPointName.create<HeadlessRuntimeProvider>(
        "com.itangcent.idea.plugin.easy-yapi.headlessRuntimeProvider"
    )

    fun requireProvider(project: Project): HeadlessRuntimeProvider =
        EP.getExtensions(project).firstOrNull()
            ?: error("No HeadlessRuntimeProvider is registered. Install the EasyYapi IDEA plugin.")
}
