package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.Agent
import com.example.model.Attachment
import com.example.model.ChatMessage
import com.example.model.DownloadStatus
import com.example.model.EngineKind
import com.example.model.HFModelConfig
import com.example.model.MessageRole
import com.example.model.MessageStatus
import com.example.model.ModelType
import com.example.ui.components.MarkdownView
import com.example.ui.components.ModelSelectorSheet
import com.example.ui.components.PersonaSelectorSheet
import com.example.ui.components.SettingsSheet
import com.example.ui.theme.Amber400
import com.example.ui.theme.Cyan300
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Cyan500
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.Emerald400
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Indigo600
import com.example.ui.theme.Pink500
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate850
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import com.example.ui.theme.Violet500
import com.example.util.FileAttachmentHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel
) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsState()
    val currentAgent by viewModel.currentAgent.collectAsState()
    val currentModel by viewModel.currentModel.collectAsState()
    val deviceSpecs by viewModel.deviceSpecs.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val input by viewModel.input.collectAsState()
    val selectedAttachment by viewModel.selectedAttachment.collectAsState()
    val downloadStates by viewModel.downloadStates.collectAsState()
    val isGpuEnabled by viewModel.isGpuEnabled.collectAsState()
    val cpuThreads by viewModel.cpuThreads.collectAsState()
    val cacheSizeBytes by viewModel.cacheSizeBytes.collectAsState()
    val chatSessions by viewModel.chatSessions.collectAsState()
    val hfSearchResults by viewModel.hfSearchResults.collectAsState()
    val isSearchingHf by viewModel.isSearchingHf.collectAsState()

    val streamingText by viewModel.streamingText.collectAsState()
    val streamStats by viewModel.streamStats.collectAsState()
    val settings by viewModel.runtimeSettings.collectAsState()
    val lastFinishReason by viewModel.lastFinishReason.collectAsState()
    var settingsInitialTab by remember { mutableIntStateOf(0) }

    var showSettingsSheet by remember { mutableStateOf(false) }
    LaunchedEffect(showSettingsSheet) {
        if (!showSettingsSheet) settingsInitialTab = 0
    }
    var showModelSheet by remember { mutableStateOf(false) }
    var showAttachmentDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Activity Result Launchers for Media and File picking
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val attachment = FileAttachmentHelper.parseUri(context, uri)
            viewModel.setAttachment(attachment)
            showAttachmentDialog = false
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val attachment = FileAttachmentHelper.parseUri(context, uri)
            viewModel.setAttachment(attachment)
            showAttachmentDialog = false
        }
    }

    // -----------------------------------------------------------------------------------------
    // Freeze-proof autoscroll.
    //
    // The old code called animateScrollToItem for *every* emitted token, restarting an
    // in-flight scroll animation thousands of times per reply. Here we follow only while the
    // list is actually at the bottom, use a non-animated jump while streaming, and rely on the
    // coalesced snapshot (≈1 update per policy.flushIntervalMs) as the natural throttle.
    // -----------------------------------------------------------------------------------------
    val isNearBottom = remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()
            lastVisible == null || lastVisible.index >= info.totalItemsCount - 2
        }
    }
    LaunchedEffect(messages.size, streamingText.length, isStreaming) {
        if (messages.isEmpty()) return@LaunchedEffect
        if (!settings.policy.autoFollowScroll) return@LaunchedEffect
        if (isStreaming && !isNearBottom.value) return@LaunchedEffect
        if (isStreaming) {
            listState.scrollToItem(messages.size - 1)
        } else {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        containerColor = Slate950,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Slate950)
        ) {
            // Background Aurora Glow
            AuroraBackground()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            ) {
                // Top Navigation Bar
                ChatTopBar(
                    currentModel = currentModel,
                    isLocal = viewModel.isModelDownloaded(currentModel.id),
                    onOpenSettingsSheet = { showSettingsSheet = true },
                    onOpenModelSheet = { showModelSheet = true },
                    onNewChat = { viewModel.startNewChat() }
                )

                // Main Chat Scroll Area
                Box(modifier = Modifier.weight(1f)) {
                    if (messages.isEmpty()) {
                        EmptyChatState(
                            agent = currentAgent,
                            model = currentModel,
                            offlineStatusText = localWeightsNote(
                                onDevice = settings.activeEngine().kind == EngineKind.LOCAL_NATIVE,
                                downloaded = downloadStates[currentModel.id]?.status ==
                                    DownloadStatus.DOWNLOADED,
                                anyDownloaded = downloadStates.values.any {
                                    it.status == DownloadStatus.DOWNLOADED
                                }
                            ),
                            offlineStatusOk = downloadStates.values.any {
                                it.status == DownloadStatus.DOWNLOADED
                            },
                            onOpenModelHub = { showModelSheet = true },
                            onSuggestionClick = { prompt ->
                                viewModel.sendMessage(prompt)
                            },
                            onAttachZip = { viewModel.attachSampleZip() },
                            onAttachCode = { viewModel.attachSampleCode() }
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            itemsIndexed(messages, key = { _, msg -> msg.id }) { index, message ->
                                val streamingHere =
                                    isStreaming && index == messages.size - 1 &&
                                        message.role == MessageRole.ASSISTANT
                                MessageItem(
                                    // The live text comes from a separate StateFlow, so the list
                                    // itself keeps a stable size while tokens arrive.
                                    message = if (streamingHere && message.content.isEmpty()) {
                                        message.copy(content = streamingText)
                                    } else {
                                        message
                                    },
                                    isStreaming = streamingHere,
                                    renderMarkdown = !streamingHere ||
                                        settings.policy.renderMarkdownWhileStreaming,
                                    stats = if (streamingHere) streamStats else null,
                                    engineLabel = settings.activeEngine().label,
                                    tokenCapHint = viewModel.effectiveLimits().let { (maxTok, ctx) ->
                                        "≤%d tokens out • %d token context".format(maxTok, ctx)
                                    }
                                )
                            }
                        }
                    }
                }

                // Why did that answer stop? Truncation comes from the device-derived caps, so the
                // hint points straight at the control that owns it instead of leaving the user
                // guessing that the app "froze" or "cut off".
                if (!isStreaming && lastFinishReason != null &&
                    lastFinishReason in listOf(
                        "length-cap", "truncated", "stopped", "partial", "repetition"
                    )
                ) {
                    val capped = lastFinishReason == "length-cap" || lastFinishReason == "truncated"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (capped) Amber400.copy(alpha = 0.10f) else Slate900)
                            .border(
                                1.dp,
                                if (capped) Amber400.copy(alpha = 0.35f) else GlassBorder,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                settingsInitialTab = 1
                                showSettingsSheet = true
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = if (capped) Amber400 else Slate400,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (lastFinishReason) {
                                "length-cap", "truncated" ->
                                    "Answer hit its token cap — open Response Tuning to raise it for this device"
                                "repetition" ->
                                    "The model started repeating itself, so the answer was cut short — lower " +
                                        "Temperature or Top-k in Response Tuning"
                                else ->
                                    "Generation was stopped early — tap to review the anti-hang limits"
                            },
                            fontSize = 11.sp,
                            color = Slate300,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Adjust",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Cyan300
                        )
                    }
                }

                // Composer input bar
                ChatComposer(
                    input = input,
                    isStreaming = isStreaming,
                    currentAgent = currentAgent,
                    currentModel = currentModel,
                    selectedAttachment = selectedAttachment,
                    onInputChange = { viewModel.onInputChange(it) },
                    onSend = { viewModel.sendMessage() },
                    onStop = { viewModel.stopGeneration() },
                    onOpenModelHub = { showModelSheet = true },
                    onOpenAttachmentMenu = { showAttachmentDialog = true },
                    onClearAttachment = { viewModel.clearAttachment() },
                    modifier = Modifier.imePadding()
                )
            }
        }
    }

    // Attachment Picker Bottom Sheet
    if (showAttachmentDialog) {
        AttachmentPickerSheet(
            model = currentModel,
            onDismiss = { showAttachmentDialog = false },
            onPickImage = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onPickFile = {
                filePickerLauncher.launch("*/*")
            },
            onSampleZip = {
                viewModel.attachSampleZip()
                showAttachmentDialog = false
            },
            onSampleCode = {
                viewModel.attachSampleCode()
                showAttachmentDialog = false
            },
            onSampleImage = {
                viewModel.attachSampleImage()
                showAttachmentDialog = false
            }
        )
    }

    // Settings & Device Detective Bottom Sheet
    SettingsSheet(
        isOpen = showSettingsSheet,
        deviceSpecs = deviceSpecs,
        isGpuEnabled = isGpuEnabled,
        cpuThreads = cpuThreads,
        cacheSizeBytes = cacheSizeBytes,
        chatSessions = chatSessions,
        currentAgent = currentAgent,
        currentModel = currentModel,
        onDismiss = { showSettingsSheet = false },
        onToggleGpu = { viewModel.toggleGpu(it) },
        onSetCpuThreads = { viewModel.setCpuThreads(it) },
        onClearCache = { viewModel.clearCache() },
        onSelectAgent = { agent -> viewModel.selectAgent(agent) },
        onSelectModel = { model -> viewModel.selectModel(model) },
        onLoadChatSession = { session -> viewModel.loadChatSession(session) },
        onDeleteChatSession = { sessionId -> viewModel.deleteChatSession(sessionId) },
        onDeleteAllHistory = { viewModel.deleteAllChatHistory() },
        onClearCurrentChat = { viewModel.clearChat() },
        onOpenModelHub = {
            showSettingsSheet = false
            showModelSheet = true
        },
        viewModel = viewModel,
        initialTab = settingsInitialTab
    )

    // Model Hub Bottom Sheet (with local download, live HF search & start model buttons)
    ModelSelectorSheet(
        isOpen = showModelSheet,
        currentModel = currentModel,
        deviceSpecs = deviceSpecs,
        downloadStates = downloadStates,
        hfSearchResults = hfSearchResults,
        isSearchingHf = isSearchingHf,
        onSearchQueryChange = { query -> viewModel.searchHfModels(query) },
        onClearHfSearch = { viewModel.clearHfSearch() },
        onDismiss = { showModelSheet = false },
        onSelectModel = { model -> viewModel.selectModel(model) },
        onDownloadModel = { model -> viewModel.startDownloadModel(model) },
        onPauseDownload = { id -> viewModel.pauseDownload(id) },
        onCancelDownload = { id -> viewModel.cancelDownload(id) },
        onStartUsingModel = { model -> viewModel.startUsingDownloadedModel(model) },
        onDeleteDownloadedModel = { modelId -> viewModel.deleteDownloadedModel(modelId) }
    )
}

@Composable
fun AuroraBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "aurora")

    val offset1X by infiniteTransition.animateFloat(
        initialValue = -30f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset1X"
    )

    val offset1Y by infiniteTransition.animateFloat(
        initialValue = -20f,
        targetValue = 60f,
        animationSpec = infiniteRepeatable(
            animation = tween(8500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset1Y"
    )

    val offset2X by infiniteTransition.animateFloat(
        initialValue = 40f,
        targetValue = -50f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset2X"
    )

    val offset2Y by infiniteTransition.animateFloat(
        initialValue = 30f,
        targetValue = -40f,
        animationSpec = infiniteRepeatable(
            animation = tween(7500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset2Y"
    )

    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.38f,
        animationSpec = infiniteRepeatable(
            animation = tween(4500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val scaleAnim by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Floating Top-Start Indigo/Violet Orb
        Box(
            modifier = Modifier
                .size(340.dp)
                .align(Alignment.TopStart)
                .offset { IntOffset(offset1X.roundToInt(), offset1Y.roundToInt()) }
                .scale(scaleAnim)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Indigo500.copy(alpha = alphaAnim * 0.9f),
                            Violet500.copy(alpha = alphaAnim * 0.4f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Floating Bottom-End Cyan/Emerald Orb
        Box(
            modifier = Modifier
                .size(340.dp)
                .align(Alignment.BottomEnd)
                .offset { IntOffset(offset2X.roundToInt(), offset2Y.roundToInt()) }
                .scale(scaleAnim)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Cyan500.copy(alpha = alphaAnim),
                            Emerald400.copy(alpha = alphaAnim * 0.3f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Gentle Central Shimmer Aura
        Box(
            modifier = Modifier
                .size(260.dp)
                .align(Alignment.Center)
                .scale(scaleAnim * 0.95f)
                .alpha(alphaAnim * 0.5f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Cyan400.copy(alpha = 0.12f), Color.Transparent)
                    )
                )
        )
    }
}

@Composable
fun ChatTopBar(
    currentModel: HFModelConfig,
    isLocal: Boolean,
    onOpenSettingsSheet: () -> Unit,
    onOpenModelSheet: () -> Unit,
    onNewChat: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_indicator")
    val dotScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Slate950.copy(alpha = 0.88f))
            .border(width = 1.dp, color = Slate900.copy(alpha = 0.8f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Settings Button Pill with smooth press effect
        var settingsPressed by remember { mutableStateOf(false) }
        val settingsScale by animateFloatAsState(
            targetValue = if (settingsPressed) 0.93f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            label = "settingsScale"
        )

        Row(
            modifier = Modifier
                .height(38.dp)
                .graphicsLayer {
                    scaleX = settingsScale
                    scaleY = settingsScale
                }
                .clip(RoundedCornerShape(20.dp))
                .background(GlassSurface)
                .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                .clickable {
                    settingsPressed = true
                    onOpenSettingsSheet()
                }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LaunchedEffect(settingsPressed) {
                if (settingsPressed) {
                    delay(120)
                    settingsPressed = false
                }
            }
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = Cyan400,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Settings",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Slate100
            )
        }

        // Center: Model Dropdown Pill with animated pulsating status indicator
        var modelPressed by remember { mutableStateOf(false) }
        val modelScale by animateFloatAsState(
            targetValue = if (modelPressed) 0.94f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            label = "modelScale"
        )

        Row(
            modifier = Modifier
                .height(38.dp)
                .graphicsLayer {
                    scaleX = modelScale
                    scaleY = modelScale
                }
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (isLocal) Emerald400.copy(alpha = 0.14f)
                    else Cyan400.copy(alpha = 0.12f)
                )
                .border(
                    width = 1.dp,
                    color = if (isLocal) Emerald400.copy(alpha = 0.4f) else Cyan400.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(20.dp)
                )
                .clickable {
                    modelPressed = true
                    onOpenModelSheet()
                }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            LaunchedEffect(modelPressed) {
                if (modelPressed) {
                    delay(120)
                    modelPressed = false
                }
            }
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .scale(dotScale)
                    .clip(CircleShape)
                    .background(if (isLocal) Emerald400 else Cyan400)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = currentModel.name.take(16),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isLocal) Emerald400 else Cyan300
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Select Model",
                tint = if (isLocal) Emerald400 else Cyan400,
                modifier = Modifier.size(15.dp)
            )
        }

        // Right: Clean New Chat / Clear Action Button with spring animation
        var newChatPressed by remember { mutableStateOf(false) }
        val newChatScale by animateFloatAsState(
            targetValue = if (newChatPressed) 0.88f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            label = "newChatScale"
        )

        IconButton(
            onClick = {
                newChatPressed = true
                onNewChat()
            },
            modifier = Modifier
                .size(38.dp)
                .graphicsLayer {
                    scaleX = newChatScale
                    scaleY = newChatScale
                }
                .clip(CircleShape)
                .background(GlassSurface)
                .border(1.dp, GlassBorder, CircleShape)
        ) {
            LaunchedEffect(newChatPressed) {
                if (newChatPressed) {
                    delay(120)
                    newChatPressed = false
                }
            }
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "New Chat",
                tint = Slate300,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

/**
 * Live generation telemetry: flush count is what proves the UI is being coalesced
 * (e.g. "312 tok/s • 1.9k chars • 24 repaints" instead of 1.9k recompositions).
 */
@Composable
fun StreamingTelemetry(stats: com.example.service.ResponseStreamer.Stats) {
    val phase = when {
        stats.phase == "connecting" -> "connecting…"
        stats.phase == "loading-weights" -> "mapping weights…"
        stats.phase == "decoding" -> "prefilling…"
        stats.phase == "streaming" -> "streaming"
        stats.phase == "timeout" -> "stalled — closing turn"
        stats.phase.startsWith("fallback:") -> "switching engine"
        stats.phase.startsWith("local-litertlm") -> "on-device"
        else -> stats.phase
    }
    Text(
        text = "%s • %d tok • %.0f tok/s • %d repaints".format(
            phase,
            stats.tokensOut,
            stats.tokensPerSecond,
            stats.flushes
        ),
        fontSize = 10.sp,
        color = Slate400,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun MessageItem(
    message: ChatMessage,
    isStreaming: Boolean,
    renderMarkdown: Boolean = true,
    stats: com.example.service.ResponseStreamer.Stats? = null,
    engineLabel: String = "",
    tokenCapHint: String = ""
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }

    val copyAction = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Message", message.content)
        clipboard.setPrimaryClip(clip)
        copied = true
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        scope.launch {
            delay(2000)
            copied = false
        }
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(280, easing = FastOutSlowInEasing)) +
                slideInVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) { fullHeight -> fullHeight / 4 }
    ) {
        if (message.role == MessageRole.USER) {
            // User Message (Right-aligned, Indigo Gradient with smooth expansion)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        ),
                    horizontalAlignment = Alignment.End
                ) {
                    // Render attached file/image preview if present
                    if (message.attachment != null) {
                        AttachmentMessageBubble(attachment = message.attachment)
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    if (message.content.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 16.dp,
                                        topEnd = 16.dp,
                                        bottomStart = 16.dp,
                                        bottomEnd = 4.dp
                                    )
                                )
                                .background(
                                    Brush.linearGradient(listOf(Indigo500, Violet500))
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = message.content,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = Slate100
                            )
                        }
                    }
                }
            }
        } else {
            // Assistant Message (Left-aligned, Glass Card + Markdown + Smooth Fluid Expansion)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(Cyan400, Indigo600))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = message.agentEmoji ?: "🤖", fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        )
                        .clip(
                            RoundedCornerShape(
                                topStart = 4.dp,
                                topEnd = 16.dp,
                                bottomStart = 16.dp,
                                bottomEnd = 16.dp
                            )
                        )
                        .background(Slate900.copy(alpha = 0.95f))
                        .border(
                            1.dp,
                            GlassBorder,
                            RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                        )
                        .padding(14.dp)
                ) {
                    if (message.content.isEmpty() && isStreaming) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Reasoning & generating tokens",
                                    fontSize = 13.sp,
                                    color = Slate400,
                                    fontWeight = FontWeight.Medium
                                )
                                if (engineLabel.isNotBlank()) {
                                    Text(
                                        text = "$engineLabel • $tokenCapHint",
                                        fontSize = 10.sp,
                                        color = Slate400.copy(alpha = 0.8f)
                                    )
                                }
                            }
                            BouncingThinkingDots()
                        }
                    } else if (renderMarkdown) {
                        MarkdownView(
                            text = message.content,
                            textColor = Slate100
                        )
                        if (isStreaming) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PulsingCursor()
                                if (stats != null) StreamingTelemetry(stats)
                            }
                        }
                    } else {
                        // Cheaper path while tokens pour in: no Markdown parse per repaint.
                        Text(
                            text = message.content,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            color = Slate100
                        )
                        if (isStreaming) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PulsingCursor()
                                if (stats != null) StreamingTelemetry(stats)
                            }
                        }
                    }

                    if (!isStreaming && message.content.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(if (message.isLocalExecution) Emerald400 else Cyan400)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = when {
                                        message.isLocalExecution -> "On-device weights"
                                        engineLabel.isNotBlank() -> engineLabel
                                        else -> "Remote model"
                                    },
                                    fontSize = 10.sp,
                                    color = Slate400
                                )
                            }

                            val copyBtnScale by animateFloatAsState(
                                targetValue = if (copied) 1.08f else 1.0f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                label = "copyBtnScale"
                            )

                            Row(
                                modifier = Modifier
                                    .graphicsLayer {
                                        scaleX = copyBtnScale
                                        scaleY = copyBtnScale
                                    }
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Slate850)
                                    .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
                                    .clickable { copyAction() }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = "Copy Message",
                                    tint = if (copied) Emerald400 else Slate400,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (copied) "Copied" else "Copy",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (copied) Emerald400 else Slate300
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BouncingThinkingDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "bouncing_dots")

    val dot1Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )

    val dot2Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(350, delayMillis = 120, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )

    val dot3Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(350, delayMillis = 240, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .offset(y = dot1Offset.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(Cyan400)
        )
        Box(
            modifier = Modifier
                .offset(y = dot2Offset.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(Cyan300)
        )
        Box(
            modifier = Modifier
                .offset(y = dot3Offset.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(Indigo500)
        )
    }
}

@Composable
fun AttachmentMessageBubble(attachment: Attachment) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Slate900)
            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        if (attachment.isImage && attachment.uri != null) {
            AsyncImage(
                model = attachment.uri,
                contentDescription = attachment.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        when {
                            attachment.isImage -> Cyan400.copy(alpha = 0.2f)
                            attachment.isZip -> Emerald400.copy(alpha = 0.2f)
                            else -> Amber400.copy(alpha = 0.2f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        attachment.isImage -> Icons.Default.Image
                        attachment.isZip -> Icons.Default.FolderZip
                        else -> Icons.Default.Code
                    },
                    contentDescription = null,
                    tint = when {
                        attachment.isImage -> Cyan400
                        attachment.isZip -> Emerald400
                        else -> Amber400
                    },
                    modifier = Modifier.size(16.dp)
                )
            }

            Column {
                Text(
                    text = attachment.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Slate100,
                    maxLines = 1
                )
                Text(
                    text = "${attachment.formattedSize} • ${if (attachment.isZip) "${attachment.zipEntries.size} files in archive" else attachment.extension.uppercase()}",
                    fontSize = 10.sp,
                    color = Slate400
                )
            }
        }
    }
}

@Composable
fun PulsingCursor() {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )
    Box(
        modifier = Modifier
            .size(width = 7.dp, height = 14.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Cyan400.copy(alpha = alpha))
    )
}

/**
 * One honest sentence about whether the next reply is produced by this phone. Kept out of the
 * composable so the wording can be unit-tested without a Compose host.
 */
internal fun localWeightsNote(
    onDevice: Boolean,
    downloaded: Boolean,
    anyDownloaded: Boolean
): String? = when {
    !onDevice -> null
    downloaded -> "Running on this device — the selected weights are local, no network needed."
    anyDownloaded ->
        "On-device engine selected, but this model's weights are not on disk yet. Download them " +
            "in Model Hub, or the reply will fall back to a configured server."
    else ->
        "On-device engine: nothing downloaded yet. Open Model Hub and download a Q4 file that fits " +
            "your RAM — answers are then generated offline, with no API key."
}
@Composable
fun EmptyChatState(
    agent: Agent,
    model: HFModelConfig,
    offlineStatusText: String? = null,
    offlineStatusOk: Boolean = false,
    onOpenModelHub: () -> Unit,
    onSuggestionClick: (String) -> Unit,
    onAttachZip: () -> Unit,
    onAttachCode: () -> Unit
) {
    val suggestions = listOf(
        "✨" to "What capabilities does Qwen 2.5 offer?",
        "📦" to "Inspect and analyze my project's ZIP archive.",
        "⚡" to "Write high-performance Kotlin coroutines code.",
        "🖼️" to "Analyze visual UI elements and architecture."
    )

    val infiniteTransition = rememberInfiniteTransition(label = "hero_floating")
    val floatingY by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatingY"
    )

    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseGlow"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Hero Avatar with Smooth Levitating Floating Movement & Ambient Pulse Glow
        Box(
            modifier = Modifier
                .offset(y = floatingY.dp)
                .size(72.dp),
            contentAlignment = Alignment.Center
        ) {
            // Ambient Aura Halo
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .scale(pulseGlow)
                    .clip(CircleShape)
                    .background(Cyan400.copy(alpha = 0.25f))
            )

            // Core Avatar Circle
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Cyan400.copy(alpha = 0.85f), Indigo600)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = agent.emoji, fontSize = 28.sp)
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "What can I help with today?",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = Slate100
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "${agent.name} • ${model.name}",
            fontSize = 13.sp,
            color = Slate400
        )

        // Tells the user, before they type anything, whether this conversation will run on the
        // phone itself — and exactly which button fixes it when it will not.
        if (offlineStatusText != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (offlineStatusOk) Emerald400.copy(alpha = 0.10f)
                        else Amber400.copy(alpha = 0.08f)
                    )
                    .border(
                        1.dp,
                        if (offlineStatusOk) Emerald400.copy(alpha = 0.30f)
                        else Amber400.copy(alpha = 0.35f),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable(enabled = !offlineStatusOk, onClick = onOpenModelHub)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (offlineStatusOk) "●" else "▲",
                    fontSize = 10.sp,
                    color = if (offlineStatusOk) Emerald400 else Amber400
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = offlineStatusText,
                    fontSize = 11.sp,
                    color = Slate300,
                    lineHeight = 15.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (!offlineStatusOk) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Open",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Cyan300
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Clean Suggestion Cards with Interactive Spring Movement
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for ((icon, suggestion) in suggestions) {
                var isPressed by remember { mutableStateOf(false) }
                val cardScale by animateFloatAsState(
                    targetValue = if (isPressed) 0.96f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "suggestionScale"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = cardScale
                            scaleY = cardScale
                        }
                        .clip(RoundedCornerShape(16.dp))
                        .background(Slate900.copy(alpha = 0.75f))
                        .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                        .clickable {
                            isPressed = true
                            onSuggestionClick(suggestion)
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LaunchedEffect(isPressed) {
                        if (isPressed) {
                            delay(120)
                            isPressed = false
                        }
                    }
                    Text(text = icon, fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = suggestion,
                        fontSize = 13.sp,
                        color = Slate100,
                        modifier = Modifier.weight(1f),
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Send suggestion",
                        tint = Slate400,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Supports Local ONNX, Code, ZIP, and Multimodal Vision",
            fontSize = 11.sp,
            color = Slate400.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun ChatComposer(
    input: String,
    isStreaming: Boolean,
    currentAgent: Agent,
    currentModel: HFModelConfig,
    selectedAttachment: Attachment?,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onOpenModelHub: () -> Unit,
    onOpenAttachmentMenu: () -> Unit,
    onClearAttachment: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canAttach = currentModel.supportsVision || currentModel.supportsFiles

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Slate950.copy(alpha = 0.95f))
            .padding(
                start = 14.dp,
                end = 14.dp,
                top = 6.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 6.dp
            )
    ) {
        // Selected Attachment Preview Card with Smooth Pop Animation
        AnimatedVisibility(
            visible = selectedAttachment != null,
            enter = fadeIn(tween(200)) + slideInVertically(spring(dampingRatio = Spring.DampingRatioLowBouncy)) { 20 },
            exit = fadeOut(tween(150)) + slideOutVertically { 20 }
        ) {
            if (selectedAttachment != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Slate900)
                        .border(1.dp, Cyan400.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when {
                                selectedAttachment.isImage -> Icons.Default.Image
                                selectedAttachment.isZip -> Icons.Default.FolderZip
                                else -> Icons.Default.Code
                            },
                            contentDescription = null,
                            tint = Cyan400,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = selectedAttachment.name,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate100,
                                maxLines = 1
                            )
                            Text(
                                text = "${selectedAttachment.formattedSize} • Ready to send",
                                fontSize = 10.sp,
                                color = Slate400
                            )
                        }
                    }

                    IconButton(
                        onClick = onClearAttachment,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove attachment",
                            tint = Slate400,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Pill Composer (ChatGPT / Gemini style with Animated Glowing Border)
        val isInputActive = input.isNotBlank() || isStreaming
        val composerBorderColor by animateColorAsState(
            targetValue = if (isStreaming) Cyan400.copy(alpha = 0.6f)
            else if (input.isNotBlank()) Cyan400.copy(alpha = 0.35f)
            else GlassBorder,
            animationSpec = tween(300),
            label = "composerBorder"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(Slate900)
                .border(1.dp, composerBorderColor, RoundedCornerShape(26.dp))
                .padding(start = 6.dp, end = 6.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Attachment Button
            if (canAttach) {
                IconButton(
                    onClick = onOpenAttachmentMenu,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Attach File or Image",
                        tint = if (selectedAttachment != null) Cyan400 else Slate300,
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(8.dp))
            }

            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                placeholder = {
                    Text(
                        text = if (isStreaming) "Generating response..."
                        else if (selectedAttachment != null) "Ask about ${selectedAttachment.name}..."
                        else "Message Qwen Agent...",
                        fontSize = 14.sp,
                        color = Slate400
                    )
                },
                maxLines = 4,
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_field"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Slate100,
                    unfocusedTextColor = Slate100
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() })
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Smooth Animated Transition between Send and Stop Buttons
            AnimatedContent(
                targetState = isStreaming,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                     scaleIn(initialScale = 0.8f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)))
                        .togetherWith(
                            fadeOut(animationSpec = tween(160, easing = FastOutSlowInEasing)) +
                            scaleOut(targetScale = 0.8f)
                        )
                },
                label = "send_stop_morph"
            ) { streaming ->
                if (streaming) {
                    // Stop Button with Pulse Breathing
                    val infiniteTransition = rememberInfiniteTransition(label = "stop_pulse")
                    val stopScale by infiniteTransition.animateFloat(
                        initialValue = 0.94f,
                        targetValue = 1.04f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(700, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "stopScale"
                    )

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .scale(stopScale)
                            .clip(CircleShape)
                            .background(Rose500)
                            .clickable { onStop() }
                            .testTag("stop_generation_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop",
                            tint = Slate100,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    // Send Circular Button
                    val canSend = input.isNotBlank() || selectedAttachment != null
                    var sendPressed by remember { mutableStateOf(false) }
                    val sendScale by animateFloatAsState(
                        targetValue = if (sendPressed) 0.88f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "sendScale"
                    )

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .graphicsLayer {
                                scaleX = sendScale
                                scaleY = sendScale
                            }
                            .clip(CircleShape)
                            .background(
                                if (canSend) Brush.linearGradient(listOf(Cyan400, Indigo500))
                                else Brush.linearGradient(listOf(Slate800, Slate800))
                            )
                            .clickable(enabled = canSend) {
                                sendPressed = true
                                onSend()
                            }
                            .testTag("send_message_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        LaunchedEffect(sendPressed) {
                            if (sendPressed) {
                                delay(120)
                                sendPressed = false
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (canSend) Slate950 else Slate400,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Sleek Minimalist Footnote
        Text(
            text = "Qwen LLM • Private On-Device AI",
            fontSize = 10.sp,
            color = Slate400.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentPickerSheet(
    model: HFModelConfig,
    onDismiss: () -> Unit,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    onSampleZip: () -> Unit,
    onSampleCode: () -> Unit,
    onSampleImage: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Slate900,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Attach Files & Media",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate100
                    )
                    Text(
                        text = "Compatible with ${model.name}",
                        fontSize = 11.sp,
                        color = Slate400
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(GlassSurface)
                        .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Slate300,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Real Pick Options
            if (model.supportsVision) {
                AttachmentOptionCard(
                    icon = Icons.Default.Image,
                    iconTint = Cyan400,
                    title = "Choose Photo or Image",
                    subtitle = "Zero-permission Android Photo Picker",
                    onClick = onPickImage
                )
            }

            if (model.supportsFiles) {
                AttachmentOptionCard(
                    icon = Icons.Default.FolderZip,
                    iconTint = Emerald400,
                    title = "Pick ZIP, Code, or Text File",
                    subtitle = "Inspect and extract files in local memory",
                    onClick = onPickFile
                )
            }

            // Fast Sample Attachments Section
            Text(
                text = "Quick Demo Attachments",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Slate400,
                modifier = Modifier.padding(top = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Slate850)
                        .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                        .clickable { onSampleZip() }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FolderZip, contentDescription = null, tint = Emerald400, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sample ZIP", fontSize = 11.sp, color = Slate200, fontWeight = FontWeight.Medium)
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Slate850)
                        .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                        .clickable { onSampleCode() }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Code, contentDescription = null, tint = Amber400, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sample Code", fontSize = 11.sp, color = Slate200, fontWeight = FontWeight.Medium)
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Slate850)
                        .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                        .clickable { onSampleImage() }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = Cyan400, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sample Image", fontSize = 11.sp, color = Slate200, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun AttachmentOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Slate850)
            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = title, tint = iconTint, modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Slate100)
            Text(text = subtitle, fontSize = 11.sp, color = Slate400)
        }
    }
}

val Slate200 = Color(0xFFE2E8F0)
