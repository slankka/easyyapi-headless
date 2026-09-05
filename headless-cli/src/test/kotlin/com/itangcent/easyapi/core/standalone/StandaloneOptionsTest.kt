package com.itangcent.easyapi.core.standalone

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StandaloneOptionsTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun arguments(): List<String> {
        val project = temporary.newFolder()
        project.resolve("pom.xml").writeText("<project/>")
        return listOf("--project", project.path, "--output", temporary.root.resolve("result.json").path)
    }

    @Test fun parsesRepeatedClassSelection() {
        val options = StandaloneOptions.parse(arguments() + listOf("--class", "demo.A", "--class", "demo.B", "--class", "demo.A"))
        assertEquals("Class selection should preserve order and eliminate duplicates", listOf("demo.A", "demo.B"), options.classes)
        assertEquals("Noninteractive JSON should be the default", "json", options.format)
    }

    @Test fun refusesExistingOutputBeforeAnyExport() {
        val args = arguments()
        temporary.root.resolve("result.json").writeText("keep me")
        assertThrows(IllegalArgumentException::class.java) { StandaloneOptions.parse(args) }
        assertEquals("Existing data must remain intact", "keep me", temporary.root.resolve("result.json").readText())
    }

    @Test fun rejectsUnknownDuplicateAndIncompleteOptions() {
        val args = arguments()
        for (suffix in listOf(listOf("--token", "secret"), listOf("--format"),
            listOf("--format", "json", "--format", "yaml"), listOf("--timeout", "0"),
            listOf("--channel", "postman"), listOf("--format", "ALWAYS_ASK"))) {
            assertThrows("Should reject $suffix", IllegalArgumentException::class.java) { StandaloneOptions.parse(args + suffix) }
        }
    }

    @Test fun refusesNonMavenDirectory() {
        assertThrows(IllegalArgumentException::class.java) {
            StandaloneOptions.parse(listOf("--project", temporary.root.path, "--output", temporary.root.resolve("result.json").path))
        }
    }
}
