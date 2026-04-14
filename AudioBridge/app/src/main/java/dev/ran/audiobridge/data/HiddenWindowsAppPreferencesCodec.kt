package dev.ran.audiobridge.data

import dev.ran.audiobridge.model.HiddenWindowsApp
import dev.ran.audiobridge.model.HiddenWindowsAppSupport
import org.json.JSONArray
import org.json.JSONObject

object HiddenWindowsAppPreferencesCodec {
    const val DEFAULT_HIDDEN_PROCESS_NAME = "AudioBridge"

    fun defaultHiddenProcessNames(): Set<String> = setOf(DEFAULT_HIDDEN_PROCESS_NAME)

    fun defaultHiddenWindowsApps(): List<HiddenWindowsApp> = listOf(
        HiddenWindowsApp(
            processName = DEFAULT_HIDDEN_PROCESS_NAME,
            displayName = DEFAULT_HIDDEN_PROCESS_NAME,
            iconBase64 = null,
        ),
    )

    fun encodeHiddenWindowsApps(apps: List<HiddenWindowsApp>): String {
        val array = JSONArray()
        normalizeHiddenWindowsApps(apps).forEach { app ->
            array.put(
                JSONObject()
                    .put("processName", app.processName)
                    .put("displayName", app.displayName)
                    .put("iconBase64", app.iconBase64 ?: JSONObject.NULL),
            )
        }
        return array.toString()
    }

    fun decodeHiddenWindowsApps(json: String?): List<HiddenWindowsApp> {
        if (json.isNullOrBlank()) {
            return emptyList()
        }

        val array = JSONArray(json)
        val apps = mutableListOf<HiddenWindowsApp>()
        for (index in 0 until array.length()) {
            val node = array.optJSONObject(index) ?: continue
            val processName = node.optString("processName").trim()
            if (processName.isBlank()) {
                continue
            }

            apps += HiddenWindowsApp(
                processName = processName,
                displayName = node.optString("displayName").trim().ifBlank { processName },
                iconBase64 = node.optString("iconBase64").takeIf { it.isNotBlank() },
            )
        }

        return normalizeHiddenWindowsApps(apps)
    }

    private fun normalizeHiddenWindowsApps(apps: List<HiddenWindowsApp>): List<HiddenWindowsApp> {
        val merged = linkedMapOf<String, HiddenWindowsApp>()
        apps.forEach { app ->
            val processName = app.processName.trim()
            if (processName.isBlank()) {
                return@forEach
            }

            val incoming = HiddenWindowsApp(
                processName = processName,
                displayName = app.displayName.trim().ifBlank { processName },
                iconBase64 = app.iconBase64?.takeIf { it.isNotBlank() },
            )
            merged[processName] = HiddenWindowsAppSupport.mergeHiddenWindowsApp(merged[processName], incoming)
        }

        return merged.values.sortedBy { it.displayName.lowercase() }
    }
}