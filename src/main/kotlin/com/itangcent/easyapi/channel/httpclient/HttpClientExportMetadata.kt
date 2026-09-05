package com.itangcent.easyapi.channel.httpclient

import com.itangcent.easyapi.headless.core.export.ExportMetadata

data class HttpClientExportMetadata(val content: String) : ExportMetadata {
    override fun formatDisplay(): String? = null
}
