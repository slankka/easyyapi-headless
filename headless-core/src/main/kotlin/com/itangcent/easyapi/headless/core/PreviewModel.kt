package com.itangcent.easyapi.headless.core

/**
 * Serialized endpoint data consumed by a headless preview renderer.
 *
 * The model deliberately contains no PSI, IntelliJ UI, or channel-specific
 * classes. The IDEA adapter converts its [ApiEndpoint] instances into this
 * model before rendering, so the preview contract can be reused by CLI and
 * future container integrations.
 */
data class PreviewEndpoint(
    val title: String,
    val group: String,
    val method: String,
    val path: String,
    val description: String? = null,
    val source: String? = null,
    val parameters: List<PreviewParameter> = emptyList(),
    val requestBodyJson: String? = null,
    val responseBodyJson: String? = null
)

data class PreviewParameter(
    val name: String,
    val location: String,
    val required: Boolean,
    val description: String? = null
)
