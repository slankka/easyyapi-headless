package com.itangcent.easyapi.core.dashboard

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.itangcent.easyapi.core.util.json.GsonUtils

/** Reconciles an edited dashboard body with fields added to the source model. */
object RequestBodyCacheMerger {

    fun merge(cachedBody: String?, generatedBody: String?): String? {
        if (cachedBody == null || generatedBody == null) return cachedBody ?: generatedBody

        return runCatching {
            val cached = JsonParser.parseString(cachedBody)
            val generated = JsonParser.parseString(generatedBody)
            if (!cached.isJsonObject || !generated.isJsonObject) return cachedBody

            val changed = mergeObjects(cached.asJsonObject, generated.asJsonObject)
            if (changed) GsonUtils.toJson(cached) else cachedBody
        }.getOrDefault(cachedBody)
    }

    private fun mergeObjects(cached: JsonObject, generated: JsonObject): Boolean {
        var changed = false
        for ((name, generatedValue) in generated.entrySet()) {
            val cachedValue = cached.get(name)
            if (cachedValue == null) {
                cached.add(name, generatedValue.deepCopy())
                changed = true
            } else if (cachedValue.isJsonObject && generatedValue.isJsonObject) {
                changed = mergeObjects(cachedValue.asJsonObject, generatedValue.asJsonObject) || changed
            }
        }
        return changed
    }
}
