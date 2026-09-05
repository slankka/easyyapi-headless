package com.itangcent.easyapi.headless.core

import java.nio.file.Path

/**
 * Result of producing a static headless API preview.
 *
 * This value deliberately contains no IntelliJ, PSI, Swing, or channel types,
 * so CI integrations can consume it without depending on the plugin UI.
 */
data class PreviewArtifact(
    val indexFile: Path,
    val endpointCount: Int,
    val groupCount: Int
)
