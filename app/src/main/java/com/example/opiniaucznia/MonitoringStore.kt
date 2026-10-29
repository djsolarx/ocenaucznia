package com.example.opiniaucznia

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class MonitoringStore(context: Context) {
    private val prefs = context.getSharedPreferences(
        "opinia_ucznia_archive",
        Context.MODE_PRIVATE
    )

    private val archiveKey = "completed_students_v1"

    private fun readArchive(): JSONArray {
        val raw = prefs.getString(archiveKey, "[]") ?: "[]"
        return runCatching { JSONArray(raw) }
            .getOrDefault(JSONArray())
    }

    fun load(studentId: String): StudentMonitoring {
        val archive = readArchive()

        for (i in 0 until archive.length()) {
            val record = archive.optJSONObject(i) ?: continue

            if (record.optString("id") == studentId) {
                return StudentMonitoring.fromJson(
                    record.optJSONObject("monitoring")
                )
            }
        }

        return StudentMonitoring()
    }

    fun save(
        studentId: String,
        monitoring: StudentMonitoring
    ): Boolean {
        if (studentId.isBlank()) return false

        val archive = readArchive()
        var found = false

        for (i in 0 until archive.length()) {
            val record = archive.optJSONObject(i) ?: continue

            if (record.optString("id") == studentId) {
                record.put("monitoring", monitoring.toJson())
                found = true
                break
            }
        }

        if (!found) return false

        return runCatching {
            prefs.edit()
                .putString(archiveKey, archive.toString())
                .commit()
        }.getOrDefault(false)
    }

    fun studentExists(studentId: String): Boolean {
        if (studentId.isBlank()) return false

        val archive = readArchive()

        for (i in 0 until archive.length()) {
            val record = archive.optJSONObject(i) ?: continue
            if (record.optString("id") == studentId) return true
        }

        return false
    }
}
