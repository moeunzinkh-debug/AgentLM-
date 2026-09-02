package com.example.service.engine

import com.example.model.ChatMessage
import com.example.model.MessageRole
import com.example.model.ResponsePolicy
import kotlinx.coroutines.flow.Flow

/** One turn handed to an engine (system prompt is separate so native engines can use it). */
data class ChatTurn(
    val role: MessageRole,
    val text: String
)

data class EngineRequest(
    val systemPrompt: String,
    val turns: List<ChatTurn>,
    val policy: ResponsePolicy,
    /** Remote model id (Gemini / Ollama tag) or ignored for local engines. */
    val modelId: String,
    /** Absolute path of a locally downloaded weight file, when the engine runs on-device. */
    val localModelPath: String? = null,
    /** Extra instructions for tool-ish behaviour (persona tweaks, file context note). */
    val contextNote: String? = null,
    /** Hard cap on generated length — the main guard against a runaway reply. */
    val maxOutputTokens: Int = 384,
    /** Prompt-side budget (history + attachments), already trimmed by PromptBudget. */
    val contextTokenBudget: Int = 1536,
    /** Base64 image payload for multimodal endpoints (only sent when the model advertises vision). */
    val imageBase64: String? = null,
    val imageMime: String = "image/jpeg",
    val temperature: Double = 0.7,
    val topP: Double = 0.9,
    /** Candidate pool for the sampler; on-device engines honour it directly. */
    val topK: Int = 40,
    val stopSequences: List<String> = emptyList()
)

/**
 * Stream events. Only [Delta] carries *incremental* text — the UI layer coalesces these,
 * which is exactly what stops a per-token recomposition storm from freezing the app.
 */
sealed interface GenEvent {
    data class Delta(val text: String) : GenEvent
    data class Progress(val phase: String, val elapsedMs: Long) : GenEvent
    data class Done(
        val fullText: String,
        val tokensOut: Int,
        val finishReason: String,
        val elapsedMs: Long
    ) : GenEvent

    data class Failed(
        val message: String,
        val hint: String? = null,
        val recoverable: Boolean = true
    ) : GenEvent
}

interface ChatEngine {
    val id: String
    val label: String

    /** Cheap, synchronous check used to grey out an engine in the UI. */
    fun isUsable(): Boolean

    fun stream(request: EngineRequest): Flow<GenEvent>

    /** Used by the Settings "Test connection" button. */
    suspend fun ping(): EnginePing = EnginePing(true, "Ready", null)
}

data class EnginePing(
    val ok: Boolean,
    val message: String,
    val detail: String? = null
)

/** Trims history to the token budget agreed with the device limits. */
object PromptBudget {

    /** ≈4 chars per token for Latin text; Khmer/Devanagari tokenize denser, so we pad. */
    fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        val ascii = text.count { it.code < 0x1000 }
        val dense = text.length - ascii
        return ((ascii / 4.0) + (dense / 2.2)).toInt().coerceAtLeast(1)
    }

    /**
     * Keeps the newest turns that fit in [contextTokenBudget] (output tokens are reserved
     * by the caller). Attachment bodies are truncated here, never the user's own question.
     */
    fun fit(
        history: List<ChatMessage>,
        currentText: String,
        systemPrompt: String,
        contextTokenBudget: Int,
        maxTurns: Int
    ): List<ChatMessage> {
        val reserveForOutput = 64
        var budget = (contextTokenBudget - reserveForOutput).coerceAtLeast(128)
        budget -= estimateTokens(systemPrompt)
        budget -= estimateTokens(currentText)
        if (budget < 64) return emptyList()

        val picked = ArrayList<ChatMessage>()
        var used = 0
        val recent = history.takeLast(maxTurns.coerceAtLeast(0) * 2)
        for (msg in recent.asReversed()) {
            val cost = estimateTokens(msg.content) + 6
            if (used + cost > budget) break
            picked.add(0, msg)
            used += cost
        }
        return picked
    }

    /** Hard character clamp for injected file bodies — the #1 prefill-freeze cause. */
    fun clampBody(text: String, maxChars: Int, label: String): String {
        if (text.length <= maxChars) return text
        val head = text.take(maxChars)
        return "$head\n\n[… $label truncated: ${text.length - maxChars} more characters dropped to protect the context window …]"
    }
}
