package com.itangcent.easyapi.core.scan

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.psi.PsiClass
import com.itangcent.easyapi.core.export.ApiEndpoint

/** GUI-free contract for discovering API endpoints from indexed source code. */
interface ApiEndpointScanner {
    suspend fun scanAll(): List<ApiEndpoint>

    suspend fun scanClasses(
        classes: List<PsiClass>,
        indicator: ProgressIndicator? = null
    ): Sequence<ApiEndpoint>
}
