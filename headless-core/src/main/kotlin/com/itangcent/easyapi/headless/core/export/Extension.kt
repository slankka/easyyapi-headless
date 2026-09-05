package com.itangcent.easyapi.headless.core.export

/** Read-only key-value extensions attached to shared export models. */
interface Extension {
    val exts: Map<String, Any?>

    companion object {
        val EMPTY: Extension = EmptyExtension

        fun Extension.asMutable(): MutableExtension =
            if (this is MutableExtension) this
            else MutableExtension().apply { putAll(exts) }
    }
}

private object EmptyExtension : Extension {
    private val emptyMap: Map<String, Any?> = java.util.Collections.emptyMap()
    override val exts: Map<String, Any?> get() = emptyMap
}

/** Mutable extension carrier for models that are enriched during export. */
class MutableExtension : Extension {
    override val exts: MutableMap<String, Any?> = mutableMapOf()

    operator fun get(key: String): Any? = exts[key]

    operator fun set(key: String, value: Any?) {
        exts[key] = value
    }

    fun putAll(entries: Map<String, Any?>) {
        exts.putAll(entries)
    }
}
