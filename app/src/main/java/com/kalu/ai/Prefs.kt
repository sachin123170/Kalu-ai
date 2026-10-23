package com.kalu.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts the Gemini API key with an AES-GCM key that lives inside the Android Keystore. */
class SecureStore(context: Context) {
    private val sp = context.getSharedPreferences("kalu_secure", Context.MODE_PRIVATE)
    private val alias = "kalu_ai_secret_v1"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun putSecret(name: String, value: String) {
        if (value.isBlank()) {
            sp.edit().remove(name).apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val ct = cipher.doFinal(value.trim().toByteArray(Charsets.UTF_8))
        sp.edit().putString(name, Base64.encodeToString(iv + ct, Base64.NO_WRAP)).apply()
    }

    fun getSecret(name: String): String? {
        try {
            val raw = sp.getString(name, null) ?: return null
            val bytes = Base64.decode(raw, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, 12)
            val ct = bytes.copyOfRange(12, bytes.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            return String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (e: Exception) {
            return null
        }
    }

    fun putApiKey(value: String) = putSecret("api", value)

    fun getApiKey(): String? = getSecret("api")
}

/** Non-secret settings. The API key is NOT here; it goes through SecureStore. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("kalu_prefs", Context.MODE_PRIVATE)
    val secure = SecureStore(context)

    var model: String
        get() = sp.getString("model", "gemini-2.5-flash") ?: "gemini-2.5-flash"
        set(v) = sp.edit().putString("model", v.trim()).apply()

    /** friendly | short | detailed */
    var style: String
        get() = sp.getString("style", "friendly") ?: "friendly"
        set(v) = sp.edit().putString("style", v).apply()

    /** hinglish | hindi | english */
    var language: String
        get() = sp.getString("language", "hinglish") ?: "hinglish"
        set(v) = sp.edit().putString("language", v).apply()

    /** balanced | caring | funny | serious | motivational */
    var mood: String
        get() = sp.getString("mood", "balanced") ?: "balanced"
        set(v) = sp.edit().putString("mood", v).apply()

    var speakReplies: Boolean
        get() = sp.getBoolean("speak", false)
        set(v) = sp.edit().putBoolean("speak", v).apply()

    var saveHistory: Boolean
        get() = sp.getBoolean("save_history", true)
        set(v) = sp.edit().putBoolean("save_history", v).apply()

    /** How many previous exchanges are sent to Gemini as context. 0 = none. */
    var memoryTurns: Int
        get() = sp.getInt("memory_turns", 8)
        set(v) = sp.edit().putInt("memory_turns", v).apply()

    var wakeEnabled: Boolean
        get() = sp.getBoolean("wake_on", false)
        set(v) = sp.edit().putBoolean("wake_on", v).apply()

    var wakePhrase: String
        get() = sp.getString("wake_phrase", "kalu") ?: "kalu"
        set(v) = sp.edit().putString("wake_phrase", v.trim()).apply()

    /** Name of the person who built this app; KALU says it when asked who made it. */
    var creatorName: String
        get() = sp.getString("creator", "") ?: ""
        set(v) = sp.edit().putString("creator", v.trim()).apply()

    /** Use Google Search inside Gemini for current facts (news, weather, scores). */
    var webSearch: Boolean
        get() = sp.getBoolean("web_search", true)
        set(v) = sp.edit().putBoolean("web_search", v).apply()

    /** Send the things you asked KALU to remember to Gemini as context. */
    var useMemory: Boolean
        get() = sp.getBoolean("use_memory", true)
        set(v) = sp.edit().putBoolean("use_memory", v).apply()

    var appLock: Boolean
        get() = sp.getBoolean("app_lock", false)
        set(v) = sp.edit().putBoolean("app_lock", v).apply()

    /** In-app consent for reading messaging notifications (also needs Notification access in Android). */
    var notifAccess: Boolean
        get() = sp.getBoolean("notif_access", false)
        set(v) = sp.edit().putBoolean("notif_access", v).apply()

    /** What the assistant is called inside the app (the launcher name is set in strings.xml). */
    var assistantName: String
        get() = sp.getString("assistant_name", "KALU") ?: "KALU"
        set(v) = sp.edit().putString("assistant_name", v.trim().ifEmpty { "KALU" }).apply()

    /** blue | purple | green | orange | pink | amoled */
    var theme: String
        get() = sp.getString("theme", "blue") ?: "blue"
        set(v) = sp.edit().putString("theme", v).apply()

    var wakeEngine: Boolean
        get() = sp.getBoolean("wake_engine", false)
        set(v) = sp.edit().putBoolean("wake_engine", v).apply()

    /** Built-in Porcupine keyword name, e.g. JARVIS. */
    var wakeKeyword: String
        get() = sp.getString("wake_keyword", "JARVIS") ?: "JARVIS"
        set(v) = sp.edit().putString("wake_keyword", v).apply()

    var wakeCustom: Boolean
        get() = sp.getBoolean("wake_custom", false)
        set(v) = sp.edit().putBoolean("wake_custom", v).apply()

    /** If ON, messages to the trusted names below are sent without asking "Pakka?". */
    var skipConfirm: Boolean
        get() = sp.getBoolean("skip_confirm", false)
        set(v) = sp.edit().putBoolean("skip_confirm", v).apply()

    var trustedContacts: String
        get() = sp.getString("trusted", "") ?: ""
        set(v) = sp.edit().putString("trusted", v).apply()

    fun isTrusted(name: String): Boolean {
        if (name.isBlank()) return false
        val n = name.lowercase()
        return trustedContacts.split(",").map { it.trim().lowercase() }.any { it.isNotEmpty() && n.contains(it) }
    }

    fun resetAll() = sp.edit().clear().apply()
}
