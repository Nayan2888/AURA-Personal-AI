package ai.aura.personal.core.experience

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Device-local persistence for learning experiences.
 *
 * Only completed interaction outcomes should be written here. An interaction
 * with no assistant result should remain outside the learning dataset.
 */
class ExperienceStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun save(record: ExperienceRecord) {
        require(record.id.isNotBlank()) { "Experience id must not be blank" }

        val records = readRecords()
        val json = record.toJson()

        var replaced = false
        for (index in 0 until records.length()) {
            if (records.getJSONObject(index).optString("id") == record.id) {
                records.put(index, json)
                replaced = true
                break
            }
        }

        if (!replaced) records.put(json)
        preferences.edit().putString(KEY_RECORDS, records.toString()).apply()
    }

    fun load(id: String): ExperienceRecord? {
        if (id.isBlank()) return null

        val records = readRecords()
        for (index in 0 until records.length()) {
            val item = records.getJSONObject(index)
            if (item.optString("id") == id) {
                return item.toExperienceRecordOrNull()
            }
        }
        return null
    }

    fun list(): List<ExperienceRecord> {
        val records = readRecords()
        return buildList {
            for (index in 0 until records.length()) {
                records.getJSONObject(index).toExperienceRecordOrNull()?.let(::add)
            }
        }.sortedByDescending { it.createdAtEpochMs }
    }

    fun delete(id: String) {
        val records = readRecords()
        for (index in records.length() - 1 downTo 0) {
            if (records.getJSONObject(index).optString("id") == id) {
                records.remove(index)
            }
        }
        preferences.edit().putString(KEY_RECORDS, records.toString()).apply()
    }

    private fun readRecords(): JSONArray = runCatching {
        JSONArray(preferences.getString(KEY_RECORDS, "[]"))
    }.getOrElse { JSONArray() }

    private companion object {
        const val PREFERENCES_NAME = "aura_experience_store"
        const val KEY_RECORDS = "records"
    }
}

private fun ExperienceRecord.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("userInput", userInput)
    put("assistantOutput", assistantOutput)
    put("outcome", outcome.name)
    put("createdAtEpochMs", createdAtEpochMs)
    correctedOutput?.let { put("correctedOutput", it) }
}

private fun JSONObject.toExperienceRecordOrNull(): ExperienceRecord? = runCatching {
    ExperienceRecord(
        id = getString("id"),
        userInput = getString("userInput"),
        assistantOutput = getString("assistantOutput"),
        outcome = ExperienceRecord.Outcome.valueOf(getString("outcome")),
        createdAtEpochMs = getLong("createdAtEpochMs"),
        correctedOutput = if (has("correctedOutput")) getString("correctedOutput") else null
    )
}.getOrNull()
