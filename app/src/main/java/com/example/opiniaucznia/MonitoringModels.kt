package com.example.opiniaucznia

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Dane jednej oceny nauczyciela w konkretnym obszarze.
 * null oznacza brak oceny, a nie zero.
 */
data class MonitoringRating(
    val areaId: String,
    val areaName: String,
    val rating: Int? = null,
    val notObserved: Boolean = false,
    val comment: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("areaId", areaId)
        put("areaName", areaName)
        if (rating == null) put("rating", JSONObject.NULL) else put("rating", rating)
        put("notObserved", notObserved)
        put("comment", comment)
    }

    companion object {
        fun fromJson(json: JSONObject): MonitoringRating {
            val rawRating = if (json.isNull("rating")) {
                null
            } else {
                json.optInt("rating", -1).takeIf { it in 1..5 }
            }

            return MonitoringRating(
                areaId = json.optString("areaId"),
                areaName = json.optString("areaName"),
                rating = rawRating,
                notObserved = json.optBoolean("notObserved", false) && rawRating == null,
                comment = json.optString("comment")
            )
        }
    }
}

/** Wyniki jednej osoby prowadzącej obserwację. */
data class MonitoringTeacher(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val role: String = "",
    val observationDate: String = "",
    val ratings: List<MonitoringRating> = emptyList(),
    val generalComment: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("role", role)
        put("observationDate", observationDate)
        put("ratings", JSONArray().apply {
            ratings.forEach { put(it.toJson()) }
        })
        put("generalComment", generalComment)
    }

    companion object {
        fun fromJson(json: JSONObject): MonitoringTeacher {
            val ratingArray = json.optJSONArray("ratings") ?: JSONArray()

            return MonitoringTeacher(
                id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
                name = json.optString("name"),
                role = json.optString("role"),
                observationDate = json.optString("observationDate"),
                ratings = (0 until ratingArray.length()).mapNotNull { index ->
                    ratingArray.optJSONObject(index)?.let(MonitoringRating::fromJson)
                },
                generalComment = json.optString("generalComment")
            )
        }
    }
}

/** Jeden okres monitorowania ucznia. Można utworzyć dowolną liczbę okresów. */
data class MonitoringPeriod(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val createdAt: String = "",
    val observationAreas: List<String> = emptyList(),
    val cardContent: String = "",
    val teachers: List<MonitoringTeacher> = emptyList(),
    val summary: String = "",
    val recommendations: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("startDate", startDate)
        put("endDate", endDate)
        put("createdAt", createdAt)
        put("observationAreas", JSONArray(observationAreas))
        put("cardContent", cardContent)
        put("teachers", JSONArray().apply {
            teachers.forEach { put(it.toJson()) }
        })
        put("summary", summary)
        put("recommendations", recommendations)
    }

    companion object {
        fun fromJson(json: JSONObject): MonitoringPeriod {
            val areas = json.optJSONArray("observationAreas") ?: JSONArray()
            val teachers = json.optJSONArray("teachers") ?: JSONArray()

            return MonitoringPeriod(
                id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
                title = json.optString("title"),
                startDate = json.optString("startDate"),
                endDate = json.optString("endDate"),
                createdAt = json.optString("createdAt"),
                observationAreas = (0 until areas.length()).mapNotNull { areas.optString(it).takeIf(String::isNotBlank) },
                cardContent = json.optString("cardContent"),
                teachers = (0 until teachers.length()).mapNotNull { index ->
                    teachers.optJSONObject(index)?.let(MonitoringTeacher::fromJson)
                },
                summary = json.optString("summary"),
                recommendations = json.optString("recommendations")
            )
        }
    }
}

/** Wszystkie okresy monitorowania przypisane do jednego rekordu ucznia. */
data class StudentMonitoring(
    val periods: List<MonitoringPeriod> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("periods", JSONArray().apply {
            periods.forEach { put(it.toJson()) }
        })
    }

    companion object {
        fun fromJson(json: JSONObject?): StudentMonitoring {
            if (json == null) return StudentMonitoring()
            val periods = json.optJSONArray("periods") ?: JSONArray()

            return StudentMonitoring(
                periods = (0 until periods.length()).mapNotNull { index ->
                    periods.optJSONObject(index)?.let(MonitoringPeriod::fromJson)
                }
            )
        }
    }
}
