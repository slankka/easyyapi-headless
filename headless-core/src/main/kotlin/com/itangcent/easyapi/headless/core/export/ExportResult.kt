package com.itangcent.easyapi.headless.core.export

/** Result of an API export operation. */
sealed class ExportResult {
    data class Success(
        val count: Int,
        val target: String,
        val metadata: ExportMetadata? = null
    ) : ExportResult()

    data class Error(val message: String) : ExportResult()

    data object Cancelled : ExportResult()
}
