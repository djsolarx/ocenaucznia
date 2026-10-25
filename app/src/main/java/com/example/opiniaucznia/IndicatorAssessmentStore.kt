package com.example.opiniaucznia

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Model danych jednego poziomu skali. */
data class RatingLevel(
    val value: Int,
    val description: String
)

/** Wskaźnik wraz z oryginalnym opisem i poziomami skali z dokumentu źródłowego. */
data class AssessmentIndicator(
    val id: String,
    val areaTitle: String,
    val title: String,
    val description: String,
    val levels: List<RatingLevel>,
    val scaleComplete: Boolean
)

/** Stan zapisanej odpowiedzi dla jednego wskaźnika. */
data class IndicatorAnswer(
    val rating: Int? = null,
    val noData: Boolean = false,
    val notes: String = ""
) {
    init {
        require(rating == null || rating in 1..10) { "Ocena musi mieścić się w zakresie 1–10." }
        require(!(noData && rating != null)) { "Wybierz ocenę albo Brak danych, nie oba naraz." }
    }
}

/**
 * Wczytuje katalog z app/src/main/assets/struktura_wskaznikow.json.
 * Sekcje wyłączone przez użytkownika są pomijane na podstawie include_in_requested_app.
 * Zapis odpowiedzi odbywa się lokalnie w SharedPreferences.
 */
class IndicatorAssessmentStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "opinia_ucznia_indicator_answers_v1", Context.MODE_PRIVATE
    )

    fun loadCatalog(context: Context): List<AssessmentIndicator> {
        val jsonText = context.assets.open("struktura_wskaznikow.json")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(jsonText)
        val areas = root.optJSONArray("areas") ?: JSONArray()
        val result = mutableListOf<AssessmentIndicator>()

        for (areaIndex in 0 until areas.length()) {
            val area = areas.optJSONObject(areaIndex) ?: continue
            if (!area.optBoolean("include_in_requested_app", true)) continue
            val areaTitle = area.optString("title", "Obszar bez nazwy")
            val indicators = area.optJSONArray("indicators") ?: JSONArray()

            for (indicatorIndex in 0 until indicators.length()) {
                val item = indicators.optJSONObject(indicatorIndex) ?: continue
                val title = item.optString("title", "Wskaźnik bez nazwy")
                val sourceParagraphs = item.optJSONArray("source_paragraphs") ?: JSONArray()
                val description = extractIndicatorDescription(sourceParagraphs)
                val levelsArray = item.optJSONArray("levels") ?: JSONArray()
                val levels = mutableListOf<RatingLevel>()
                for (levelIndex in 0 until levelsArray.length()) {
                    val level = levelsArray.optJSONObject(levelIndex) ?: continue
                    val value = level.optInt("level", -1)
                    val text = level.optString("description", "").trim()
                    if (value in 1..10 && text.isNotBlank()) levels += RatingLevel(value, text)
                }
                result += AssessmentIndicator(
                    id = item.optString("id").takeIf { it.isNotBlank() } ?: "${areaIndex}_${indicatorIndex}",
                    areaTitle = areaTitle,
                    title = title,
                    description = description,
                    levels = levels.sortedBy { it.value },
                    scaleComplete = (1..10).all { number -> levels.any { it.value == number && it.description.isNotBlank() } }
                )
            }
        }
        return result
    }

    fun getAnswer(indicatorId: String): IndicatorAnswer {
        val json = prefs.getString(indicatorId, null) ?: return IndicatorAnswer()
        return runCatching {
            val obj = JSONObject(json)
            val ratingValue = if (obj.isNull("rating")) null else obj.optInt("rating", -1).takeIf { it in 1..10 }
            IndicatorAnswer(
                rating = ratingValue,
                noData = obj.optBoolean("noData", false) && ratingValue == null,
                notes = obj.optString("notes", "")
            )
        }.getOrDefault(IndicatorAnswer())
    }

    fun saveAnswer(indicatorId: String, answer: IndicatorAnswer) {
        val json = JSONObject().apply {
            if (answer.rating == null) put("rating", JSONObject.NULL) else put("rating", answer.rating)
            put("noData", answer.noData)
            put("notes", answer.notes)
        }
        prefs.edit().putString(indicatorId, json.toString()).apply()
    }

    fun getAllSavedAnswers(): Map<String, IndicatorAnswer> {
        return prefs.all.mapNotNull { (key, value) ->
            if (value !is String) return@mapNotNull null
            val answer = runCatching {
                val obj = JSONObject(value)
                val ratingValue = if (obj.isNull("rating")) null else obj.optInt("rating", -1).takeIf { it in 1..10 }
                IndicatorAnswer(
                    rating = ratingValue,
                    noData = obj.optBoolean("noData", false) && ratingValue == null,
                    notes = obj.optString("notes", "")
                )
            }.getOrNull() ?: return@mapNotNull null
            key to answer
        }.toMap()
    }

    fun clearAnswers() {
        prefs.edit().clear().apply()
    }

    fun replaceAllSavedAnswers(answers: Map<String, IndicatorAnswer>) {
        val editor = prefs.edit().clear()
        answers.forEach { (id, answer) ->
            val json = JSONObject().apply {
                if (answer.rating == null) put("rating", JSONObject.NULL) else put("rating", answer.rating)
                put("noData", answer.noData)
                put("notes", answer.notes)
            }
            editor.putString(id, json.toString())
        }
        editor.apply()
    }

    private fun extractIndicatorDescription(paragraphs: JSONArray): String {
        val values = (0 until paragraphs.length()).mapNotNull { index ->
            paragraphs.optString(index, "").trim().takeIf { it.isNotBlank() }
        }
        val start = values.indexOfFirst {
            it.equals("Opis wskaźnika", ignoreCase = true) ||
                it.startsWith("Opis wskaźnika:", ignoreCase = true)
        }
        if (start < 0) return ""

        val collected = mutableListOf<String>()
        val first = values[start].substringAfter(":", "").trim()
        if (first.isNotBlank() && !first.equals("Opis wskaźnika", ignoreCase = true)) collected += first

        for (index in start + 1 until values.size) {
            val value = values[index]
            if (value.startsWith("Skala oceny", ignoreCase = true) ||
                value.equals("Ocena", ignoreCase = true) ||
                value.equals("Opis poziomu", ignoreCase = true) ||
                value.matches(Regex("^\\d{1,2}\\.?$"))) break
            if (value.startsWith("Wskaźnik", ignoreCase = true)) break
            collected += value
        }
        return collected.joinToString(" ").replace(Regex("\\s+"), " ").trim()
    }
}
