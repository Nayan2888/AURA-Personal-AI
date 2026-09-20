package ai.aura.personal.core.research

import android.content.Context

/**
 * Persists explicit user consent for network-backed research.
 *
 * Disabled by default. This consent is independent from learning consent.
 */
class ResearchConsentStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun isGranted(): Boolean = preferences.getBoolean(KEY_GRANTED, false)

    fun setGranted(granted: Boolean) {
        preferences.edit()
            .putBoolean(KEY_GRANTED, granted)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "aura_research_consent"
        const val KEY_GRANTED = "granted"
    }
}
