package com.example.service

import com.example.model.Agent
import com.example.model.Attachment
import com.example.model.ChatMessage
import com.example.model.EngineKind
import com.example.model.EngineProfile
import com.example.model.HFModelConfig
import com.example.model.MessageRole
import com.example.model.ResponsePolicy
import com.example.model.RuntimeSettings
import com.example.service.engine.ChatEngine
import com.example.service.engine.ChatTurn
import com.example.service.engine.EngineRequest
import com.example.service.engine.GeminiEngine
import com.example.service.engine.GenEvent
import com.example.service.engine.NativeLlmEngine
import com.example.service.engine.OpenAiCompatEngine
import com.example.service.engine.PromptBudget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject
import java.io.File

/**
 * Chooses the engine that will answer, builds a RAM-bounded prompt from the conversation, and
 * streams the model's real tokens.
 *
 * There is deliberately **no canned-response path**: if no engine can answer, the user gets a
 * precise error plus a fix suggestion instead of text that was written at build time.
 */
class AiService(
    private val downloads: ModelDownloadManager,
    private val cacheRoot: File
) {

    fun engineFor(profile: EngineProfile): ChatEngine = when (profile.kind) {
        EngineKind.LOCAL_NATIVE -> nativeEngine
        EngineKind.GEMINI -> GeminiEngine(profile.id, profile.label, profile)
        EngineKind.OPENAI_COMPAT -> OpenAiCompatEngine(
            id = profile.id,
            label = profile.label,
            baseUrl = profile.baseUrl,
            apiKey = profile.apiKey.ifBlank { null },
            modelId = profile.modelId.ifBlank { "default" }
        )
    }

    /** One shared native engine so the loaded weights survive across turns. */
    private val nativeEngine: NativeLlmEngine by lazy {
        NativeLlmEngine { cacheDir().absolutePath }
    }

    private fun cacheDir(): File = cacheRoot.apply { if (!exists()) mkdirs() }

    /** Frees multi-hundred-MB of mapped weights (idle unload / app backgrounded). */
    fun releaseNativeSession() {
        nativeEngine.closeSession()
    }

    fun nativeSessionOpen(): Boolean = nativeEngine.isSessionOpen()

    /** Idle unload: gives the mapped weights back so the OS has RAM for the next foreground frame. */
    fun maybeReleaseNative(keepAliveSec: Int): Boolean = nativeEngine.maybeRelease(keepAliveSec)

    /** Engines worth trying, in order: the selected one, then the on-device one, then the rest. */
    fun chain(settings: RuntimeSettings, model: HFModelConfig): List<Pair<EngineProfile, ChatEngine>> {
        val ready = settings.engines.filter { it.isReady }
        val active = settings.activeEngine().let { a -> if (a.isReady) a else null }
        val ordered = ArrayList<EngineProfile>()
        active?.let { ordered.add(it) }
        val local = ready.firstOrNull { it.kind == EngineKind.LOCAL_NATIVE }
        if (local != null && downloads.isDownloaded(model.id) && ordered.none { it.id == local.id }) {
            ordered.add(local)
        }
        ready.forEach { if (!ordered.contains(it)) ordered.add(it) }

        return ordered
            .map { profile -> profile to engineFor(profile) }
            .filter { (_, engine) -> engine.isUsable() }
    }

    /**
     * Streams with automatic engine fallback. A failing engine is only replaced while nothing
     * has been shown yet — never mid-sentence, which would produce a spliced answer.
     */
    fun streamWithFallback(
        agent: Agent,
        model: HFModelConfig,
        history: List<ChatMessage>,
        userPrompt: String,
        attachment: Attachment?,
        settings: RuntimeSettings,
        maxTokens: Int,
        contextBudget: Int
    ): Flow<GenEvent> = flow {
        val attempts = chain(settings, model)
        if (attempts.isEmpty()) {
            emit(noEngineFailure(settings))
            return@flow
        }

        var emittedAnything = false
        var lastFailure: GenEvent.Failed? = null

        for ((index, attempt) in attempts.withIndex()) {
            val (profile, engine) = attempt
            var done = false
            var failure: GenEvent.Failed? = null

            streamReply(agent, model, history, userPrompt, attachment, settings, maxTokens, contextBudget, engine)
                .collect { event ->
                    when (event) {
                        is GenEvent.Delta -> {
                            emittedAnything = true
                            emit(event)
                        }
                        is GenEvent.Progress -> if (!emittedAnything) {
                            emit(GenEvent.Progress("${engine.id}:${event.phase}", event.elapsedMs))
                        } else {
                            emit(event)
                        }
                        is GenEvent.Done -> {
                            done = true
                            emit(event)
                        }
                        is GenEvent.Failed -> {
                            failure = event
                            if (emittedAnything) emit(event)
                        }
                    }
                }

            if (done || emittedAnything) return@flow
            lastFailure = failure ?: lastFailure

            if (index < attempts.size - 1) {
                emit(
                    GenEvent.Progress(
                        "fallback:${attempts[index + 1].first.label}",
                        0
                    )
                )
            }
        }

        emit(lastFailure ?: noEngineFailure(settings))
    }.flowOn(Dispatchers.IO)

    private fun noEngineFailure(settings: RuntimeSettings): GenEvent.Failed {
        val active = settings.activeEngine()
        return GenEvent.Failed(
            message = when (active.kind) {
                EngineKind.LOCAL_NATIVE ->
                    "On-device engine selected, but no runtime/weights are usable in this build."
                EngineKind.GEMINI -> "Gemini is selected but no API key is configured."
                EngineKind.OPENAI_COMPAT -> "No reachable inference endpoint is configured."
            },
            hint = "Open Settings → Inference Engine: paste an API key, or point a base URL at Ollama / " +
                "llama-server / LM Studio on your network, then press Test. AgentLM never fabricates a reply " +
                "when no model can answer.",
            recoverable = true
        )
    }

    fun streamReply(
        agent: Agent,
        model: HFModelConfig,
        history: List<ChatMessage>,
        userPrompt: String,
        attachment: Attachment?,
        settings: RuntimeSettings,
        maxTokens: Int,
        contextBudget: Int,
        engine: ChatEngine? = null
    ): Flow<GenEvent> {
        val chosen = engine ?: engineFor(settings.activeEngine())
        val personaCap = minOf(agent.maxNewTokens, maxTokens)
        val localPath = if (chosen is NativeLlmEngine) downloads.localPathFor(model.id) else null

        val policy = settings.policy
        val trimmed = PromptBudget.fit(
            history = history,
            currentText = userPrompt,
            systemPrompt = agent.systemPrompt,
            contextTokenBudget = contextBudget,
            maxTurns = policy.effectiveHistoryTurns(agent.historyTurns)
        )

        val turns = ArrayList<ChatTurn>()
        for (msg in trimmed) {
            if (msg.content.isBlank()) continue
            turns.add(ChatTurn(msg.role, msg.content))
        }

        val promptText = buildPromptText(userPrompt, attachment, contextBudget)
        turns.add(ChatTurn(MessageRole.USER, promptText))

        // Vision input is only forwarded when the selected weights advertise vision support, so a
        // text-only model never receives a huge base64 blob it will choke on.
        val imageData: String? = if (attachment != null && attachment.isImage &&
            (model.supportsVision || chosen is NativeLlmEngine)
        ) {
            attachment.base64Data?.takeIf { it.isNotBlank() }
        } else {
            null
        }

        val request = EngineRequest(
            systemPrompt = buildSystemPrompt(agent, model, localPath != null),
            turns = turns,
            policy = policy,
            modelId = settings.activeEngine().modelId.ifBlank { model.id },
            localModelPath = localPath,
            contextNote = null,
            imageBase64 = imageData,
            imageMime = attachment?.mimeType?.takeIf { it.isNotBlank() } ?: "image/jpeg",
            temperature = agent.temperature.toDouble(),
            topP = agent.topP.toDouble(),
            maxOutputTokens = personaCap.coerceAtLeast(96),
            contextTokenBudget = contextBudget,
            stopSequences = agent.stopSequences
        )
        return chosen.stream(request)
    }

    private fun buildPromptText(
        userPrompt: String,
        attachment: Attachment?,
        contextBudget: Int
    ): String {
        if (attachment == null) return userPrompt.ifBlank { "Hello" }

        // Attaching a whole repo is the classic way to freeze on-device inference: the prefill
        // blows past the context window and the model burns minutes tokenizing. Cap it hard.
        val bodyBudget = (contextBudget * 2).coerceIn(600, 6_000)
        val parts = ArrayList<String>()
        if (userPrompt.isNotBlank()) parts.add(userPrompt)

        when {
            attachment.isZip -> {
                val sb = StringBuilder()
                sb.append("(Attached archive: ${attachment.name}, ${attachment.formattedSize}, ")
                sb.append("${attachment.zipEntries.size} entries)\n")
                for (entry in attachment.zipEntries.take(40)) {
                    if (entry.isDirectory) continue
                    sb.append("- ${entry.name} (${entry.sizeBytes} B)")
                    if (!entry.isReadable) sb.append(" — ${entry.reason}")
                    sb.append('\n')
                }
                parts.add(PromptBudget.clampBody(sb.toString(), bodyBudget / 2, "archive listing"))
                attachment.extractedText?.let {
                    parts.add(PromptBudget.clampBody(it, bodyBudget, "archive text"))
                }
                if (parts.size == 1) parts.add("Summarize the structure of this archive and call out risks.")
            }

            attachment.isCodeOrText -> {
                val text = attachment.extractedText.orEmpty()
                parts.add(
                    "Attached file `${attachment.name}` (${attachment.formattedSize}):\n```\n" +
                        PromptBudget.clampBody(text, bodyBudget, "file body") + "\n```"
                )
            }

            attachment.isImage -> {
                val note = "Attached image `${attachment.name}` (${attachment.formattedSize})."
                val ocr = attachment.extractedText?.takeIf { it.isNotBlank() }
                parts.add(
                    if (ocr != null) "$note Extracted text:\n${PromptBudget.clampBody(ocr, bodyBudget, "OCR text")}"
                    else "$note Describe what is visible."
                )
            }

            else -> {
                parts.add("Attached file `${attachment.name}` (${attachment.formattedSize}).")
                attachment.extractedText?.let {
                    parts.add(PromptBudget.clampBody(it, bodyBudget, "file body"))
                }
            }
        }

        if (userPrompt.isBlank() && parts.size > 1) {
            parts.add("Please analyze the attachment above.")
        }
        return parts.joinToString("\n\n").ifBlank { "Hello" }
    }

    private fun buildSystemPrompt(agent: Agent, model: HFModelConfig, local: Boolean): String {
        val sb = StringBuilder(agent.systemPrompt)
        sb.append("\n\nRuntime notes:")
        sb.append("\n- You are answering through ${if (local) "an on-device runtime" else "a remote inference endpoint"}.")
        sb.append("\n- Active weights: ${model.name} (${model.id}).")
        if (model.supportsVision && !local) {
            sb.append("\n- The selected repo is multimodal; image bytes are forwarded when the endpoint accepts them.")
        }
        sb.append("\n- Respect the requested length limit. Prefer Markdown, fenced code blocks and short bullets.")
        if (agent.bannedPhrases.isNotEmpty()) {
            sb.append("\n- Never use these phrases: ${agent.bannedPhrases.joinToString(", ")}.")
        }
        return sb.toString()
    }

    /** Lightweight status blob for the Settings screen. */
    fun describeEngine(profile: EngineProfile): JSONObject {
        val engine = engineFor(profile)
        return JSONObject()
            .put("id", engine.id)
            .put("label", engine.label)
            .put("kind", profile.kind.name)
            .put("usable", engine.isUsable())
    }
}
