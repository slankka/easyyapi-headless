package com.itangcent.easyapi.core.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

class RequestBodyCacheMergerTest {

    @Test
    fun testAddsNewSchemaFieldsWhilePreservingEditedValues() {
        val cached = """{"toolType":"skill","toolId":"42"}"""
        val generated = """{"toolType":"","toolId":"","scope":"market"}"""

        assertEquals(
            """{"toolType":"skill","toolId":"42","scope":"market"}""",
            RequestBodyCacheMerger.merge(cached, generated)
        )
    }

    @Test
    fun testMergesNestedObjectFields() {
        val cached = """{"config":{"host":"edited"}}"""
        val generated = """{"config":{"host":"","port":8080}}"""

        assertEquals(
            """{"config":{"host":"edited","port":8080}}""",
            RequestBodyCacheMerger.merge(cached, generated)
        )
    }

    @Test
    fun testKeepsNonJsonCachedBodyUntouched() {
        assertEquals("custom request body", RequestBodyCacheMerger.merge("custom request body", "{\"scope\":\"\"}"))
    }
}
