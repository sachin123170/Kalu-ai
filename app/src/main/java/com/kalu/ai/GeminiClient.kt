package com.kalu.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

enum class ErrKind { INVALID_KEY, QUOTA, MODEL, NETWORK, BLOCKED, SERVER, OTHER }

sealed class GeminiResult {
    data class Ok(val text: String) : GeminiResult()
    data class Err(val kind: ErrKind, val message: String) : GeminiResult()
}

data class ModelsResult(val models: List<String>, val error: String?)

/**
 * Minimal Gemini REST client (generativelanguage.googleapis.com, v1beta).
 * The key is sent in the x-goog-api-key header, never in the URL, and never logged.
 * NOTE: shipping a user-entered key in a client app is fine for personal use.
 * A production app should call Gemini through your own backend so keys stay server-side.
 */
object GeminiClient {
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta"

    suspend fun generate(
        apiKey: String,
        model: String,
        system: String,
        turns: List<Pair<String, String>>
    ): GeminiResult = withContext(Dispatchers.IO) {
        try {
            val contents = JSONArray()
            for ((role, text) in turns) {
                contents.put(
                    JSONObject().put("role", role)
                        .put("parts", JSONArray().put(JSONObject().put("text", text)))
                )
            }
            val body = JSONObject()
                .put(
                    "systemInstruction",
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system)))
                )
                .put("contents", contents)
            val name = model.trim().removePrefix("models/")
            val (code, text) = http("POST", "$BASE/models/$name:generateContent", apiKey, body.toString())
            if (code in 200..299) parse(text) else httpError(code, text, name)
        } catch (e: IOException) {
            GeminiResult.Err(ErrKind.NETWORK, "Internet ya network mein problem hai. Connection check karke dobara try karo.")
        } catch (e: Exception) {
            GeminiResult.Err(ErrKind.OTHER, "Unexpected error: ${e.javaClass.simpleName}")
        }
    }

    suspend fun listModels(apiKey: String): ModelsResult = withContext(Dispatchers.IO) {
        try {
            val (code, text) = http("GET", "$BASE/models?pageSize=100", apiKey, null)
            if (code !in 200..299) {
                return@withContext ModelsResult(emptyList(), (httpError(code, text, "") as GeminiResult.Err).message)
            }
            val arr = JSONObject(text).optJSONArray("models") ?: JSONArray()
            val out = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                val methods = m.optJSONArray("supportedGenerationMethods")
                var ok = false
                if (methods != null) for (j in 0 until methods.length()) {
                    if (methods.getString(j) == "generateContent") ok = true
                }
                val n = m.optString("name").removePrefix("models/")
                if (ok && n.startsWith("gemini")) out.add(n)
            }
            ModelsResult(out.sorted(), null)
        } catch (e: IOException) {
            ModelsResult(emptyList(), "Network problem. Internet check karo.")
        } catch (e: Exception) {
            ModelsResult(emptyList(), "Models list parse nahi hui.")
        }
    }

    private fun http(method: String, url: String, key: String, body: String?): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 15000
            c.readTimeout = 60000
            c.setRequestProperty("x-goog-api-key", key)
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return code to text
        } finally {
            c.disconnect()
        }
    }

    private fun parse(text: String): GeminiResult {
        val o = JSONObject(text)
        val cands = o.optJSONArray("candidates")
        if (cands == null || cands.length() == 0) {
            val reason = o.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
            return GeminiResult.Err(
                ErrKind.BLOCKED,
                "Gemini ne is request ka jawab nahi diya" + if (reason.isNotEmpty()) " (reason: $reason)." else "."
            )
        }
        val c = cands.getJSONObject(0)
        val parts = c.optJSONObject("content")?.optJSONArray("parts")
        val sb = StringBuilder()
        if (parts != null) for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
        if (sb.isBlank()) {
            val fr = c.optString("finishReason")
            return GeminiResult.Err(ErrKind.BLOCKED, "Khaali jawab aaya (finishReason: $fr). Sawaal thoda alag tareeke se poochho.")
        }
        return GeminiResult.Ok(sb.toString().trim())
    }

    private fun httpError(code: Int, body: String, model: String): GeminiResult {
        val msg = try {
            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
        } catch (e: Exception) {
            ""
        }
        return when {
            code == 400 && msg.contains("API key", ignoreCase = true) ->
                GeminiResult.Err(ErrKind.INVALID_KEY, "API key galat ya expired hai. Settings mein check karo.")
            code == 400 -> GeminiResult.Err(ErrKind.OTHER, "Request galat gayi (400): $msg")
            code == 401 || code == 403 ->
                GeminiResult.Err(ErrKind.INVALID_KEY, "Key ko permission nahi mili ($code). Key sahi hai? Gemini API enabled hai? $msg")
            code == 404 ->
                GeminiResult.Err(ErrKind.MODEL, "Model '$model' nahi mila. Settings mein 'Models laao' dabake sahi model chuno.")
            code == 429 ->
                GeminiResult.Err(ErrKind.QUOTA, "Quota/limit khatam ho gayi ya bahut zyada requests. Thodi der baad try karo ya plan check karo.")
            code >= 500 ->
                GeminiResult.Err(ErrKind.SERVER, "Gemini server par problem ($code). Thodi der baad try karo.")
            else -> GeminiResult.Err(ErrKind.OTHER, "Error $code: $msg")
        }
    }
}
