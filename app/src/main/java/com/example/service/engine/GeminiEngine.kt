package com.example.service.engine

import com.example.model.EngineProfile
import com.example.model.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.CancellationException
import kotlin.coroutines.coroutineContext

/**
 * Real Gemini streaming. The previous implementation fetched the whole answer and then
 * replayed it word-by-word with `delay(20)`; that is fake pacing. Here the reply is read
 * from `streamGenerateContent?alt=sse` as the model actually produces it.
 */
class GeminiEngine(
    override val id: String = "gemini",
    override val label: String = "Google Gemini",
    private val profile: EngineProfile
) : ChatEngine {

    private val client: OkHttpClient by lazy { EngineHttp.streaming(120) }

    private val base: String
        get() = profile.baseUrl.trim().removeSuffix("/").ifBlank {
            "https://generativelanguage.googleapis.com/v1beta"
        }

    private val model: String
        get() = profile.modelId.ifBlank { "gemini-2.5-flash" }

    private fun apiKey(): String = profile.effectiveKey()

    override fun isUsable(): Boolean = apiKey().isNotEmpty()

    private fun keyParam(): String = "key=" + java.net.URLEncoder.encode(apiKey(), "UTF-8")

    override fun stream(request: EngineRequest): Flow<GenEvent> = flow {
        val key = apiKey()
        if (key.isEmpty()) {
            emit(
                GenEvent.Failed(
                    message = "No Gemini API key configured.",
                    hint = "Add a key in Settings → Inference Engine (or pick a local/lan engine). AgentLM never " +
                        "invents an answer when no model is reachable.",
                    recoverable = true
                )
            )
            return@flow
        }

        val startedAt = System.currentTimeMillis()
        val body = buildBody(request)
        val sb = StringBuilder()
        var deltas = 0
        var finishReason = ""
        var blocked = false

        val streamUrl = "$base/models/$model:streamGenerateContent?alt=sse&$keyParam()"
        val call = client.newCall(
            okhttp3.Request.Builder()
                .url(streamUrl)
                .post(body.toString().toRequestBody(EngineHttp.JSON))
                .header("Accept", "text/event-stream")
                .header("User-Agent", "AgentLM/2.0")
                .build()
        )

        val disposal = coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) runCatching { call.cancel() }
        }

        var earlyFailure: GenEvent.Failed? = null
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val errText = runCatching { response.body?.string()?.take(500) }.getOrNull().orEmpty()
                    // No return@flow here: a labelled return from inside `use { }` is a non-local
                    // return through a crossinline parameter, which Kotlin forbids. The flag plus
                    // `break` keeps the same control flow legally.
                    earlyFailure = geminiFailure(response.code, errText)
                } else {
                val source = response.body?.source()
                    ?: throw IOException("Empty response body from Gemini")
                val reader = StreamEventReader(source)
                while (earlyFailure == null) {
                    if (!coroutineContext.isActive) throw CancellationException("cancelled")
                    val payload = reader.next() ?: break
                    if (payload == StreamEventReader.DONE) break
                    val json = runCatching { JSONObject(payload) }.getOrNull() ?: continue
                    if (json.has("error")) {
                        val msg = json.optJSONObject("error")?.optString("message", "Gemini error")
                        earlyFailure = GenEvent.Failed(msg ?: "Gemini error", null, true)
                        break
                    }
                    val (text, reason) = DeltaText.fromGeminiChunk(json)
                    if (reason.isNotBlank()) {
                        finishReason = reason
                        if (reason.equals("SAFETY", true) || reason.equals("BLOCKLIST", true) ||
                            reason.equals("PROHIBITED_CONTENT", true)
                        ) {
                            blocked = true
                        }
                    }
                    if (text.isEmpty()) continue
                    sb.append(text)
                    deltas++
                    emit(GenEvent.Delta(text))
                    if (sb.length >= request.policy.maxResponseChars) {
                        finishReason = "length-cap"
                        break
                    }
                }
                }
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            val text = t.message.orEmpty()
            emit(
                GenEvent.Failed(
                    message = if (text.contains("HTTP 4", true) || text.contains("HTTP 5", true)) text
                    else "Gemini request failed: ${t.javaClass.simpleName}: ${t.message}",
                    hint = when {
                        t is SocketTimeoutException -> "No token within the read window — retry, or lower Max response tokens."
                        t is IOException -> "Network unreachable — check connectivity for generativelanguage.googleapis.com"
                        else -> null
                    },
                    recoverable = true
                )
            )
            return@flow
        } finally {
            disposal?.dispose()
        }

        val elapsed = System.currentTimeMillis() - startedAt
        val pending = earlyFailure
        if (pending != null) {
            if (sb.isEmpty()) {
                emit(pending)
            } else {
                // Tokens already on screen are kept instead of throwing the whole reply away.
                emit(GenEvent.Done(sb.toString(), deltas, "interrupted", elapsed))
            }
            return@flow
        }

        if (sb.isEmpty()) {
            emit(
                GenEvent.Failed(
                    message = if (blocked) "Gemini blocked this prompt (finishReason=$finishReason)."
                    else "Gemini returned no text after $deltas stream events.",
                    hint = if (blocked) "Rephrase the prompt, or switch to a local / uncensored-capable engine in Settings."
                    else "The model may be busy — try again or use another engine.",
                    recoverable = true
                )
            )
            return@flow
        }
        emit(
            GenEvent.Done(
                fullText = sb.toString(),
                tokensOut = PromptBudget.estimateTokens(sb.toString()),
                finishReason = finishReason.ifBlank { "stop" },
                elapsedMs = elapsed
            )
        )
    }.flowOn(Dispatchers.IO)

    private fun geminiFailure(code: Int, body: String): GenEvent.Failed {
        val parsed = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull()
        val msg = parsed?.takeIf { it.isNotBlank() } ?: "Gemini HTTP $code"
        val hint = when (code) {
            400 -> "Bad request — the model id or one of the generation fields is not accepted (often a stale model name)."
            403 -> "The key is not authorized for the Generative Language API. Enable it in Google Cloud and retry."
            429 -> "Quota exceeded (429). Free-tier Gemini keys throttle hard — wait, or use a local engine."
            else -> null
        }
        return GenEvent.Failed(msg, hint, recoverable = code != 400)
    }

    private fun buildBody(request: EngineRequest): JSONObject {
        val contents = JSONArray()
        for (turn in request.turns) {
            if (turn.text.isBlank()) continue
            val role = if (turn.role == MessageRole.USER) "user" else "model"
            contents.put(
                JSONObject()
                    .put("role", role)
                    .put("parts", JSONArray().put(JSONObject().put("text", turn.text)))
            )
        }
        val lastRole = if (contents.length() > 0)
            contents.getJSONObject(contents.length() - 1).optString("role") else ""
        if (lastRole != "user") {
            contents.put(
                JSONObject()
                    .put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", "Please continue.")))
            )
        }

        request.imageBase64?.takeIf { it.isNotBlank() }?.let { b64 ->
            val last = contents.getJSONObject(contents.length() - 1)
            val parts = last.optJSONArray("parts") ?: JSONArray()
            parts.put(
                JSONObject().put(
                    "inline_data",
                    JSONObject()
                        .put("mime_type", request.imageMime)
                        .put("data", b64)
                )
            )
            last.put("parts", parts)
        }

        val generationConfig = JSONObject()
            .put("temperature", request.temperature)
            .put("topP", request.topP)
            .put("maxOutputTokens", request.maxOutputTokens.coerceAtLeast(64))
            .put("candidateCount", 1)

        val root = JSONObject()
            .put("contents", contents)
            .put("generationConfig", generationConfig)
        if (request.systemPrompt.isNotBlank()) {
            root.put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", request.systemPrompt)))
            )
        }
        if (request.stopSequences.isNotEmpty()) {
            root.put("stopSequences", JSONArray(request.stopSequences))
        }
        return root
    }

    override suspend fun ping(): EnginePing {
        val key = apiKey()
        if (key.isEmpty()) return EnginePing(false, "No Gemini API key", null)
        return try {
            val request = okhttp3.Request.Builder()
                .url("$base/models?key=$keyParam()")
                .get()
                .build()
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    val text = resp.body?.string().orEmpty()
                    val has = text.contains(model)
                    EnginePing(
                        ok = true,
                        message = if (has) "Key valid • model $model listed" else "Key valid • $model not in the public list (may still work)",
                        detail = base
                    )
                } else {
                    EnginePing(false, "Gemini HTTP ${resp.code}", base)
                }
            }
        } catch (e: Exception) {
            EnginePing(false, "Unreachable: ${e.message}", base)
        }
    }
}
