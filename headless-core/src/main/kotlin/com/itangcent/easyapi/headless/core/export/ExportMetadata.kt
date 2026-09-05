package com.itangcent.easyapi.headless.core.export

/** Channel-specific metadata attached to a successful export. */
interface ExportMetadata {
    fun formatDisplay(): String?
}
