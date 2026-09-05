package com.itangcent.easyapi.channel.openapi

import com.google.gson.JsonParser
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.ExportContext
import com.itangcent.easyapi.headless.core.export.ExportResult
import com.itangcent.easyapi.core.export.HttpMetadata
import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class OpenApiHeadlessTest : EasyApiLightCodeInsightFixtureTestCase() {
    override fun createConfigReader() = TestConfigReader.empty(project)

    fun testDefaultAskSettingsNeverOpenDialogs() = runTest {
        val old = TestDialogManager.setTestDialog(TestDialog { throw AssertionError("Headless export opened a dialog") })
        try {
            val endpoint = ApiEndpoint(name = "ping", metadata = HttpMetadata(path = "/ping", method = HttpMethod.GET))
            val exported = OpenApiChannel().exportHeadless(ExportContext(project, listOf(endpoint)), "json")
            assertTrue("Export should succeed with default settings", exported.result is ExportResult.Success)
            val document = JsonParser.parseString(exported.content).asJsonObject
            assertTrue("Serialized output must contain the endpoint", document.getAsJsonObject("paths").has("/ping"))
            val yaml = OpenApiChannel().exportHeadless(ExportContext(project, listOf(endpoint)), "yaml")
            assertTrue("YAML should be serialized without a dialog", yaml.content!!.contains("/ping"))
        } finally {
            TestDialogManager.setTestDialog(old)
        }
    }
}
