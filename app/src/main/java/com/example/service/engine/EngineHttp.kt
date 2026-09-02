package com.example.service.engine

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal object EngineHttp {
    val JSON = "application/json; charset=utf-8".toMediaType()

    /**
     * A client tuned for token streaming: generous read timeout between deltas, no
     * write timeout, and keep-alive so a long decode is never cut off mid-response.
     */
    fun streaming(readTimeoutSec: Int): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(readTimeoutSec.coerceIn(20, 300).toLong(), TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(okhttp3.ConnectionPool(4, 5, TimeUnit.MINUTES))
        .build()

    fun post(url: String, body: JSONObject, apiKey: String?, extra: Map<String, String> = emptyMap()): Request {
        val builder = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON))
            .header("Accept", "text/event-stream, application/x-ndjson, application/json")
            .header("User-Agent", "AgentLM/2.0")
        if (!apiKey.isNullOrBlank()) builder.header("Authorization", "Bearer $apiKey")
        extra.forEach { (k, v) -> builder.header(k, v) }
        return builder.build()
    }
}

/**
 * Reads an SSE (or NDJSON) byte stream and yields one JSON payload per server event.
 * Returns the literal `[DONE]` sentinel when the server signals end-of-stream.
 */
internal class StreamEventReader(private val source: BufferedSource) {

    fun next(): String? {
        val sb = StringBuilder()
        var sawData = false
        while (true) {
            val line = source.readUtf8Line() ?: return if (sawData) sb.toString() else null
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                if (sawData) return sb.toString().trim()
                continue
            }
            if (trimmed.startsWith(":")) continue
            if (trimmed.startsWith("data:")) {
                val payload = trimmed.substring(5).trim()
                if (payload == "[DONE]") return DONE
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append(payload)
                sawData = true
            } else if (trimmed.startsWith("{")) {
                return trimmed
            }
        }
    }

    companion object {
        const val DONE = "[DONE]"
    }
}

/** Small shared helpers for decoding OpenAI-style stream payloads. */
internal object DeltaText {
    fun fromOpenAiChunk(json: JSONObject): Pair<String, String> {
        val choices = json.optJSONArray("choices")
        if (choices == null || choices.length() == 0) return "" to ""
        val choice = choices.getJSONObject(0)
        val delta = choice.optJSONObject("delta")
        var text = delta?.optString("content", "") ?: ""
        if (text.isEmpty()) {
            // Some servers (and non-streaming fallbacks) use message/text fields instead.
            text = choice.optJSONObject("message")?.optString("content", "").orEmpty()
            if (text.isEmpty()) text = choice.optString("text", "")
        }
        if (text == "null") text = ""
        val reason = choice.optString("finish_reason", "")
        return text to reason
    }

    fun fromGeminiChunk(json: JSONObject): Pair<String, String> {
        val candidates = json.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) return "" to ""
        val candidate = candidates.getJSONObject(0)
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
        val sb = StringBuilder()
        if (parts != null) {
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                if (part.has("text")) sb.append(part.optString("text", ""))
            }
        }
        return sb.toString() to candidate.optString("finishReason", "")
    }
}
