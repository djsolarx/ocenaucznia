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

    /**
     * Zwraca nauczycieli, którzy uczestniczyli już w monitoringu uczniów
     * z tej samej szkoły. Lista jest budowana z zapisanych rekordów,
     * więc nie wymaga osobnej, ręcznie synchronizowanej bazy nauczycieli.
     */
    fun getTeachersForSchool(schoolName: String, schoolTown: String): List<MonitoringTeacher> {
        if (schoolName.isBlank()) return emptyList()

        fun normalize(value: String): String =
            value.trim().replace(Regex("\\s+"), " ").lowercase(java.util.Locale.ROOT)

        val targetSchool = normalize(schoolName)
        val targetTown = normalize(schoolTown)
        val teachers = mutableListOf<MonitoringTeacher>()

        val archive = readArchive()
        for (i in 0 until archive.length()) {
            val record = archive.optJSONObject(i) ?: continue
            val draft = runCatching { JSONObject(record.optString("draft")) }.getOrNull()
                ?: continue
            if (normalize(draft.optString("school")) != targetSchool) continue
            // Jeśli starszy rekord nie ma zapisanej miejscowości, nie odrzucaj go:
            // nazwa szkoły nadal musi być zgodna. Gdy obie miejscowości są znane,
            // wymagaj zgodności, aby nie mieszać szkół o tej samej nazwie.
            val storedTown = normalize(draft.optString("schoolTown"))
            if (targetTown.isNotBlank() && storedTown.isNotBlank() && storedTown != targetTown) continue

            val monitoring = StudentMonitoring.fromJson(record.optJSONObject("monitoring"))
            monitoring.periods.forEach { period -> teachers.addAll(period.teachers) }
        }

        return teachers
            .filter { it.name.isNotBlank() }
            .distinctBy {
                it.name.trim().lowercase(java.util.Locale.ROOT) + "|" +
                    it.role.trim().lowercase(java.util.Locale.ROOT)
            }
            .sortedBy { it.name.lowercase(java.util.Locale.ROOT) }
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
