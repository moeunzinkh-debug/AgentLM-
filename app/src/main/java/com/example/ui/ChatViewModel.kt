package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.Agent
import com.example.model.AgentCatalog
import com.example.model.Attachment
import com.example.model.ChatMessage
import com.example.model.ChatSession
import com.example.model.DeviceSpecs
import com.example.model.DownloadStatus
import com.example.model.HFModelConfig
import com.example.model.MessageRole
import com.example.model.MessageStatus
import com.example.model.ModelCatalog
import com.example.model.ModelDownloadProgress
import com.example.service.AiService
import com.example.service.HfModelSearchService
import com.example.util.FileAttachmentHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class ChatViewModel(
    private val aiService: AiService = AiService(),
    private val hfSearchService: HfModelSearchService = HfModelSearchService()
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _currentAgent = MutableStateFlow<Agent>(AgentCatalog.AGENTS.first())
    val currentAgent: StateFlow<Agent> = _currentAgent.asStateFlow()

    private val _currentModel = MutableStateFlow<HFModelConfig>(ModelCatalog.DEFAULT_MODEL)
    val currentModel: StateFlow<HFModelConfig> = _currentModel.asStateFlow()

    private val _deviceSpecs = MutableStateFlow<DeviceSpecs>(ModelCatalog.detectDevice())
    val deviceSpecs: StateFlow<DeviceSpecs> = _deviceSpecs.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _selectedAttachment = MutableStateFlow<Attachment?>(null)
    val selectedAttachment: StateFlow<Attachment?> = _selectedAttachment.asStateFlow()

    // Settings & Hardware Accelerators
    private val _isGpuEnabled = MutableStateFlow(true)
    val isGpuEnabled: StateFlow<Boolean> = _isGpuEnabled.asStateFlow()

    private val _cpuThreads = MutableStateFlow(4)
    val cpuThreads: StateFlow<Int> = _cpuThreads.asStateFlow()

    private val _cacheSizeBytes = MutableStateFlow(148_600_000L) // ~148.6 MB
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

    // Chat History Management
    private val _chatSessions = MutableStateFlow<List<ChatSession>>(
        listOf(
            ChatSession(
                title = "Kotlin Coroutines & Flow Optimization",
                timestamp = System.currentTimeMillis() - 3600_000L * 2,
                messageCount = 4,
                previewText = "How to implement efficient channel buffers with backpressure in Jetpack Compose...",
                messages = listOf(
                    ChatMessage(
                        role = MessageRole.USER,
                        content = "How to implement efficient channel buffers with backpressure in Jetpack Compose?"
                    ),
                    ChatMessage(
                        role = MessageRole.ASSISTANT,
                        content = "To handle backpressure in Kotlin Coroutines with Flow:\n\n```kotlin\nval sharedFlow = MutableSharedFlow<Event>(\n    replay = 0,\n    extraBufferCapacity = 64,\n    onBufferOverflow = BufferOverflow.DROP_OLDEST\n)\n```\nThis prevents UI freezes under rapid event bursts.",
                        agentEmoji = "⚡"
                    )
                ),
                agentEmoji = "⚡",
                modelName = "Qwen 2.5 Coder (0.5B)"
            ),
            ChatSession(
                title = "ZIP Project Architecture Analysis",
                timestamp = System.currentTimeMillis() - 3600_000L * 24,
                messageCount = 3,
                previewText = "Extracted 6 source files and verified Android Clean Architecture boundaries...",
                messages = listOf(
                    ChatMessage(
                        role = MessageRole.USER,
                        content = "Please analyze my uploaded project archive structure."
                    ),
                    ChatMessage(
                        role = MessageRole.ASSISTANT,
                        content = "### 📦 Architecture Review\n- **UI Layer**: Jetpack Compose M3\n- **Domain**: Repository & UseCases\n- **Status**: Production-ready Clean Architecture.",
                        agentEmoji = "🤖"
                    )
                ),
                agentEmoji = "🤖",
                modelName = "Qwen 2.5 (0.5B Instruct)"
            )
        )
    )
    val chatSessions: StateFlow<List<ChatSession>> = _chatSessions.asStateFlow()

    // Model Download Tracking
    private val _downloadStates = MutableStateFlow<Map<String, ModelDownloadProgress>>(
        mapOf(
            ModelCatalog.DEFAULT_MODEL.id to ModelDownloadProgress(
                modelId = ModelCatalog.DEFAULT_MODEL.id,
                status = DownloadStatus.DOWNLOADED,
                progress = 1.0f,
                downloadedBytes = ModelCatalog.DEFAULT_MODEL.sizeBytes,
                totalBytes = ModelCatalog.DEFAULT_MODEL.sizeBytes,
                speedMbps = 45.0
            )
        )
    )
    val downloadStates: StateFlow<Map<String, ModelDownloadProgress>> = _downloadStates.asStateFlow()

    // Live Hugging Face API Model Search
    private val _hfSearchResults = MutableStateFlow<List<HFModelConfig>>(emptyList())
    val hfSearchResults: StateFlow<List<HFModelConfig>> = _hfSearchResults.asStateFlow()

    private val _isSearchingHf = MutableStateFlow(false)
    val isSearchingHf: StateFlow<Boolean> = _isSearchingHf.asStateFlow()

    private var searchJob: Job? = null
    private val downloadJobs = mutableMapOf<String, Job>()
    private var activeJob: Job? = null

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
            delay(350) // Debounce rapid keystrokes
            val results = hfSearchService.searchModels(clean)
            _hfSearchResults.value = results
            _isSearchingHf.value = false
        }
    }

    fun clearHfSearch() {
        searchJob?.cancel()
        _hfSearchResults.value = emptyList()
        _isSearchingHf.value = false
    }

    fun onInputChange(text: String) {
        _input.value = text
    }

    fun selectAgent(agent: Agent) {
        if (_currentAgent.value.id != agent.id) {
            _currentAgent.value = agent
        }
    }

    fun selectModel(model: HFModelConfig) {
        _currentModel.value = model
    }

    fun isModelDownloaded(modelId: String): Boolean {
        return _downloadStates.value[modelId]?.status == DownloadStatus.DOWNLOADED
    }

    fun startDownloadModel(model: HFModelConfig) {
        if (downloadJobs[model.id]?.isActive == true) return

        val totalBytes = model.sizeBytes
        val job = viewModelScope.launch {
            val totalSteps = 20
            for (step in 1..totalSteps) {
                delay(120)
                val progress = step / totalSteps.toFloat()
                val downloadedBytes = (totalBytes * progress).toLong()
                val speed = 25.0 + (step % 5) * 6.2

                val currentMap = _downloadStates.value.toMutableMap()
                currentMap[model.id] = ModelDownloadProgress(
                    modelId = model.id,
                    status = if (step == totalSteps) DownloadStatus.DOWNLOADED else DownloadStatus.DOWNLOADING,
                    progress = progress,
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes,
                    speedMbps = speed
                )
                _downloadStates.value = currentMap
            }
            downloadJobs.remove(model.id)
        }
        downloadJobs[model.id] = job
    }

    fun cancelDownload(modelId: String) {
        downloadJobs[modelId]?.cancel()
        downloadJobs.remove(modelId)
        val currentMap = _downloadStates.value.toMutableMap()
        currentMap.remove(modelId)
        _downloadStates.value = currentMap
    }

    fun deleteDownloadedModel(modelId: String) {
        cancelDownload(modelId)
        val currentMap = _downloadStates.value.toMutableMap()
        currentMap[modelId] = ModelDownloadProgress(
            modelId = modelId,
            status = DownloadStatus.NOT_DOWNLOADED,
            progress = 0f,
            downloadedBytes = 0L,
            totalBytes = 0L
        )
        _downloadStates.value = currentMap
    }

    fun startUsingDownloadedModel(model: HFModelConfig) {
        // Ensure marked as downloaded
        if (!isModelDownloaded(model.id)) {
            val currentMap = _downloadStates.value.toMutableMap()
            currentMap[model.id] = ModelDownloadProgress(
                modelId = model.id,
                status = DownloadStatus.DOWNLOADED,
                progress = 1.0f,
                downloadedBytes = model.sizeBytes,
                totalBytes = model.sizeBytes,
                speedMbps = 50.0
            )
            _downloadStates.value = currentMap
        }
        selectModel(model)
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

    fun toggleGpu(enabled: Boolean) {
        _isGpuEnabled.value = enabled
    }

    fun setCpuThreads(threads: Int) {
        _cpuThreads.value = threads.coerceIn(1, 16)
    }

    fun clearCache() {
        // Simulates clearing app tokenizer cache, temp attachment buffers & inference cache
        _cacheSizeBytes.value = 0L
    }

    fun startNewChat() {
        stopGeneration()
        val currentMsgs = _messages.value
        if (currentMsgs.isNotEmpty()) {
            val firstUserPrompt = currentMsgs.firstOrNull { it.role == MessageRole.USER }?.content ?: "Conversation"
            val title = if (firstUserPrompt.length > 36) firstUserPrompt.take(36) + "..." else firstUserPrompt
            val newSession = ChatSession(
                title = title,
                timestamp = System.currentTimeMillis(),
                messageCount = currentMsgs.size,
                previewText = currentMsgs.lastOrNull()?.content?.take(80) ?: "Empty preview",
                messages = currentMsgs,
                agentEmoji = _currentAgent.value.emoji,
                modelName = _currentModel.value.name
            )
            val updated = listOf(newSession) + _chatSessions.value
            _chatSessions.value = updated
        }
        _messages.value = emptyList()
        _selectedAttachment.value = null
        _input.value = ""
    }

    fun loadChatSession(session: ChatSession) {
        stopGeneration()
        _messages.value = session.messages
        _selectedAttachment.value = null
        _input.value = ""
    }

    fun deleteChatSession(sessionId: String) {
        val updated = _chatSessions.value.filterNot { it.id == sessionId }
        _chatSessions.value = updated
    }

    fun deleteAllChatHistory() {
        _chatSessions.value = emptyList()
    }

    fun clearAllChattingAndHistory() {
        stopGeneration()
        _messages.value = emptyList()
        _chatSessions.value = emptyList()
        _selectedAttachment.value = null
        _input.value = ""
    }

    fun clearChat() {
        stopGeneration()
        _messages.value = emptyList()
    }

    fun stopGeneration() {
        activeJob?.cancel()
        activeJob = null
        _isStreaming.value = false
        val currentList = _messages.value
        if (currentList.isNotEmpty() && currentList.last().status == MessageStatus.STREAMING) {
            val updated = currentList.toMutableList()
            val last = updated.removeAt(updated.size - 1)
            updated.add(last.copy(status = MessageStatus.SUCCESS))
            _messages.value = updated
        }
    }

    fun sendMessage(promptText: String? = null) {
        val text = (promptText ?: _input.value).trim()
        val attachment = _selectedAttachment.value

        if ((text.isEmpty() && attachment == null) || _isStreaming.value) return

        val messageContent = if (text.isNotEmpty()) text else if (attachment != null) "Please inspect and summarize ${attachment.name}" else ""

        _input.value = ""
        _selectedAttachment.value = null

        val isLocal = isModelDownloaded(_currentModel.value.id)

        val userMsg = ChatMessage(
            role = MessageRole.USER,
            content = messageContent,
            status = MessageStatus.SUCCESS,
            attachment = attachment,
            isLocalExecution = isLocal
        )

        val assistantMsg = ChatMessage(
            role = MessageRole.ASSISTANT,
            content = "",
            status = MessageStatus.STREAMING,
            agentEmoji = _currentAgent.value.emoji,
            isLocalExecution = isLocal
        )

        val updatedList = _messages.value + userMsg + assistantMsg
        _messages.value = updatedList
        _isStreaming.value = true

        activeJob = viewModelScope.launch {
            val history = _messages.value.dropLast(2)
            val currentAgentVal = _currentAgent.value
            val currentModelVal = _currentModel.value

            aiService.streamReply(
                agent = currentAgentVal,
                model = currentModelVal,
                history = history,
                userPrompt = messageContent,
                attachment = attachment
            ).catch { err ->
                _isStreaming.value = false
                val list = _messages.value.toMutableList()
                if (list.isNotEmpty()) {
                    val last = list.removeAt(list.size - 1)
                    list.add(
                        last.copy(
                            content = "⚠️ Error: ${err.localizedMessage ?: "Failed to generate response."}",
                            status = MessageStatus.ERROR
                        )
                    )
                    _messages.value = list
                }
            }.collect { accumulatedChunk ->
                val list = _messages.value.toMutableList()
                if (list.isNotEmpty()) {
                    val last = list.removeAt(list.size - 1)
                    list.add(last.copy(content = accumulatedChunk))
                    _messages.value = list
                }
            }

            // Finish streaming
            val list = _messages.value.toMutableList()
            if (list.isNotEmpty()) {
                val last = list.removeAt(list.size - 1)
                list.add(last.copy(status = MessageStatus.SUCCESS))
                _messages.value = list
            }
            _isStreaming.value = false
        }
    }
}

