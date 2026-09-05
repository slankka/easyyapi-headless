package com.itangcent.easyapi.core.standalone

import com.intellij.openapi.projectRoots.Sdk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class StandaloneSdkOverrideTest {

    @Test
    fun `does not remove an existing SDK from the table`() {
        val sdk = mock<Sdk>()

        assertFalse(StandaloneSdkOverride(sdk, sdk, addedToTable = false).shouldRemoveConfiguredSdk())
    }

    @Test
    fun `removes only a newly registered temporary SDK`() {
        val original = mock<Sdk>()
        val configured = mock<Sdk>()

        assertTrue(StandaloneSdkOverride(original, configured, addedToTable = true).shouldRemoveConfiguredSdk())
        assertFalse(StandaloneSdkOverride(original, original, addedToTable = true).shouldRemoveConfiguredSdk())
    }
}
