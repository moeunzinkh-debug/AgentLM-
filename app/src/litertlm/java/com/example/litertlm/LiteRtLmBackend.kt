package com.example.litertlm

import android.util.Log
import com.example.service.engine.NativeLlmBackend
import com.example.service.engine.NativeModelSpec
import com.example.service.engine.NativeOutcome
import com.example.service.engine.NativeQuery
import com.example.service.engine.NativeSession
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File

/**
 * Real on-device inference through **Google AI Edge LiteRT-LM** (the same runtime the
 * PrivateLM client uses for its LiteRT path).
 *
 * This file is only compiled when the build is configured with:
 *
 * ```
 * gradle assembleDebug -Pagentlm.nativeEngine=true
 * ```
 *
 * which additionally pulls `com.google.ai.edge.litertlm:litertlm-android`. It is discovered at
 * runtime by [com.example.service.engine.NativeBackends] through a single reflective lookup, so
 * the default APK keeps working (and stays slim) while this build variant runs the weights the
 * user downloaded in Model Hub - entirely offline.
 */
class LiteRtLmBackend : NativeLlmBackend {

    override val backendName: String = "LiteRT-LM (Google AI Edge)"
    override val versionLabel: String = "litertlm-android"

    override fun isAvailable(): Boolean = true

    override suspend fun load(spec: NativeModelSpec): NativeSession {
        val file = File(spec.modelPath)
        if (!file.exists()) {
            throw IllegalStateException("Weight file is missing on disk: ${file.name}")
        }

        val requestedGpu = spec.backend.equals("gpu", ignoreCase = true)
        val backend: Backend = try {
            if (requestedGpu) Backend.GPU() else Backend.CPU()
        } catch (t: Throwable) {
            Log.w(TAG, "GPU backend unavailable, falling back to CPU", t)
            Backend.CPU()
        }

        val engine = Engine(
            EngineConfig(
                modelPath = spec.modelPath,
                backend = backend,
                cacheDir = spec.cacheDir,
                maxNumTokens = spec.maxContextTokens
            )
        )
        try {
            engine.initialize()
        } catch (t: Throwable) {
            runCatching { engine.close() }
            if (requestedGpu) {
                // Same recovery as "LiteRT loading now retries on CPU when GPU init fails".
                Log.w(TAG, "engine.initialize() failed on GPU, retrying on CPU", t)
                return load(spec.copy(backend = "cpu"))
            }
            throw IllegalStateException(
                "Could not load ${file.name}: ${t.message}. Try a smaller quantization or free some RAM."
            )
        }

        val conversation = try {
            engine.createConversation(spec.toConversationConfig())
        } catch (t: Throwable) {
            runCatching { engine.close() }
            throw IllegalStateException("Conversation setup failed: ${t.message}", t)
        }
        return LiteRtSession(engine, conversation, activeBackend = if (requestedGpu) "gpu" else "cpu")
    }

    private fun NativeModelSpec.toConversationConfig(): ConversationConfig {
        var config = ConversationConfig()
        if (systemPrompt.isNotBlank()) {
            config = config.copy(systemInstruction = Contents.of(systemPrompt))
        }
        config = config.copy(
            samplerConfig = SamplerConfig(
                topK = topK,
                topP = topP,
                temperature = temperature
            )
        )
        return config
    }

    private class LiteRtSession(
        private val engine: Engine,
        private val conversation: Conversation,
        val activeBackend: String
    ) : NativeSession {

        private var turns = 0

        override suspend fun generate(
            query: NativeQuery,
            onDelta: suspend (String) -> Unit
        ): NativeOutcome {
            // The Conversation object keeps multi-turn state for free after the first exchange.
            // For a freshly (re)loaded session we replay the trimmed history as plain context,
            // which is cheaper and safer than re-prefilling the whole chat every turn.
            val prompt = if (turns == 0 && query.history.isNotEmpty()) {
                val sb = StringBuilder()
                for ((role, text) in query.history) {
                    sb.append(if (role == "user") "User: " else "Assistant: ").append(text).append('\n')
                }
                sb.append("User: ").append(query.prompt).append("\nAssistant:")
                sb.toString()
            } else {
                query.prompt
            }
            turns++

            val builder = StringBuilder()
            var chunks = 0
            val flow = conversation.sendMessageAsync(Contents.of(prompt))
            flow.collect { message: Message ->
                val text = message.contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString("") { it.text }
                if (text.isNotEmpty()) {
                    builder.append(text)
                    chunks++
                    onDelta(text)
                }
            }
            return NativeOutcome(
                text = builder.toString(),
                tokensOut = chunks,
                finishReason = "stop"
            )
        }

        override fun close() {
            runCatching { conversation.close() }
            runCatching { engine.close() }
        }
    }

    companion object {
        private const val TAG = "LiteRtLmBackend"
    }
}
