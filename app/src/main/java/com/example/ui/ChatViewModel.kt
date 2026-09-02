package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.Agent
import com.example.model.AgentCatalog
import com.example.model.Attachment
import com.example.model.BudgetAdvice
import com.example.model.ChatMessage
import com.example.model.ChatSession
import com.example.model.DeviceSpecs
import com.example.model.DownloadStatus
import com.example.model.EngineProfile
import com.example.model.HFModelConfig
import com.example.model.HardwareInfo
import com.example.model.MessageRole
import com.example.model.MessageStatus
import com.example.model.ModelCatalog
import com.example.model.ModelDownloadProgress
import com.example.model.ResponseBudgetAdvisor
import com.example.model.ResponsePolicy
import com.example.model.RuntimeSettings
import com.example.model.RuntimeSettingsRepository
import com.example.model.SafetyMode
import com.example.service.AiService
import com.example.service.ChatHistoryStore
import com.example.service.HfModelSearchService
import com.example.service.HfRemoteFile
import com.example.service.ModelDownloadManager
import com.example.service.ResponseStreamer
import com.example.service.engine.NativeBackends
import com.example.util.FileAttachmentHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * Owns the chat, the real downloads and — most importantly — the *freeze-proof* streaming path.
 *
 * While a reply is being generated, only [streamingText] / [streamStats] change. The message list
 * is written exactly twice per turn (start + commit), so the LazyColumn never rebuilds on every
 * token. See [ResponseStreamer] for the coalescing and timeout rules.
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Application = application

    /** Downloads must survive the ViewModel/UI, so they run in their own supervisor scope. */
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val settingsRepository = RuntimeSettingsRepository.get(app, viewModelScope)
    private val downloads = ModelDownloadManager(app, downloadScope)
    private val history = ChatHistoryStore(app)
    private val hfSearchService = HfModelSearchService()
    private val aiService = AiService(downloads, File(app.cacheDir, "agentlm_tmp"))
    private val streamer = ResponseStreamer()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    /** The live bubble text. Updated on a time budget — never per token. */
    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    private val _streamStats = MutableStateFlow<ResponseStreamer.Stats?>(null)
    val streamStats: StateFlow<ResponseStreamer.Stats?> = _streamStats.asStateFlow()

    // Cheap first value so ViewModel creation never touches /proc or StatFs on the main thread;
    // the full measurement follows on a worker dispatcher in init.
    private val _hardware = MutableStateFlow(HardwareInfo.probeLight(app))
    val hardware: StateFlow<HardwareInfo> = _hardware.asStateFlow()

    private val _deviceSpecs = MutableStateFlow(ModelCatalog.deviceSpecsFrom(_hardware.value))
    val deviceSpecs: StateFlow<DeviceSpecs> = _deviceSpecs.asStateFlow()

    private val _budgetAdvice = MutableStateFlow(
        ResponseBudgetAdvisor.advise(_hardware.value, ModelCatalog.DEFAULT_MODEL)
    )
    val budgetAdvice: StateFlow<BudgetAdvice> = _budgetAdvice.asStateFlow()

    private val _currentAgent = MutableStateFlow<Agent>(AgentCatalog.AGENTS.first())
    val currentAgent: StateFlow<Agent> = _currentAgent.asStateFlow()

    private val _currentModel = MutableStateFlow<HFModelConfig>(ModelCatalog.DEFAULT_MODEL)
    val currentModel: StateFlow<HFModelConfig> = _currentModel.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _selectedAttachment = MutableStateFlow<Attachment?>(null)
    val selectedAttachment: StateFlow<Attachment?> = _selectedAttachment.asStateFlow()

    private val _runtimeSettings = settingsRepository.settings
    val runtimeSettings: StateFlow<RuntimeSettings> = _runtimeSettings

    /** Derived from the policy so the existing GPU/CPU settings UI keeps working. */
    private val _isGpuEnabled = MutableStateFlow(true)
    val isGpuEnabled: StateFlow<Boolean> = _isGpuEnabled.asStateFlow()

    private val _cpuThreads = MutableStateFlow(4)
    val cpuThreads: StateFlow<Int> = _cpuThreads.asStateFlow()

    private val _lastFinishReason = MutableStateFlow<String?>(null)
    val lastFinishReason: StateFlow<String?> = _lastFinishReason.asStateFlow()

    private val _cacheSizeBytes = MutableStateFlow(0L)
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

    private val _modelStorageBytes = MutableStateFlow(0L)
    val modelStorageBytes: StateFlow<Long> = _modelStorageBytes.asStateFlow()

    private val _chatSessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val chatSessions: StateFlow<List<ChatSession>> = _chatSessions.asStateFlow()

    val downloadStates: StateFlow<Map<String, ModelDownloadProgress>> = downloads.states

    private val _resolvedFiles = MutableStateFlow<Map<String, HfRemoteFile>>(emptyMap())
    val resolvedFiles: StateFlow<Map<String, HfRemoteFile>> = _resolvedFiles.asStateFlow()

    private val _hfSearchResults = MutableStateFlow<List<HFModelConfig>>(emptyList())
    val hfSearchResults: StateFlow<List<HFModelConfig>> = _hfSearchResults.asStateFlow()

    private val _isSearchingHf = MutableStateFlow(false)
    val isSearchingHf: StateFlow<Boolean> = _isSearchingHf.asStateFlow()

    private val _enginePing = MutableStateFlow<Map<String, EnginePingResult>>(emptyMap())
    val enginePing: StateFlow<Map<String, EnginePingResult>> = _enginePing.asStateFlow()

    data class EnginePingResult(
        val ok: Boolean,
        val message: String,
        val detail: String?,
        val at: Long = System.currentTimeMillis()
    )

    val nativeEngineSummary: String get() = NativeBackends.describe()

    private var searchJob: Job? = null
    private var activeJob: Job? = null

    /** Guards against a cancelled/stale generation writing into a *new* chat. */
    private var generationSerial = 0

    init {
        downloads.reconcile()
        viewModelScope.launch {
            _runtimeSettings.collect { settings ->
                _isGpuEnabled.value = settings.policy.gpuEnabled
                _cpuThreads.value = settings.policy.resolvedThreads(
                    cores = _hardware.value.cores,
                    usingGpu = settings.policy.gpuEnabled && _hardware.value.hasVulkanCompute,
                    forceSingleThread = _budgetAdvice.value.singleThreadGuard
                )
            }
        }
        viewModelScope.launch {
            _chatSessions.value = history.loadSessions()
            val draft = history.loadDraft()
            if (draft.isNotEmpty() && _messages.value.isEmpty()) _messages.value = draft
            measureCache()
            // Re-measure hardware once layout settles: RAM pressure changes through the day.
            reprobeHardware()
        }
        startIdleReleaseWatch()
    }

    // --------------------------------------------------------- lifecycle helpers ----

    /** Called from MainActivity.onStop(): the UI is gone, so heavy idle state must go too. */
    /**
     * Called from `Activity.onStop`. Unmapping a loaded model releases real memory pressure but
     * takes hundreds of milliseconds in native code — doing that here, on the main thread, is
     * exactly the kind of "phone hangs when I leave the app" report to avoid at all costs.
     */
    fun onEnterBackground() {
        val policy = _runtimeSettings.value.policy
        if (policy.releaseModelOnBackground && !_isStreaming.value) {
            viewModelScope.launch(Dispatchers.IO) { aiService.releaseNativeSession() }
        }
    }

    fun onEnterForeground() {
        reprobeHardware()
    }

    /** Periodic idle-unload check (keep-alive window from Settings → Response Tuning). */
    private fun startIdleReleaseWatch() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                delay(30_000)
                if (_isStreaming.value) continue
                val policy = _runtimeSettings.value.policy
                // closeSession() is native teardown: it must never run on Main, or the idle
                // release itself becomes the freeze it was meant to prevent.
                aiService.maybeReleaseNative(policy.modelKeepAliveSec)
            }
        }
    }

    // ------------------------------------------------------------------ hardware ----

    fun reprobeHardware() {
        viewModelScope.launch(Dispatchers.Default) {
            val fresh = HardwareInfo.probe(app)
            _hardware.value = fresh
            _deviceSpecs.value = ModelCatalog.deviceSpecsFrom(fresh)
            refreshAdvice()
        }
    }

    private fun refreshAdvice() {
        _budgetAdvice.value = ResponseBudgetAdvisor.advise(_hardware.value, _currentModel.value)
    }

    /** Real numbers: actual cache tree + bytes of downloaded weights. */
    private fun measureCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val cacheBytes = try {
                app.cacheDir.walkBottomUp().sumOf { if (it.isFile) it.length() else 0L }
            } catch (e: Exception) {
                0L
            }
            _cacheSizeBytes.value = cacheBytes
            _modelStorageBytes.value = downloads.totalModelBytesOnDisk()
        }
    }

    // -------------------------------------------------------------------- policy ----

    fun updateSettings(transform: (RuntimeSettings) -> RuntimeSettings) {
        settingsRepository.update(transform)
    }

    fun updatePolicy(transform: (ResponsePolicy) -> ResponsePolicy) {
        settingsRepository.update { it.copy(policy = transform(it.policy)) }
    }

    fun setSafetyMode(mode: SafetyMode) {
        settingsRepository.update { current ->
            current.copy(policy = current.policyFor(mode).copy(safetyMode = mode))
        }
    }

    /** Applies the measured device recommendation as the active limits. */
    fun applyDeviceAdvice() {
        val advice = _budgetAdvice.value
        updatePolicy {
            it.copy(
                maxOutputTokens = advice.maxOutputTokens,
                contextTokenBudget = advice.contextTokenBudget,
                historyTurnsOverride = advice.historyTurns,
                cpuThreads = advice.cpuThreads,
                cpuReserveCores = -1,
                gpuEnabled = advice.gpuEnabled,
                quantization = advice.quantization,
                flushIntervalMs = ResponseBudgetAdvisor.flushIntervalMs(_hardware.value),
                minFlushChars = ResponseBudgetAdvisor.minFlushChars(_hardware.value)
            )
        }
    }

    fun toggleGpu(enabled: Boolean) = updatePolicy { it.copy(gpuEnabled = enabled) }

    /** 0 = auto (device tier); the reference rule is never to use every core. */
    fun setCpuThreads(threads: Int) = updatePolicy { it.copy(cpuThreads = threads.coerceIn(0, 16)) }

    /** -1 = auto; otherwise how many cores stay free for Android while a reply is decoding. */
    fun setCpuReserve(cores: Int) = updatePolicy { it.copy(cpuReserveCores = cores.coerceIn(-1, 7)) }

    fun setLowPriorityInference(enabled: Boolean) =
        updatePolicy { it.copy(lowPriorityInference = enabled) }

    fun setMaxTokens(tokens: Int) = updatePolicy { it.copy(maxOutputTokens = tokens.coerceIn(0, 4_096)) }

    fun setContextBudget(tokens: Int) = updatePolicy { it.copy(contextTokenBudget = tokens) }

    fun setHistoryTurns(turns: Int) = updatePolicy { it.copy(historyTurnsOverride = turns) }

    fun setFlushInterval(ms: Long) = updatePolicy { it.copy(flushIntervalMs = ms.coerceIn(30L, 500L)) }

    fun setMinFlushChars(chars: Int) = updatePolicy { it.copy(minFlushChars = chars.coerceIn(1, 120)) }

    /** Negative means "follow the persona", so it must survive the clamp. */
    fun setTemperature(value: Double) =
        updatePolicy { it.copy(temperature = if (value < 0) -1.0 else value.coerceIn(0.0, 2.0)) }

    fun setTopP(value: Double) =
        updatePolicy { it.copy(topP = if (value < 0) -1.0 else value.coerceIn(0.05, 1.0)) }

    fun setTopK(value: Int) =
        updatePolicy { it.copy(topK = if (value <= 0) -1 else value.coerceIn(1, 200)) }

    /** Give the persona its own sampling back. */
    fun resetSamplingToPersona() =
        updatePolicy { it.copy(temperature = -1.0, topP = -1.0, topK = -1) }

    fun setMarkdownWhileStreaming(enabled: Boolean) =
        updatePolicy { it.copy(renderMarkdownWhileStreaming = enabled) }

    fun setAutoFollowScroll(enabled: Boolean) = updatePolicy { it.copy(autoFollowScroll = enabled) }

    fun setTimeouts(prefillSec: Int, idleSec: Int, hardSec: Int) = updatePolicy {
        it.copy(
            prefillTimeoutSec = prefillSec.coerceIn(10, 600),
            idleTokenTimeoutSec = idleSec.coerceIn(3, 120),
            hardTimeoutSec = hardSec.coerceIn(30, 1_800)
        )
    }

    fun setReleaseModelOnBackground(enabled: Boolean) =
        updatePolicy { it.copy(releaseModelOnBackground = enabled) }

    // ------------------------------------------------------------------- engines ----

    fun setActiveEngine(id: String) {
        settingsRepository.update { it.copy(activeEngineId = id) }
    }

    fun saveEngineProfile(profile: EngineProfile) {
        settingsRepository.update { current ->
            val replaced = current.engines.map { if (it.id == profile.id) profile else it }
            val list = if (replaced.any { it.id == profile.id }) replaced else replaced + profile
            current.copy(engines = list)
        }
    }

    fun removeEngine(id: String) {
        settingsRepository.update { current ->
            val kept = current.engines.filterNot { it.id == id }
            current.copy(
                engines = kept.ifEmpty { EngineProfile.DEFAULTS },
                activeEngineId = if (current.activeEngineId == id) EngineProfile.GEMINI_BUILTIN.id
                else current.activeEngineId
            )
        }
    }

    fun testEngine(id: String) {
        viewModelScope.launch {
            val profile = _runtimeSettings.value.engines.find { it.id == id } ?: return@launch
            _enginePing.update { it + (id to EnginePingResult(false, "Testing…", null)) }
            val engine = aiService.engineFor(profile)
            val ping = runCatching { engine.ping() }
                .getOrElse { com.example.service.engine.EnginePing(false, "Probe failed: ${it.message}", null) }
            _enginePing.update { it + (id to EnginePingResult(ping.ok, ping.message, ping.detail)) }
        }
    }

    // ------------------------------------------------------------------- models ----

    fun searchHfModels(query: String) {
        searchJob?.cancel()
        val clean = query.trim()
        if (clean.isBlank()) {
            _hfSearchResults.value = emptyList()
            _isSearchingHf.value = false
            return
        }
        searchJob = viewModelScope.launch {
            _isSearchingHf.value = true
            delay(350) // debounce rapid keystrokes
            val settings = _runtimeSettings.value
            val token = settings.engines
                .firstOrNull { it.id == "hf-router" }?.apiKey?.takeIf { it.isNotBlank() }
            // Live search + real repo-tree enrichment: the hub shows the byte size of the file
            // the downloader will actually fetch, and marks whether it fits this device.
            _hfSearchResults.value = hfSearchService.searchModels(
                query = clean,
                token = token,
                enrichFiles = true
            )
            _isSearchingHf.value = false
        }
    }

    fun clearHfSearch() {
        searchJob?.cancel()
        _hfSearchResults.value = emptyList()
        _isSearchingHf.value = false
    }

    fun selectModel(model: HFModelConfig) {
        _currentModel.value = model
        refreshAdvice()
    }

    fun isModelDownloaded(modelId: String): Boolean = downloads.isDownloaded(modelId)

    /** Called by the hub UI; returns instantly, progress flows through [downloadStates]. */
    fun startDownloadModel(model: HFModelConfig) {
        val settings = _runtimeSettings.value
        val advice = _budgetAdvice.value
        downloads.start(
            model = model,
            residentBudgetBytes = (advice.modelResidentMb + 300L) * 1_048_576L,
            preferredQuant = settings.preferredHfQuant.ifBlank { advice.quantization },
            token = settings.engines.firstOrNull { it.id == "hf-router" }?.apiKey?.ifBlank { null },
            onResolved = { file ->
                _resolvedFiles.update { it + (model.id to file) }
            }
        )
    }

    fun pauseDownload(modelId: String) = downloads.pause(modelId)

    fun cancelDownload(modelId: String) {
        downloads.cancel(modelId)
        measureCache()
    }

    fun deleteDownloadedModel(modelId: String) {
        downloads.deleteDownload(modelId)
        measureCache()
    }

    /**
     * "Use offline" selects the weights, and only switches to the on-device engine when a native
     * runtime is actually linked into this build — otherwise the downloaded file stays usable as
     * a source for a LAN llama.cpp/Ollama server instead of dead-ending with an unavailable engine.
     */
    fun startUsingDownloadedModel(model: HFModelConfig) {
        selectModel(model)
        val downloaded = isModelDownloaded(model.id)
        if (downloaded && NativeBackends.discover() != null) {
            setActiveEngine(EngineProfile.LOCAL_ENGINE.id)
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            downloads.clearCaches()
            measureCache()
        }
    }

    // ------------------------------------------------------------- composer state ----

    fun onInputChange(text: String) {
        _input.value = text
    }

    fun selectAgent(agent: Agent) {
        if (_currentAgent.value.id != agent.id) _currentAgent.value = agent
    }

    fun setAttachment(attachment: Attachment?) {
        _selectedAttachment.value = attachment
    }

    fun clearAttachment() {
        _selectedAttachment.value = null
    }

    fun attachSampleZip() {
        _selectedAttachment.value = FileAttachmentHelper.createSampleZipAttachment()
    }

    fun attachSampleCode() {
        _selectedAttachment.value = FileAttachmentHelper.createSampleCodeAttachment()
    }

    fun attachSampleImage() {
        _selectedAttachment.value = FileAttachmentHelper.createSampleImageAttachment()
    }

    // -------------------------------------------------------------------- chat ops ----

    fun startNewChat() {
        stopGeneration()
        val currentMsgs = _messages.value
        if (currentMsgs.isNotEmpty()) {
            val firstUserPrompt = currentMsgs.firstOrNull { it.role == MessageRole.USER }?.content ?: "Conversation"
            val title = if (firstUserPrompt.length > 36) firstUserPrompt.take(36) + "…" else firstUserPrompt
            val session = ChatSession(
                title = title,
                timestamp = System.currentTimeMillis(),
                messageCount = currentMsgs.size,
                previewText = currentMsgs.lastOrNull()?.content?.take(80) ?: "Empty preview",
                messages = currentMsgs,
                agentEmoji = _currentAgent.value.emoji,
                modelName = _currentModel.value.name
            )
            viewModelScope.launch { _chatSessions.value = history.appendSession(session) }
        }
        _messages.value = emptyList()
        _selectedAttachment.value = null
        _input.value = ""
        viewModelScope.launch { history.clearDraft() }
    }

    fun loadChatSession(session: ChatSession) {
        stopGeneration()
        _messages.value = session.messages
        _selectedAttachment.value = null
        _input.value = ""
    }

    fun deleteChatSession(sessionId: String) {
        viewModelScope.launch {
            _chatSessions.value = history.deleteSession(sessionId)
        }
    }

    fun deleteAllChatHistory() {
        viewModelScope.launch {
            history.clearSessions()
            _chatSessions.value = emptyList()
        }
    }

    fun clearAllChattingAndHistory() {
        stopGeneration()
        _messages.value = emptyList()
        _selectedAttachment.value = null
        _input.value = ""
        viewModelScope.launch {
            history.clearSessions()
            history.clearDraft()
            _chatSessions.value = emptyList()
        }
    }

    fun clearChat() {
        stopGeneration()
        _messages.value = emptyList()
        viewModelScope.launch { history.clearDraft() }
    }

    /**
     * Cancels the generation job (which cancels the HTTP call / native decode) and commits
     * whatever text already arrived. No coroutine hop, so the bubble never flickers empty.
     */
    fun stopGeneration() {
        val job = activeJob
        activeJob = null
        generationSerial++
        _isStreaming.value = false
        job?.cancel()

        val partial = _streamingText.value
        val list = _messages.value
        if (list.isNotEmpty() && list.last().status == MessageStatus.STREAMING) {
            val last = list.last()
            val content = partial.ifBlank { last.content }
                .ifBlank { "— stopped before any token arrived —" }
            val updated = list.dropLast(1) + last.copy(content = content, status = MessageStatus.SUCCESS)
            _messages.value = updated
            viewModelScope.launch { history.saveDraft(updated) }
        }
        _streamingText.value = ""
        _streamStats.value = null
        _lastFinishReason.value = "stopped"
    }

    fun sendMessage(promptText: String? = null) {
        val text = (promptText ?: _input.value).trim()
        val attachment = _selectedAttachment.value
        if ((text.isEmpty() && attachment == null) || _isStreaming.value) return

        val settings = _runtimeSettings.value
        val policy = settings.policy
        val advice = _budgetAdvice.value
        val maxTokens = policy.effectiveMaxTokens(advice.maxOutputTokens)
        val contextBudget = policy.effectiveContextBudget(advice.contextTokenBudget)
        // CPU governor for this turn: bounded threads with the system core(s) kept free, and a
        // single thread on the SoC/quant combinations that are known to race.
        val inferenceThreadsCount = policy.resolvedThreads(
            cores = _hardware.value.cores,
            usingGpu = policy.gpuEnabled && _hardware.value.hasVulkanCompute,
            forceSingleThread = advice.singleThreadGuard
        )

        val messageContent = if (text.isNotEmpty()) text
        else if (attachment != null) "Please inspect and summarize ${attachment.name}"
        else return

        _input.value = ""
        _selectedAttachment.value = null

        val localWeights = isModelDownloaded(_currentModel.value.id)
        val userMsg = ChatMessage(
            role = MessageRole.USER,
            content = messageContent,
            status = MessageStatus.SUCCESS,
            attachment = attachment,
            isLocalExecution = localWeights && settings.activeEngine().kind == com.example.model.EngineKind.LOCAL_NATIVE
        )
        val placeholder = ChatMessage(
            role = MessageRole.ASSISTANT,
            content = "",
            status = MessageStatus.STREAMING,
            agentEmoji = _currentAgent.value.emoji,
            isLocalExecution = userMsg.isLocalExecution
        )

        // Two list writes per turn: one to open the bubble, one to commit it. Nothing in between.
        _messages.update { it + userMsg + placeholder }
        _streamingText.value = ""
        _streamStats.value = null
        _isStreaming.value = true
        val serial = ++generationSerial
        viewModelScope.launch { history.saveDraft(_messages.value) }

        activeJob = viewModelScope.launch {
            val historySnapshot = _messages.value.dropLast(2)
            val agentVal = _currentAgent.value
            val modelVal = _currentModel.value

            val flow = aiService.streamWithFallback(
                agent = agentVal,
                model = modelVal,
                history = historySnapshot,
                userPrompt = messageContent,
                attachment = attachment,
                settings = settings,
                maxTokens = maxTokens,
                contextBudget = contextBudget,
                cpuThreads = inferenceThreadsCount
            )

            val outcome = try {
                streamer.run(
                    scope = this,
                    events = flow,
                    policy = policy,
                    onText = { snapshot -> _streamingText.value = snapshot },
                    onStats = { stats -> _streamStats.value = stats }
                )
            } catch (ce: java.util.concurrent.CancellationException) {
                commitStreaming(serial, _streamingText.value, MessageStatus.SUCCESS, "stopped")
                _isStreaming.value = false
                throw ce
            }

            when (outcome) {
                is ResponseStreamer.Outcome.Completed -> {
                    commitStreaming(serial, outcome.text, MessageStatus.SUCCESS, outcome.stats.finishReason)
                    _streamStats.value = outcome.stats
                }

                is ResponseStreamer.Outcome.Failed -> {
                    val message = buildString {
                        append("⚠️ ").append(outcome.message)
                        if (!outcome.hint.isNullOrBlank()) {
                            append("\n\n").append(outcome.hint)
                        }
                    }
                    if (outcome.partial.isNullOrBlank()) {
                        commitStreaming(serial, message, MessageStatus.ERROR, "error")
                    } else {
                        // Tokens already on screen stay; the error becomes an inline note instead
                        // of wiping a half-finished answer.
                        commitStreaming(
                            serial,
                            outcome.partial.orEmpty() + "\n\n⚠️ " + outcome.message,
                            MessageStatus.SUCCESS,
                            "partial"
                        )
                    }
                }
            }
            _isStreaming.value = false
            _streamingText.value = ""
            reprobeHardware()
        }
    }

    /**
     * The only place the message list is mutated during a turn. Idempotent and stale-write safe:
     * a generation that was superseded (chat switched / stop pressed) simply does nothing.
     */
    private fun commitStreaming(
        serial: Int,
        text: String,
        status: MessageStatus,
        finishReason: String? = null
    ) {
        if (serial != generationSerial) return
        var committed: List<ChatMessage>? = null
        _messages.update { list ->
            if (list.isEmpty()) return@update list
            val last = list.last()
            if (last.role != MessageRole.ASSISTANT) return@update list
            val content = text.ifBlank { last.content }.ifBlank { "No text returned." }
            val updated = list.dropLast(1) + last.copy(content = content, status = status)
            committed = updated
            updated
        }
        committed?.let { snapshot -> viewModelScope.launch { history.saveDraft(snapshot) } }
        _lastFinishReason.value = finishReason
    }

    /** Exposes the effective (device-clamped) limits so the UI can show real numbers. */
    fun effectiveLimits(): Pair<Int, Int> {
        val policy = _runtimeSettings.value.policy
        val advice = _budgetAdvice.value
        return policy.effectiveMaxTokens(advice.maxOutputTokens) to
            policy.effectiveContextBudget(advice.contextTokenBudget)
    }

    override fun onCleared() {
        generationSerial++
        activeJob?.cancel()
        // Hand the weights back to the OS instead of pinning gigabytes after the screen closes.
        if (_runtimeSettings.value.policy.releaseModelOnBackground) {
            aiService.releaseNativeSession()
        }
        super.onCleared()
    }
}
