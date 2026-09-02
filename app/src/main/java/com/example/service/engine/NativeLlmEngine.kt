package com.example.service.engine

import com.example.model.MessageRole
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

/**
 * On-device inference contract.
 *
 * The real implementation (Google AI Edge **LiteRT-LM**) lives in the *opt-in* source set
 * `app/src/litertlm/java`, so the default build never has to resolve a heavyweight native
 * artifact. Turn it on with:
 *
 * ```
 * gradle assembleDebug -Pagentlm.nativeEngine=true
 * ```
 *
 * The main source set reaches it through this interface plus one reflective lookup, so the
 * app compiles and runs with or without the native runtime — and instead of inventing an
 * answer when nothing can generate one, it reports exactly what is missing.
 */
data class NativeModelSpec(
    val modelPath: String,
    val modelKind: String,
    /** "gpu" or "cpu". Implementations must degrade to CPU when the delegate fails. */
    val backend: String,
    val maxContextTokens: Int,
    val cacheDir: String,
    val systemPrompt: String,
    val temperature: Double,
    val topP: Double,
    val topK: Int,
    val cpuThreads: Int
)

class NativeQuery(
    val prompt: String,
    /** (role "user" | "model", text) pairs, oldest first — empty for a stateless call. */
    val history: List<Pair<String, String>>,
    val maxOutputTokens: Int
)

class NativeOutcome(
    val text: String,
    val tokensOut: Int,
    val finishReason: String
)

interface NativeSession {
    /**
     * Streams the reply: [onDelta] has to be called for every generated chunk while decoding,
     * not once at the end, so the UI can coalesce repaints instead of blocking on the model.
     */
    suspend fun generate(query: NativeQuery, onDelta: suspend (String) -> Unit): NativeOutcome

    fun close()
}

interface NativeLlmBackend {
    val backendName: String
    val versionLabel: String

    fun isAvailable(): Boolean

    /** @throws IllegalStateException when the runtime or weights cannot be loaded. */
    suspend fun load(spec: NativeModelSpec): NativeSession
}

object NativeBackends {

    private const val IMPL = "com.example.litertlm.LiteRtLmBackend"

    @Volatile
    private var cached: NativeLlmBackend? = null

    @Volatile
    private var probed = false

    fun discover(): NativeLlmBackend? {
        if (probed) return cached
        synchronized(this) {
            if (probed) return cached
            val found = try {
                val cls = Class.forName(IMPL)
                val instance = cls.getDeclaredConstructor().newInstance() as? NativeLlmBackend
                instance?.takeIf { it.isAvailable() }
            } catch (e: Throwable) {
                null
            }
            cached = found
            probed = true
            found
        }
    }

    /** Human-readable availability, surfaced in Settings → Inference Engine. */
    fun describe(): String {
        val backend = discover() ?: return "Native runtime not built — run gradle with -Pagentlm.nativeEngine=true"
        return "${backend.backendName} • ${backend.versionLabel}"
    }
}

/**
 * Runs a *downloaded* weight file on the phone through [NativeLlmBackend], with the two
 * robustness tricks that matter most on mobile silicon:
 *  * GPU delegate failure ⇒ automatic retry on CPU;
 *  * a cached session, so 1–3 GB of weights are not re-mapped (multi-second jank) per message,
 *    plus an idle unload so RAM is handed back.
 */
class NativeLlmEngine(
    private val cacheDirProvider: () -> String,
    override val label: String = "On-device LiteRT-LM"
) : ChatEngine {

    override val id: String = "local-litertlm"

    private var session: NativeSession? = null
    private var sessionKey: String = ""
    private val lastUsed = AtomicLong(0L)

    override fun isUsable(): Boolean = NativeBackends.discover() != null

    fun loadedModelPath(): String? = sessionKey.substringBefore('|').takeIf { sessionKey.isNotEmpty() }
    fun isSessionOpen(): Boolean = session != null

    override fun stream(request: EngineRequest): Flow<GenEvent> = flow {
        val backend = NativeBackends.discover()
        val path = request.localModelPath
        if (backend == null) {
            emit(
                GenEvent.Failed(
                    message = "No on-device runtime is compiled into this build.",
                    hint = NativeBackends.describe(),
                    recoverable = true
                )
            )
            return@flow
        }
        if (path.isNullOrBlank()) {
            emit(
                GenEvent.Failed(
                    message = "No downloaded weights selected for on-device inference.",
                    hint = "Open Model Hub → download a file that fits your RAM → press “Use offline”.",
                    recoverable = true
                )
            )
            return@flow
        }

        val startedAt = System.currentTimeMillis()
        emit(GenEvent.Progress("loading-weights", 0))
        val sb = StringBuilder()
        try {
            val active = ensureSession(backend, request, path)
            emit(GenEvent.Progress("decoding", System.currentTimeMillis() - startedAt))

            val outcome = active.generate(
                NativeQuery(
                    prompt = request.turns.lastOrNull()?.text.orEmpty(),
                    history = request.turns.dropLast(1).map { turn ->
                        (if (turn.role == MessageRole.USER) "user" else "model") to turn.text
                    },
                    maxOutputTokens = request.maxOutputTokens
                )
            ) { delta ->
                if (delta.isNotEmpty()) {
                    sb.append(delta)
                    emit(GenEvent.Delta(delta))
                }
            }

            emit(
                GenEvent.Done(
                    fullText = outcome.text.ifBlank { sb.toString() },
                    tokensOut = outcome.tokensOut,
                    finishReason = outcome.finishReason.ifBlank { "stop" },
                    elapsedMs = System.currentTimeMillis() - startedAt
                )
            )
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            if (sb.isNotEmpty()) {
                // Partial output is far more useful than an error banner after 30 s of decoding.
                emit(
                    GenEvent.Done(
                        fullText = sb.toString(),
                        tokensOut = PromptBudget.estimateTokens(sb.toString()),
                        finishReason = "interrupted",
                        elapsedMs = System.currentTimeMillis() - startedAt
                    )
                )
            } else {
                emit(
                    GenEvent.Failed(
                        message = t.message ?: "On-device inference failed",
                        hint = "Try a smaller quantization in Model Hub, or lower Max response tokens in Settings → Response Tuning.",
                        recoverable = true
                    )
                )
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun ensureSession(
        backend: NativeLlmBackend,
        request: EngineRequest,
        path: String
    ): NativeSession {
        val useGpu = request.policy.gpuEnabled
        val threads = request.policy.effectiveThreads(Runtime.getRuntime().availableProcessors())
        val contextBudget = request.contextTokenBudget.coerceAtLeast(512)
        val key = "$path|$useGpu|$contextBudget"

        session?.let { existing ->
            if (sessionKey == key) {
                lastUsed.set(System.currentTimeMillis())
                return existing
            }
            runCatching { existing.close() }
            session = null
        }

        val spec = NativeModelSpec(
            modelPath = path,
            modelKind = path.substringAfterLast('.', "gguf"),
            backend = if (useGpu) "gpu" else "cpu",
            maxContextTokens = contextBudget,
            cacheDir = cacheDirProvider(),
            systemPrompt = request.systemPrompt,
            temperature = request.temperature,
            topP = request.topP,
            topK = 40,
            cpuThreads = threads
        )

        val opened = try {
            backend.load(spec)
        } catch (e: Exception) {
            if (useGpu && request.policy.fallbackGpuToCpu) {
                // Mirrors the "retry on CPU when GPU initialization fails" hardening.
                backend.load(spec.copy(backend = "cpu"))
            } else {
                throw e
            }
        }
        session = opened
        sessionKey = key
        lastUsed.set(System.currentTimeMillis())
        return opened
    }

    fun closeSession() {
        runCatching { session?.close() }
        session = null
        sessionKey = ""
    }

    /** Hands RAM back when the model has been idle for a while. */
    fun maybeRelease(keepAliveSec: Int): Boolean {
        if (session == null) return false
        if (System.currentTimeMillis() - lastUsed.get() > keepAliveSec * 1000L) {
            closeSession()
            return true
        }
        return false
    }

    override suspend fun ping(): EnginePing {
        val backend = NativeBackends.discover()
            ?: return EnginePing(false, NativeBackends.describe(), null)
        return EnginePing(true, "${backend.backendName} ready", "Weights load lazily on the first message.")
    }
}
