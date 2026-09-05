package com.itangcent.easyapi.core.standalone

import java.nio.file.Files
import java.nio.file.Path

/** Preserves the project's raw SDK name when the isolated standalone SDK table cannot resolve it. */
internal class StandaloneProjectSdkReference private constructor(
    private val miscFile: Path,
    private val originalName: String?
) {
    fun restore() {
        if (!Files.isRegularFile(miscFile)) return
        val current = Files.readString(miscFile)
        val sdkAttribute = Regex("\\s+project-jdk-name=\"[^\"]*\"")
        val restored = if (originalName == null) {
            current.replace(sdkAttribute, "")
        } else {
            val replacement = " project-jdk-name=\"${escapeAttribute(originalName)}\""
            if (sdkAttribute.containsMatchIn(current)) current.replace(sdkAttribute, replacement)
            else Regex("(<component\\s+name=\"ProjectRootManager\"[^>]*?)(\\s*/?>)")
                .replace(current) { match -> match.groupValues[1] + replacement + match.groupValues[2] }
        }
        if (restored != current) Files.writeString(miscFile, restored)
    }

    /** Original project SDK name, used to bind the isolated SDK table before model sync. */
    fun sdkName(): String? = originalName

    /** Removes the persisted SDK reference while the isolated project model is opening. */
    fun clearSdkNameForOpen() {
        if (!Files.isRegularFile(miscFile)) return
        val current = Files.readString(miscFile)
        val attribute = Regex("\\s+project-jdk-name=\"[^\"]*\"")
        val updated = current.replace(attribute, "")
        if (updated != current) Files.writeString(miscFile, updated)
    }

    companion object {
        fun capture(project: Path): StandaloneProjectSdkReference {
            val miscFile = project.resolve(".idea/misc.xml")
            val content = if (Files.isRegularFile(miscFile)) Files.readString(miscFile) else ""
            val name = Regex("project-jdk-name=\"([^\"]*)\"").find(content)?.groupValues?.get(1)
            return StandaloneProjectSdkReference(miscFile, name)
        }

        private fun escapeAttribute(value: String): String = value.replace("&", "&amp;").replace("\"", "&quot;")
    }
}
