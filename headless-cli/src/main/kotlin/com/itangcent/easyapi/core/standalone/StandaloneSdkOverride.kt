package com.itangcent.easyapi.core.standalone

import com.intellij.openapi.projectRoots.Sdk

/** The SDK state changed by a standalone run and the state that must be restored before shutdown. */
internal data class StandaloneSdkOverride(
    val original: Sdk?,
    val configured: Sdk?,
    val addedToTable: Boolean
) {
    fun shouldRemoveConfiguredSdk(): Boolean = addedToTable && configured != null && configured !== original
}
