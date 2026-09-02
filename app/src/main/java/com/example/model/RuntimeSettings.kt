package com.example.model

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Where a real generation request is sent. */
enum class EngineKind(val label: String) {
    /** Any OpenAI-compatible /v1/chat/completions server: Ollama, llama.cpp, LM Studio, vLLM, OpenRouter, HF routers. */
    OPENAI_COMPAT("OpenAI-compatible endpoint"),

    /** Google Generative Language API, token-streamed via SSE. */
    GEMINI("Google Gemini"),

    /** LiteRT-LM native runtime running the *downloaded* weights on this device. */
    LOCAL_NATIVE("On-device (LiteRT-LM)")
}

/**
 * One configured inference endpoint. [baseUrl] + [apiKey] + [modelId] are all the
 * user has to supply for Ollama / LM Studio / llama-server / OpenRouter / Groq.
 */
data class EngineProfile(
    val id: String,
    val label: String,
    val kind: EngineKind,
    val baseUrl: String = "",
    val apiKey: String = "",
    val modelId: String = "",
    val builtIn: Boolean = false
) {
    val isReady: Boolean
        get() = when (kind) {
            EngineKind.LOCAL_NATIVE -> true
            EngineKind.GEMINI -> effectiveKey().isNotEmpty()
            // An OpenAI-compatible server only needs an address; a key is optional (Ollama,
            // LM Studio and llama.cpp usually have none), so the URL alone must count as ready.
            EngineKind.OPENAI_COMPAT -> baseUrl.isNotBlank()
        }

    /** True when the base URL is plain HTTP to a host that is not on this device. */
    val needsCleartext: Boolean
        get() = kind == EngineKind.OPENAI_COMPAT &&
            baseUrl.trim().lowercase().startsWith("http://") &&
            !baseUrl.contains("localhost") && !baseUrl.contains("127.0.0.1") &&
            !baseUrl.contains("10.0.2.2") && !baseUrl.contains("[::1]")

    /** Profile key first, then the value baked in from `.env` at build time. */
    fun effectiveKey(): String {
        val fromProfile = apiKey.trim()
        if (fromProfile.isNotEmpty() && fromProfile != PLACEHOLDER_KEY) return fromProfile
        return buildTimeGeminiKey
    }

    companion object {
        const val PLACEHOLDER_KEY = "MY_GEMINI_API_KEY"

        /**
         * `GEMINI_API_KEY` from `.env`, injected by the Secrets Gradle plugin into BuildConfig.
         * Apps never see `System.getenv`, so this is the only way a build-time key can work.
         */
        val buildTimeGeminiKey: String
            get() = runCatching { com.example.BuildConfig.GEMINI_API_KEY.trim() }.getOrDefault("")

        val GEMINI_BUILTIN = EngineProfile(
            id = "gemini",
            label = "Gemini 2.5 Flash",
            kind = EngineKind.GEMINI,
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            modelId = "gemini-2.5-flash"
        )

        val OLLAMA_LOCAL = EngineProfile(
            id = "ollama",
            label = "Ollama on LAN",
            kind = EngineKind.OPENAI_COMPAT,
            baseUrl = "http://127.0.0.1:11434/v1",
            modelId = "qwen2.5:0.5b"
        )

        val HUGGINGFACE_ROUTER = EngineProfile(
            id = "hf-router",
            label = "Hugging Face Inference",
            kind = EngineKind.OPENAI_COMPAT,
            baseUrl = "https://router.huggingface.co/v1",
            modelId = "Qwen/Qwen2.5-0.5B-Instruct"
        )

        val LOCAL_ENGINE = EngineProfile(
            id = "local-litertlm",
            label = "Downloaded model on-device",
            kind = EngineKind.LOCAL_NATIVE,
            modelId = ""
        )

        // Local first: the app's purpose is running the weights this phone already downloaded.
        val DEFAULTS = listOf(LOCAL_ENGINE, GEMINI_BUILTIN, OLLAMA_LOCAL, HUGGINGFACE_ROUTER)
    }
}

/**
 * Everything that governs *how much* the model generates and *how fast* it is pushed
 * to the UI. Caps here are what prevent the reply from locking the app on a phone.
 */
data class ResponsePolicy(
    /** 0 = derive from measured RAM/CPU (recommended). */
    val maxOutputTokens: Int = 0,
    /** 0 = derive from KV-cache headroom. */
    val contextTokenBudget: Int = 0,
    val historyTurnsOverride: Int = 0,
    val cpuThreads: Int = 0,
    val gpuEnabled: Boolean = true,
    /** Retry on CPU when GPU delegate initialization fails (LiteRT-LM bug class). */
    val fallbackGpuToCpu: Boolean = true,
    val quantization: String = "Q4_K_M",
    /**
     * Sampling overrides, sent per request (no model reload). Negative = follow the active
     * persona, which is what makes Personas and this tab complementary instead of conflicting.
     */
    val temperature: Double = -1.0,
    val topP: Double = -1.0,
    val topK: Int = -1,
    /** UI coalescing: at most one recomposition per interval. */
    val flushIntervalMs: Long = 90L,
    /** Also flush early once this many new chars accumulated. */
    val minFlushChars: Int = 14,
    /** Markdown re-parsing is O(n) per frame; off by default while streaming. */
    val renderMarkdownWhileStreaming: Boolean = false,
    val autoFollowScroll: Boolean = true,
    val followScrollThresholdPx: Int = 180,
    /** Hard stops so a wedged model can never hold the UI hostage. */
    val prefillTimeoutSec: Int = 75,
    val idleTokenTimeoutSec: Int = 12,
    val hardTimeoutSec: Int = 240,
    /** Drop the whole reply into the composer instead of a message when it overflows. */
    val maxResponseChars: Int = 24_000,
    val releaseModelOnBackground: Boolean = true,
    val modelKeepAliveSec: Int = 120,
    val safetyMode: SafetyMode = SafetyMode.BALANCED
) {
    fun effectiveMaxTokens(deviceCap: Int): Int =
        (if (maxOutputTokens > 0) maxOutputTokens else deviceCap).coerceIn(64, 4_096)

    fun effectiveContextBudget(deviceCap: Int): Int =
        (if (contextTokenBudget > 0) contextTokenBudget else deviceCap).coerceIn(256, 16_384)

    fun effectiveHistoryTurns(deviceCap: Int): Int =
        (if (historyTurnsOverride > 0) historyTurnsOverride else deviceCap).coerceIn(0, 24)

    fun effectiveThreads(cores: Int): Int =
        (if (cpuThreads > 0) cpuThreads else (cores / 2).coerceAtLeast(1)).coerceIn(1, 16)
}

enum class SafetyMode(val label: String, val blurb: String) {
    /** Slowest, safest: fewest tokens, no live markdown, big UI intervals. Old / low-RAM phones. */
    SAFE("Safe", "Short answers, minimal UI work — for 3–4 GB devices"),
    BALANCED("Balanced", "Device-adaptive limits with smooth streaming"),
    /** Fastest cadence and longest answers — flagships only. */
    TURBO("Turbo", "Longest answers, 60 ms repaints — flagships")
}

data class RuntimeSettings(
    val policy: ResponsePolicy = ResponsePolicy(),
    val engines: List<EngineProfile> = EngineProfile.DEFAULTS,
    val activeEngineId: String = EngineProfile.LOCAL_ENGINE.id,
    val preferredHfQuant: String = "Q4_K_M",
    val autoApplyDeviceAdvice: Boolean = true
) {
    fun activeEngine(): EngineProfile =
        engines.find { it.id == activeEngineId && it.isReady }
            ?: engines.firstOrNull { it.isReady }
            ?: engines.find { it.id == activeEngineId }
            ?: EngineProfile.DEFAULTS.first()

    fun usableEngines(): List<EngineProfile> = engines.filter { it.isReady }

    fun policyFor(mode: SafetyMode): ResponsePolicy = when (mode) {
        SafetyMode.SAFE -> policy.copy(
            flushIntervalMs = 160L,
            minFlushChars = 28,
            renderMarkdownWhileStreaming = false,
            maxResponseChars = 9_000,
            idleTokenTimeoutSec = 8,
            releaseModelOnBackground = true
        )
        SafetyMode.BALANCED -> policy.copy(
            flushIntervalMs = 90L,
            minFlushChars = 14,
            maxResponseChars = 24_000
        )
        SafetyMode.TURBO -> policy.copy(
            flushIntervalMs = 55L,
            minFlushChars = 6,
            renderMarkdownWhileStreaming = true,
            maxResponseChars = 40_000,
            releaseModelOnBackground = false
        )
    }
}

/**
 * Persists [RuntimeSettings] in SharedPreferences as a single JSON blob and exposes it
 * as a [StateFlow]. No new dependencies (datastore is not enabled in this project).
 */
class RuntimeSettingsRepository private constructor(
    context: Context,
    private val scope: CoroutineScope
) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<RuntimeSettings> = _settings.asStateFlow()

    fun update(transform: (RuntimeSettings) -> RuntimeSettings) {
        _settings.update(transform)
        val next = _settings.value
        scope.launch(Dispatchers.IO) { write(next) }
    }

    private fun read(): RuntimeSettings {
        val raw = prefs.getString(KEY, null) ?: return RuntimeSettings()
        return try {
            decode(JSONObject(raw))
        } catch (e: Exception) {
            RuntimeSettings()
        }
    }

    private fun write(settings: RuntimeSettings) {
        try {
            prefs.edit().putString(KEY, encode(settings).toString()).apply()
        } catch (e: Exception) {
            // Ignore persistence failures; the in-memory value still applies.
        }
    }

    private fun encode(s: RuntimeSettings): JSONObject {
        val p = s.policy
        val policy = JSONObject()
            .put("maxOutputTokens", p.maxOutputTokens)
            .put("contextTokenBudget", p.contextTokenBudget)
            .put("historyTurnsOverride", p.historyTurnsOverride)
            .put("cpuThreads", p.cpuThreads)
            .put("gpuEnabled", p.gpuEnabled)
            .put("fallbackGpuToCpu", p.fallbackGpuToCpu)
            .put("quantization", p.quantization)
            .put("temperature", p.temperature)
            .put("topP", p.topP)
            .put("topK", p.topK)
            .put("flushIntervalMs", p.flushIntervalMs)
            .put("minFlushChars", p.minFlushChars)
            .put("renderMarkdownWhileStreaming", p.renderMarkdownWhileStreaming)
            .put("autoFollowScroll", p.autoFollowScroll)
            .put("prefillTimeoutSec", p.prefillTimeoutSec)
            .put("idleTokenTimeoutSec", p.idleTokenTimeoutSec)
            .put("hardTimeoutSec", p.hardTimeoutSec)
            .put("maxResponseChars", p.maxResponseChars)
            .put("releaseModelOnBackground", p.releaseModelOnBackground)
            .put("modelKeepAliveSec", p.modelKeepAliveSec)
            .put("safetyMode", p.safetyMode.name)

        val engines = JSONArray()
        for (e in s.engines) {
            engines.put(
                JSONObject()
                    .put("id", e.id)
                    .put("label", e.label)
                    .put("kind", e.kind.name)
                    .put("baseUrl", e.baseUrl)
                    .put("apiKey", e.apiKey)
                    .put("modelId", e.modelId)
                    .put("builtIn", e.builtIn)
            )
        }

        return JSONObject()
            .put("policy", policy)
            .put("engines", engines)
            .put("activeEngineId", s.activeEngineId)
            .put("preferredHfQuant", s.preferredHfQuant)
            .put("autoApplyDeviceAdvice", s.autoApplyDeviceAdvice)
    }

    private fun decode(root: JSONObject): RuntimeSettings {
        val defaults = RuntimeSettings()
        val obj = root.optJSONObject("policy")
        val policy = if (obj == null) defaults.policy else {
            defaults.policy.copy(
                maxOutputTokens = obj.optInt("maxOutputTokens", 0),
                contextTokenBudget = obj.optInt("contextTokenBudget", 0),
                historyTurnsOverride = obj.optInt("historyTurnsOverride", 0),
                cpuThreads = obj.optInt("cpuThreads", 0),
                gpuEnabled = obj.optBoolean("gpuEnabled", true),
                fallbackGpuToCpu = obj.optBoolean("fallbackGpuToCpu", true),
                quantization = obj.optString("quantization", "Q4_K_M"),
                temperature = obj.optDouble("temperature", -1.0),
                topP = obj.optDouble("topP", -1.0),
                topK = obj.optInt("topK", -1),
                flushIntervalMs = obj.optLong("flushIntervalMs", 90L),
                minFlushChars = obj.optInt("minFlushChars", 14),
                renderMarkdownWhileStreaming = obj.optBoolean("renderMarkdownWhileStreaming", false),
                autoFollowScroll = obj.optBoolean("autoFollowScroll", true),
                prefillTimeoutSec = obj.optInt("prefillTimeoutSec", 75),
                idleTokenTimeoutSec = obj.optInt("idleTokenTimeoutSec", 12),
                hardTimeoutSec = obj.optInt("hardTimeoutSec", 240),
                maxResponseChars = obj.optInt("maxResponseChars", 24_000),
                releaseModelOnBackground = obj.optBoolean("releaseModelOnBackground", true),
                modelKeepAliveSec = obj.optInt("modelKeepAliveSec", 120),
                safetyMode = runCatching {
                    SafetyMode.valueOf(obj.optString("safetyMode", SafetyMode.BALANCED.name))
                }.getOrDefault(SafetyMode.BALANCED)
            )
        }

        val savedEngines = mutableListOf<EngineProfile>()
        val arr = root.optJSONArray("engines")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                savedEngines.add(
                    EngineProfile(
                        id = e.optString("id"),
                        label = e.optString("label"),
                        kind = runCatching { EngineKind.valueOf(e.optString("kind")) }
                            .getOrDefault(EngineKind.OPENAI_COMPAT),
                        baseUrl = e.optString("baseUrl"),
                        apiKey = e.optString("apiKey"),
                        modelId = e.optString("modelId"),
                        builtIn = e.optBoolean("builtIn", false)
                    )
                )
            }
        }

        // Merge: saved values win, built-ins that were added in newer versions are appended.
        val merged = defaults.engines.map { builtin ->
            savedEngines.find { it.id == builtin.id } ?: builtin
        } + savedEngines.filter { s -> defaults.engines.none { it.id == s.id } }

        return RuntimeSettings(
            policy = policy,
            engines = merged,
            activeEngineId = root.optString("activeEngineId", defaults.activeEngineId),
            preferredHfQuant = root.optString("preferredHfQuant", "Q4_K_M"),
            autoApplyDeviceAdvice = root.optBoolean("autoApplyDeviceAdvice", true)
        )
    }

    companion object {
        private const val PREFS = "agentlm_runtime"
        private const val KEY = "settings_v1"

        @Volatile
        private var instance: RuntimeSettingsRepository? = null

        fun get(context: Context, scope: CoroutineScope): RuntimeSettingsRepository =
            instance ?: synchronized(this) {
                instance ?: RuntimeSettingsRepository(context, scope).also { instance = it }
            }
    }
}
