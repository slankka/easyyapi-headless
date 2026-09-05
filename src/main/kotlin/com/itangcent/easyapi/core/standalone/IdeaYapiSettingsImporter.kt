package com.itangcent.easyapi.core.standalone

import com.itangcent.easyapi.channel.yapi.YapiSettings
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.settings.SettingBinder
import com.itangcent.easyapi.core.settings.update
import org.w3c.dom.Element
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory

/** Reads only YapiSettings from an IDEA config; never writes to the source config. */
object IdeaYapiSettingsImporter : IdeaLog {
    private const val MODULE = "com.itangcent.easyapi.channel.yapi.YapiSettings"
    private val FIELDS = setOf("yapiServer", "yapiTokens", "yapiExportMode", "yapiReqBodyJson5", "yapiResBodyJson5")

    fun importInto(configDirectory: Path, binder: SettingBinder) {
        val file = configDirectory.resolve("options/easyapi_app.xml")
        if (!Files.isRegularFile(file)) {
            LOG.info("IDEA Yapi settings not found: $file")
            return
        }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isExpandEntityReferences = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val document = factory.newDocumentBuilder().parse(file.toFile())
        fun elements(element: Element): Sequence<Element> = sequence {
            for (index in 0 until element.childNodes.length) {
                val child = element.childNodes.item(index)
                if (child is Element) yield(child)
            }
        }
        fun descendants(element: Element): Sequence<Element> = sequence {
            for (child in elements(element)) {
                yield(child)
                yieldAll(descendants(child))
            }
        }
        val module = generateSequence(document.documentElement as Element) { null }
            .flatMap { descendants(it) }
            .map { it as Element }
            .firstOrNull { it.getAttribute("key") == MODULE } ?: return
        val values = descendants(module)
            .map { it as Element }
            .filter { it.getAttribute("key") in FIELDS }
            .associate { it.getAttribute("key") to (it.getAttribute("value").ifBlank { it.textContent.trim() }) }
        if (values.isEmpty()) return
        binder.update(YapiSettings::class) {
            values["yapiServer"]?.let { yapiServer = it.ifBlank { null } }
            values["yapiTokens"]?.let { yapiTokens = it.ifBlank { null } }
            values["yapiExportMode"]?.let { yapiExportMode = it }
            values["yapiReqBodyJson5"]?.let { yapiReqBodyJson5 = it.toBoolean() }
            values["yapiResBodyJson5"]?.let { yapiResBodyJson5 = it.toBoolean() }
        }
        LOG.info("Imported Yapi settings read-only from $file")
    }
}
