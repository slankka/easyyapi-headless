package com.itangcent.easyapi.core.standalone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.util.Comparator

class StandaloneProjectSdkReferenceTest {

    @Test
    fun `restores original SDK name without replacing Maven metadata`() {
        val project = Files.createTempDirectory("easyyapi-sdk-reference")
        try {
            val idea = Files.createDirectories(project.resolve(".idea"))
            val misc = idea.resolve("misc.xml")
            Files.writeString(misc, "<component name=\"ProjectRootManager\" project-jdk-name=\"21\" project-jdk-type=\"JavaSDK\" />")
            val reference = StandaloneProjectSdkReference.capture(project)
            Files.writeString(misc, "<component name=\"ProjectRootManager\" project-jdk-type=\"JavaSDK\" />")

            reference.restore()

            val restored = Files.readString(misc)
            assertTrue(restored.contains("project-jdk-name=\"21\""))
            assertTrue(restored.contains("project-jdk-type=\"JavaSDK\""))
        } finally {
            Files.walk(project).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    @Test
    fun `restores SDK name when IDEA omits SDK type`() {
        val project = Files.createTempDirectory("easyyapi-sdk-reference")
        try {
            val idea = Files.createDirectories(project.resolve(".idea"))
            val misc = idea.resolve("misc.xml")
            Files.writeString(misc, "<component name=\"ProjectRootManager\" project-jdk-name=\"21\" />")
            val reference = StandaloneProjectSdkReference.capture(project)
            Files.writeString(misc, "<component name=\"ProjectRootManager\" />")

            reference.restore()

            assertEquals("<component name=\"ProjectRootManager\" project-jdk-name=\"21\" />", Files.readString(misc))
        } finally {
            Files.walk(project).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
