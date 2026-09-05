package com.itangcent.easyapi.channel.spi

import com.itangcent.easyapi.core.export.ExportContext
import com.itangcent.easyapi.headless.core.export.ExportResult

/** Explicit opt-in for channels that can export without dialogs or clipboard access. */
interface HeadlessChannel {
    /** Executes an export; credentials belong in environment/configuration, never these options. */
    suspend fun exportHeadless(context: ExportContext, format: String): HeadlessExport
}

/** Serialized output crosses the process boundary without retaining PSI or channel-specific models. */
data class HeadlessExport(val result: ExportResult, val content: String? = null)
