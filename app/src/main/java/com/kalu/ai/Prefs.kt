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

    fun putApiKey(value: String) {
        if (value.isBlank()) {
            sp.edit().remove("api").apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val ct = cipher.doFinal(value.trim().toByteArray(Charsets.UTF_8))
        sp.edit().putString("api", Base64.encodeToString(iv + ct, Base64.NO_WRAP)).apply()
    }

    fun getApiKey(): String? = try {
        val raw = sp.getString("api", null) ?: throw IllegalStateException("no key")
        val bytes = Base64.decode(raw, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, 12)
        val ct = bytes.copyOfRange(12, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(ct), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }
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

    fun resetAll() = sp.edit().clear().apply()
}
