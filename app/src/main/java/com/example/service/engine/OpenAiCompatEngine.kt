package com.example.service.engine

import com.example.model.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.CancellationException
import kotlin.coroutines.coroutineContext

/**
 * Streams from **any** OpenAI-compatible server: Ollama, llama.cpp's `llama-server`,
 * LM Studio, vLLM, Jan, OpenRouter, Groq, the Hugging Face router… Text arrives as real
 * model deltas (`choices[].delta.content`) — nothing in this path is simulated.
 */
class OpenAiCompatEngine(
    override val id: String,
    override val label: String,
    private val baseUrl: String,
    private val apiKey: String?,
    private val modelId: String
) : ChatEngine {

    private val client: OkHttpClient by lazy { EngineHttp.streaming(150) }

    private val completionsUrl: String
        get() {
            val base = baseUrl.trim().removeSuffix("/").ifBlank { "http://127.0.0.1:11434/v1" }
            return if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
        }

    private val modelsUrl: String
        get() {
            val base = baseUrl.trim().removeSuffix("/").ifBlank { "http://127.0.0.1:11434/v1" }
            return if (base.endsWith("/models")) base else "${base.removeSuffix("/v1")}/models"
        }

    override fun isUsable(): Boolean = baseUrl.isNotBlank()

    override fun stream(request: EngineRequest): Flow<GenEvent> = flow {
        if (baseUrl.isBlank()) {
            emit(
                GenEvent.Failed(
                    message = "No server URL is configured for “$label”.",
                    hint = "Open Settings → Inference Engine and set a base URL, e.g. http://192.168.1.20:11434/v1",
                    recoverable = true
                )
            )
            return@flow
        }

        val startedAt = System.currentTimeMillis()
        emit(GenEvent.Progress("connecting", 0))

        var outcome: StreamOutcome? = null
        var failure: GenEvent.Failed? = null
        try {
            outcome = streamOnce(buildBody(request, stream = true), request.policy.maxResponseChars) { delta ->
                emit(GenEvent.Delta(delta))
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            val http = t as? HttpStreamException
            if (http != null && http.streamUnsupported) {
                // Older llama.cpp builds / strict proxies reject `stream: true`: retry once
                // with a single non-streamed completion instead of leaving a dead chat.
                try {
                    outcome = completeOnce(buildBody(request, stream = false))
                } catch (t2: Throwable) {
                    if (t2 is CancellationException) throw t2
                    failure = failureOf(t2, http)
                }
            } else {
                failure = failureOf(t, http)
            }
        }

        if (outcome == null) {
            failure?.let { emit(it) }
            return@flow
        }

        val result: StreamOutcome = outcome ?: return@flow
        val elapsed = System.currentTimeMillis() - startedAt
        if (result.text.isEmpty()) {
            emit(
                GenEvent.Failed(
                    message = "The model closed the stream after ${result.deltaCount} empty events.",
                    hint = "Try another model tag, or lower “Max response tokens” in Settings → Response Tuning.",
                    recoverable = true
                )
            )
            return@flow
        }
        emit(
            GenEvent.Done(
                fullText = result.text,
                tokensOut = result.tokensEstimate,
                finishReason = result.finishReason.ifBlank { if (result.capped) "length-cap" else "stop" },
                elapsedMs = elapsed
            )
        )
    }.flowOn(Dispatchers.IO)


    private fun failureOf(error: Throwable?, http: HttpStreamException?): GenEvent.Failed {
        val msg = error?.message ?: "Unknown transport error"
        val hint = when {
            http != null && http.code == 401 ->
                "The API key was rejected (401). Re-enter it in Settings → Inference Engine."
            http != null && http.code == 403 ->
                "Access denied (403). For Hugging Face you need a read token with Inference Providers enabled."
            http != null && http.code == 404 ->
                "Model “$modelId” was not found on that server (404). Check the exact model tag (e.g. qwen2.5:0.5b)."
            http != null && http.code in 500..599 ->
                "Server error ${http.code}. If this is llama.cpp/Ollama, the context you asked for may exceed the server's -c setting."
            error is SocketTimeoutException ->
                "No token arrived in time — the server is busy, or the model is too large to load over Wi-Fi."
            error is IOException ->
                "Cannot reach $completionsUrl from the phone. A localhost URL only works when the server runs on " +
                    "this same device — use the computer's LAN address and bind it to 0.0.0.0."
            else -> null
        }
        return GenEvent.Failed(message = msg, hint = hint, recoverable = true)
    }

    private data class StreamOutcome(
        val text: String,
        val deltaCount: Int,
        val finishReason: String,
        val capped: Boolean,
        val tokensEstimate: Int
    )

    /**
     * Blocking SSE reader that runs on the IO dispatcher. Every delta is pushed through
     * [onDelta] immediately — the UI layer decides how often to repaint (see ResponseStreamer).
     */
    private suspend fun streamOnce(
        body: JSONObject,
        charCap: Int,
        onDelta: suspend (String) -> Unit
    ): StreamOutcome {
        val request = EngineHttp.post(completionsUrl, body, apiKey)
        val call = client.newCall(request)
        val disposal = coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) runCatching { call.cancel() }
        }
        val sb = StringBuilder()
        var deltas = 0
        var finishReason = ""
        var capped = false
        try {
            call.execute().use { response ->
                val source = response.body?.source()
                if (!response.isSuccessful) {
                    val errBody = runCatching { response.body?.string()?.take(600) }.getOrNull().orEmpty()
                    throw HttpStreamException(
                        code = response.code,
                        body = errBody,
                        message = "HTTP ${response.code} from ${response.request.url.host}"
                    )
                }
                if (source == null) throw HttpStreamException(response.code, "", "Empty response body")
                val reader = StreamEventReader(source)
                while (true) {
                    if (!coroutineContext.isActive) throw CancellationException("cancelled")
                    val payload = reader.next() ?: break
                    if (payload == StreamEventReader.DONE) break
                    val json = runCatching { JSONObject(payload) }.getOrNull() ?: continue
                    json.optJSONObject("error")?.let { err ->
                        throw HttpStreamException(200, payload, err.optString("message", "stream error"))
                    }
                    val (text, reason) = DeltaText.fromOpenAiChunk(json)
                    if (reason.isNotEmpty()) finishReason = reason
                    if (text.isEmpty()) continue
                    sb.append(text)
                    deltas++
                    onDelta(text)
                    if (charCap > 0 && sb.length >= charCap) {
                        capped = true
                        break
                    }
                }
            }
        } finally {
            disposal?.dispose()
        }
        if (capped && finishReason.isEmpty()) finishReason = "length-cap"
        return StreamOutcome(sb.toString(), deltas, finishReason, capped, PromptBudget.estimateTokens(sb.toString()))
    }

    private suspend fun completeOnce(body: JSONObject): StreamOutcome {
        val request = EngineHttp.post(completionsUrl, body, apiKey)
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw HttpStreamException(response.code, raw.take(600), "HTTP ${response.code}")
            }
            val json = JSONObject(raw)
            json.optJSONObject("error")?.let { throw HttpStreamException(200, raw, it.optString("message", "error")) }
            val (text, reason) = DeltaText.fromOpenAiChunk(json)
            return StreamOutcome(text, 1, reason, false, PromptBudget.estimateTokens(text))
        }
    }

    private fun buildBody(request: EngineRequest, stream: Boolean): JSONObject {
        val messages = JSONArray()
        if (request.systemPrompt.isNotBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", request.systemPrompt))
        }
        for (turn in request.turns) {
            if (turn.text.isBlank()) continue
            val role = when (turn.role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "assistant"
                MessageRole.SYSTEM -> "system"
            }
            messages.put(JSONObject().put("role", role).put("content", turn.text))
        }
        if (messages.length() == 0) {
            messages.put(JSONObject().put("role", "user").put("content", "Hello"))
        }

        // Multimodal: OpenAI-compatible servers (Ollama, LM Studio, OpenRouter, llama.cpp
        // vision builds) want content as an array with a data: URL for the image part.
        val image = request.imageBase64
        if (!image.isNullOrBlank()) {
            val last = messages.getJSONObject(messages.length() - 1)
            if (last.optString("role") == "user") {
                val textPart = JSONObject()
                    .put("type", "text")
                    .put("text", last.optString("content", "Describe this image."))
                val imageUrl = JSONObject()
                    .put("url", "data:${request.imageMime};base64,$image")
                val imagePart = JSONObject()
                    .put("type", "image_url")
                    .put("image_url", imageUrl)
                last.put("content", JSONArray().put(textPart).put(imagePart))
            }
        }

        val body = JSONObject()
            .put("model", modelId)
            .put("messages", messages)
            .put("stream", stream)
            .put("temperature", request.temperature)
            .put("top_p", request.topP)
            .put("max_tokens", request.maxOutputTokens.coerceAtLeast(32))
        // Ollama / llama.cpp / vLLM accept top_k; the strict OpenAI endpoint does not need it,
        // so it is only sent when the user actually moved away from the default.
        if (request.topK > 0 && request.topK != 40) {
            body.put("top_k", request.topK)
        }
        // Ollama / llama.cpp servers accept an `options` object; sending it only to the local
        // ports keeps strict OpenAI-compatible endpoints untouched. This is the same thread cap
        // the reference app applies in-process, expressed over HTTP.
        val localServer = baseUrl.contains(":11434") || baseUrl.contains(":8080") ||
            baseUrl.contains(":1234") || baseUrl.contains("localhost", true) ||
            baseUrl.contains("127.0.0.1") || baseUrl.contains("ollama", true)
        if (localServer && request.cpuThreads > 0) {
            body.put("options", JSONObject().put("num_thread", request.cpuThreads))
        }
        if (request.stopSequences.isNotEmpty()) {
            body.put("stop", JSONArray(request.stopSequences))
        }
        return body
    }

    override suspend fun ping(): EnginePing {
        if (baseUrl.isBlank()) return EnginePing(false, "No base URL set", null)
        return try {
            val request = okhttp3.Request.Builder()
                .url(modelsUrl)
                .apply { if (!apiKey.isNullOrBlank()) header("Authorization", "Bearer $apiKey") }
                .header("User-Agent", "AgentLM/2.0")
                .get()
                .build()
            client.newCall(request).execute().use { resp ->
                when {
                    resp.isSuccessful -> {
                        val text = resp.body?.string().orEmpty()
                        val count = runCatching {
                            JSONObject(text).optJSONArray("data")?.length() ?: 0
                        }.getOrDefault(0)
                        EnginePing(true, "Reachable • $count model(s) listed", completionsUrl)
                    }
                    resp.code == 401 || resp.code == 403 -> EnginePing(
                        false,
                        "Auth rejected (HTTP ${resp.code})",
                        "Check the API key configured for this endpoint."
                    )
                    else -> EnginePing(
                        false,
                        "HTTP ${resp.code}",
                        "Host answered but /models is unavailable — chat may still work."
                    )
                }
            }
        } catch (e: Exception) {
            EnginePing(false, "Unreachable: ${e.message ?: "connection failed"}", completionsUrl)
        }
    }
}

internal class HttpStreamException(
    val code: Int,
    val body: String,
    message: String
) : IOException(message) {
    /** 400/422 (or a body complaining about streaming) means "retry without SSE". */
    val streamUnsupported: Boolean
        get() = code == 400 || code == 422 ||
            (code in 200..299 && body.contains("stream", ignoreCase = true))
}
