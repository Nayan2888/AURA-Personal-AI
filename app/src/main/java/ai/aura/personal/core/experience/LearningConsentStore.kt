package ai.aura.personal.core.experience

import android.content.Context

/**
 * Persists the user's explicit opt-in for AURA learning/experience capture.
 *
 * Learning is disabled by default. Existing experience data is not deleted
 * when consent is revoked; revocation only stops new automatic capture and
 * feedback writes until consent is granted again.
 */
class LearningConsentStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun isGranted(): Boolean = preferences.getBoolean(KEY_GRANTED, false)

    fun setGranted(granted: Boolean) {
        preferences.edit().putBoolean(KEY_GRANTED, granted).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "aura_learning_consent"
        const val KEY_GRANTED = "granted"
    }
}
