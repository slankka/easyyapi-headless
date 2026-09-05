package com.itangcent.easyapi.core.internal

import com.intellij.openapi.application.ApplicationManager

/** Runtime capabilities; IDE fixtures continue to exercise dialog and tool-window contracts. */
object RuntimeMode {
    /** True for production headless processes, excluding IDE test fixtures that emulate UI. */
    val isHeadless: Boolean
        get() = ApplicationManager.getApplication().let { it.isHeadlessEnvironment && !it.isUnitTestMode }
}
