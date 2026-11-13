package ai.aura.personal.core.providers

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.net.URI
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

internal enum class ProviderProtocol(val label: String) {
    OPENAI("OpenAI-compatible"), CLAUDE("Anthropic Messages")
}

internal enum class ProviderPreset(val label: String, val baseUrl: String, val protocol: ProviderProtocol = ProviderProtocol.OPENAI) {
    OPENAI("OpenAI", "https://api.openai.com/v1"),
    GEMINI("Gemini", "https://generativelanguage.googleapis.com/v1beta/openai"),
    CLAUDE("Anthropic", "https://api.anthropic.com/v1", ProviderProtocol.CLAUDE),
    GROQ("Groq", "https://api.groq.com/openai/v1"),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1"),
    CUSTOM("Custom", "")
}

internal data class ProviderProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val protocol: ProviderProtocol,
    val model: String
) {
    fun endpoint(): URI = URI(baseUrl.trimEnd('/')).also {
        require(it.scheme == "https" && it.host != null && it.userInfo == null && it.rawQuery == null && it.rawFragment == null) {
            "Enter an HTTPS API base URL without credentials, query or fragment."
        }
    }
}

internal class ProviderStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("aura_api_providers", Context.MODE_PRIVATE)

    fun profiles(): List<ProviderProfile> = prefs.all.keys.filter { it.startsWith("profile.") }.mapNotNull { key ->
        val record = JSONObject(prefs.getString(key, null) ?: return@mapNotNull null)
        ProviderProfile(key.removePrefix("profile."), record.getString("name"), record.getString("url"),
            ProviderProtocol.valueOf(record.getString("protocol")), record.getString("model"))
    }.sortedBy { it.name.lowercase() }

    fun save(profile: ProviderProfile, newKey: String?) {
        profile.endpoint()
        val old = prefs.getString("profile."+profile.id, null)?.let(::JSONObject)
        val key = newKey?.takeIf { it.isNotBlank() }
        val record = JSONObject()
            .put("name", profile.name).put("url", profile.baseUrl)
            .put("protocol", profile.protocol.name).put("model", profile.model)
        when {
            key != null -> record.put("secret", encrypt(profile, key))
            old != null && old.getString("url") == profile.baseUrl && old.getString("protocol") == profile.protocol.name ->
                record.put("secret", old.getString("secret"))
            else -> error("Enter an API key for this endpoint.")
        }
        check(prefs.edit().putString("profile."+profile.id, record.toString()).commit()) { "Could not save the API profile." }
    }

    fun remove(profile: ProviderProfile) {
        check(prefs.edit().remove("profile."+profile.id).commit()) { "Could not remove the API profile." }
    }

    fun key(profile: ProviderProfile): String {
        val record = JSONObject(checkNotNull(prefs.getString("profile."+profile.id, null)) { "API profile was removed." })
        check(record.getString("url") == profile.baseUrl && record.getString("protocol") == profile.protocol.name) {
            "API profile changed. Select it again."
        }
        val parts = record.getString("secret").split('.')
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        cipher.updateAAD(binding(profile))
        return cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }

    private fun encrypt(profile: ProviderProfile, key: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        cipher.updateAAD(binding(profile))
        val ciphertext = cipher.doFinal(key.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." + Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    }

    private fun binding(profile: ProviderProfile) =
        listOf(profile.id, profile.baseUrl, profile.protocol.name).joinToString("\n").toByteArray(Charsets.UTF_8)

    private fun keystoreKey(): SecretKey {
        val alias = "aura_provider_key_v1"
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }
}
