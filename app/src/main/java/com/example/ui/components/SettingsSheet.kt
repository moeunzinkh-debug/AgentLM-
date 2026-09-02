package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Agent
import com.example.model.AgentCatalog
import com.example.model.ChatMessage
import com.example.model.ChatSession
import com.example.model.DeviceSpecs
import com.example.model.EngineKind
import com.example.model.EngineProfile
import com.example.model.HFModelConfig
import com.example.model.BudgetAdvice
import com.example.model.HardwareInfo
import com.example.model.ModelCatalog
import com.example.model.RuntimeSettings
import com.example.model.SafetyMode
import com.example.ui.ChatViewModel
import com.example.ui.theme.Amber400
import com.example.ui.theme.Cyan300
import com.example.ui.theme.Cyan400
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.Emerald400
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.GlassSurfaceActive
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Indigo600
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate850
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import com.example.ui.theme.Violet500

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    isOpen: Boolean,
    deviceSpecs: DeviceSpecs,
    isGpuEnabled: Boolean,
    cpuThreads: Int,
    cacheSizeBytes: Long,
    chatSessions: List<ChatSession>,
    currentAgent: Agent,
    currentModel: HFModelConfig,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    onDismiss: () -> Unit,
    onToggleGpu: (Boolean) -> Unit,
    onSetCpuThreads: (Int) -> Unit,
    onClearCache: () -> Unit,
    onSelectAgent: (Agent) -> Unit,
    onSelectModel: (HFModelConfig) -> Unit,
    onLoadChatSession: (ChatSession) -> Unit,
    onDeleteChatSession: (String) -> Unit,
    onDeleteAllHistory: () -> Unit,
    onClearCurrentChat: () -> Unit,
    onOpenModelHub: () -> Unit,
    viewModel: ChatViewModel,
    initialTab: Int = 0
) {
    if (!isOpen) return

    var selectedTab by remember { mutableIntStateOf(initialTab) }
    LaunchedEffect(isOpen, initialTab) {
        if (isOpen) selectedTab = initialTab.coerceIn(0, 7)
    }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    var showCacheClearedToast by remember { mutableStateOf(false) }

    val hardware by viewModel.hardware.collectAsState()

    val tabs = listOf(
        "Hardware",
        "Response Tuning",
        "Engine & Keys",
        "GPU / CPU",
        "Cache",
        "History",
        "Personas",
        "About"
    )

    // Real numbers: measured free RAM + weight size + per-token KV growth of each model.
    val ramRecommendations = remember(deviceSpecs, hardware) {
        ModelCatalog.getRamRecommendations(deviceSpecs, hardware)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Slate950,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 28.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Brush.linearGradient(listOf(Cyan400, Indigo600))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Settings",
                            tint = Slate950,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Settings · Response Tuning",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate100
                        )
                        Text(
                            text = "Response limits, engines, keys & hardware diagnostics",
                            fontSize = 11.sp,
                            color = Slate400
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(GlassSurface)
                        .border(1.dp, GlassBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Slate300,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Scrollable Tab Row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Slate900,
                contentColor = Cyan400,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = Cyan400
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) Cyan300 else Slate400,
                                maxLines = 1
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Tab Content
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing))
                        .togetherWith(fadeOut(animationSpec = tween(150, easing = FastOutSlowInEasing)))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                label = "tab_content_transition"
            ) { tabIndex ->
                when (tabIndex) {
                    0 -> HardwareDetectiveTab(
                        deviceSpecs = deviceSpecs,
                        ramRecommendations = ramRecommendations,
                        currentModel = currentModel,
                        onSelectModel = { model ->
                            onSelectModel(model)
                            onDismiss()
                        },
                        onOpenModelHub = onOpenModelHub
                    )
                    1 -> ResponseTuningTab(viewModel = viewModel, hardware = hardware)
                    2 -> EngineKeysTab(viewModel = viewModel)
                    3 -> GpuCpuTab(
                        deviceSpecs = deviceSpecs,
                        isGpuEnabled = isGpuEnabled,
                        cpuThreads = cpuThreads,
                        onToggleGpu = onToggleGpu,
                        onSetCpuThreads = onSetCpuThreads,
                        hardware = hardware
                    )
                    4 -> CacheStorageTab(
                        cacheSizeBytes = cacheSizeBytes,
                        modelStorageBytes = viewModel.modelStorageBytes.collectAsState().value,
                        onClearCache = {
                            onClearCache()
                            showCacheClearedToast = true
                        }
                    )
                    5 -> HistoryTab(
                        chatSessions = chatSessions,
                        onLoadSession = { session ->
                            onLoadChatSession(session)
                            onDismiss()
                        },
                        onDeleteSession = onDeleteChatSession,
                        onClearCurrentChat = {
                            onClearCurrentChat()
                            onDismiss()
                        },
                        onDeleteAllConfirmRequest = { showDeleteAllConfirm = true }
                    )
                    6 -> PersonasTab(
                        currentAgent = currentAgent,
                        onSelectAgent = { agent ->
                            onSelectAgent(agent)
                            onDismiss()
                        }
                    )
                    7 -> AboutTab(hardware = hardware, viewModel = viewModel)
                }
            }
        }
    }

    // Confirmation Alert for Delete All Chat History
    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            containerColor = Slate900,
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = Rose500,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Delete All Chat History?",
                    color = Slate100,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This will permanently clear all saved chat conversations and active messages. This action cannot be undone.",
                    color = Slate300,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAllHistory()
                        onClearCurrentChat()
                        showDeleteAllConfirm = false
                    }
                ) {
                    Text("Delete Everything", color = Rose500, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirm = false }) {
                    Text("Cancel", color = Slate400)
                }
            }
        )
    }
}

// -----------------------------------------------------------------------------------------
// TAB 0: HARDWARE & RAM DETECTIVE
// -----------------------------------------------------------------------------------------
@Composable
private fun HardwareDetectiveTab(
    deviceSpecs: DeviceSpecs,
    ramRecommendations: List<com.example.model.RamModelRecommendation>,
    currentModel: HFModelConfig,
    onSelectModel: (HFModelConfig) -> Unit,
    onOpenModelHub: () -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.height(440.dp)
    ) {
        // System Hardware Diagnostic Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Slate900)
                    .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = "Device Detective",
                            tint = Cyan400,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Detected Hardware Specs",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate100
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Emerald400.copy(alpha = 0.15f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${deviceSpecs.totalRamGB} GB RAM",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Emerald400
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SpecChip(title = "CPU Cores", value = "${deviceSpecs.cores} Cores")
                    SpecChip(title = "Architecture", value = deviceSpecs.cpuArch)
                    SpecChip(title = "Avail. RAM", value = "~${deviceSpecs.availableRamGB} GB")
                    SpecChip(title = "GPU Acceleration", value = "Vulkan NPU")
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = deviceSpecs.reason,
                    fontSize = 11.sp,
                    color = Slate400,
                    lineHeight = 15.sp
                )
            }
        }

        // Section Title: Which Model Can Your Phone Run?
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RAM Compatibility Detective",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate100
                )
                Text(
                    text = "${ramRecommendations.size} Models Analyzed",
                    fontSize = 11.sp,
                    color = Cyan400
                )
            }
        }

        // Model Detective Items
        items(ramRecommendations) { item ->
            val isCurrent = item.model.id == currentModel.id
            val statusColor = if (item.isOptimal) Emerald400 else if (item.isSupported) Amber400 else Rose500

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isCurrent) CyanGlow else Slate900)
                    .border(
                        1.dp,
                        if (isCurrent) Cyan400 else GlassBorder,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.model.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate100,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(statusColor.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = item.statusLabel,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = "Requires ~${item.requiredRamGb}GB RAM • ${item.model.size}",
                        fontSize = 11.sp,
                        color = Slate400
                    )
                    Text(
                        text = item.tip,
                        fontSize = 10.sp,
                        color = Slate400.copy(alpha = 0.8f)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (isCurrent) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Cyan400.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text("Active", fontSize = 11.sp, color = Cyan300, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(GlassSurface)
                            .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
                            .clickable { onSelectModel(item.model) }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text("Use", fontSize = 11.sp, color = Slate300)
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecChip(title: String, value: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Slate850)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, fontSize = 9.sp, color = Slate400)
        Text(text = value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate100)
    }
}

// -----------------------------------------------------------------------------------------
// TAB 1: GPU / CPU HARDWARE ACCELERATION
// -----------------------------------------------------------------------------------------
@Composable
private fun GpuCpuTab(
    deviceSpecs: DeviceSpecs,
    isGpuEnabled: Boolean,
    cpuThreads: Int,
    onToggleGpu: (Boolean) -> Unit,
    onSetCpuThreads: (Int) -> Unit,
    hardware: HardwareInfo
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(440.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // GPU Switch Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Slate900)
                .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = "GPU",
                        tint = Cyan400,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "GPU / NPU Acceleration",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate100
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = when {
                        isGpuEnabled && hardware.vulkanComputeLevel >= 42 ->
                            "Vulkan compute level ${hardware.vulkanComputeLevel} detected — GPU delegate requested, " +
                                "with automatic retry on CPU if the driver refuses."
                        isGpuEnabled ->
                            "No full Vulkan compute device found (level ${hardware.vulkanComputeLevel}) — " +
                                "GPU requests will fall back to CPU on this SoC anyway."
                        else -> "CPU execution only (${hardware.cores} logical cores available)."
                    },
                    fontSize = 11.sp,
                    color = Slate400
                )
            }

            Switch(
                checked = isGpuEnabled,
                onCheckedChange = onToggleGpu,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Slate950,
                    checkedTrackColor = Cyan400,
                    uncheckedThumbColor = Slate400,
                    uncheckedTrackColor = Slate800
                )
            )
        }

        // CPU Multithreading Selector Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Slate900)
                .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Text(
                text = "CPU Inference Threads",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Slate100
            )
            Text(
                text = "Threads handed to the local runtime (device reports ${deviceSpecs.cores} logical cores). " +
                    "More threads than big cores makes decode *slower* and the UI jitterier — keep it at or below " +
                    "${(hardware.cores - 1).coerceAtLeast(1)}.",
                fontSize = 11.sp,
                color = Slate400,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(2, 4, 6, 8).forEach { count ->
                    val isSelected = cpuThreads == count
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Cyan400 else Slate800)
                            .clickable { onSetCpuThreads(count) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$count Threads",
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Slate950 else Slate300
                        )
                    }
                }
            }
        }

        // ONNX Engine Status
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Slate900)
                .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Text(
                text = "Runtime Engine Status",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Slate100
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Emerald400)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when {
                        hardware.isTensorSoC -> "Tensor SoC: Gemma-family weights are pinned to 1 thread to avoid corrupted logits"
                        hardware.powerSaveMode -> "Battery saver active: shorter replies + fewer threads"
                        else -> "${hardware.chipset} • ${hardware.abi} • heap ceiling ${hardware.appHeapMb} MB"
                    },
                    fontSize = 12.sp,
                    color = Slate300
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// TAB 2: CACHE & STORAGE
// -----------------------------------------------------------------------------------------
@Composable
private fun CacheStorageTab(
    cacheSizeBytes: Long,
    modelStorageBytes: Long,
    onClearCache: () -> Unit
) {
    val formattedCache = remember(cacheSizeBytes) {
        if (cacheSizeBytes <= 0) "0.0 MB"
        else "%.1f MB".format(cacheSizeBytes / (1024.0 * 1024.0))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(440.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Cache Info Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Slate900)
                .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = "Cache",
                        tint = Cyan400,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Temporary Inference Cache",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate100
                    )
                }

                Text(
                    text = formattedCache,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (cacheSizeBytes > 0) Cyan300 else Emerald400
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Cached weights on disk", fontSize = 10.sp, color = Slate400)
                    Text(
                        text = "%.1f MB".format(modelStorageBytes / 1_048_576.0),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Emerald400
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Model Hub storage", fontSize = 10.sp, color = Slate400)
                    Text(
                        text = if (modelStorageBytes > 0) "kept for offline use" else "nothing downloaded yet",
                        fontSize = 10.sp,
                        color = Slate300
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Clearing removes stale temp files and abandoned partial downloads only — " +
                    "completed weight files stay on disk so offline inference keeps working. " +
                    "Delete a model from Model Hub to reclaim its space.",
                fontSize = 11.sp,
                color = Slate400,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (cacheSizeBytes > 0) Cyan400 else Slate800)
                    .clickable(enabled = cacheSizeBytes > 0) { onClearCache() }
                    .padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CleaningServices,
                    contentDescription = "Clear",
                    tint = if (cacheSizeBytes > 0) Slate950 else Slate400,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (cacheSizeBytes > 0) "Clear App Cache Now ($formattedCache)" else "Cache is Clean (0 MB)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (cacheSizeBytes > 0) Slate950 else Slate400
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// TAB 3: CHAT HISTORY & CLEARING
// -----------------------------------------------------------------------------------------
@Composable
private fun HistoryTab(
    chatSessions: List<ChatSession>,
    onLoadSession: (ChatSession) -> Unit,
    onDeleteSession: (String) -> Unit,
    onClearCurrentChat: () -> Unit,
    onDeleteAllConfirmRequest: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(440.dp)
    ) {
        // Quick Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Clear Current Active Chat
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                .background(GlassSurface)
                .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                .clickable { onClearCurrentChat() }
                .padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Clear Current",
                    tint = Slate300,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text("Clear Active Chat", fontSize = 12.sp, color = Slate300)
            }

            // Delete All Chat History
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Rose500.copy(alpha = 0.15f))
                    .border(1.dp, Rose500.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .clickable { onDeleteAllConfirmRequest() }
                    .padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = "Delete All",
                    tint = Rose500,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text("Delete All History", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Rose500)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (chatSessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "No History",
                        tint = Slate700,
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No saved chat history",
                        fontSize = 13.sp,
                        color = Slate400
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(chatSessions, key = { it.id }) { session ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Slate900)
                            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                            .clickable { onLoadSession(session) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = session.agentEmoji, fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = session.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate100,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${session.modelName} • ${session.messageCount} messages",
                                fontSize = 10.sp,
                                color = Slate400
                            )
                        }

                        IconButton(
                            onClick = { onDeleteSession(session.id) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Session",
                                tint = Slate400,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// TAB 4: AGENT PERSONAS (MOVED FROM TOP BAR INTO SETTINGS)
// -----------------------------------------------------------------------------------------
@Composable
private fun PersonasTab(
    currentAgent: Agent,
    onSelectAgent: (Agent) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.height(440.dp)
    ) {
        items(AgentCatalog.AGENTS) { agent ->
            val isSelected = agent.id == currentAgent.id
            val cardBorder = if (isSelected) Cyan400 else GlassBorder
            val cardBg = if (isSelected) CyanGlow else Slate900

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(cardBg)
                    .border(1.dp, cardBorder, RoundedCornerShape(14.dp))
                    .clickable { onSelectAgent(agent) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Cyan400.copy(alpha = 0.3f), Indigo500.copy(alpha = 0.3f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = agent.emoji, fontSize = 20.sp)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = agent.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate100
                    )
                    Text(
                        text = agent.description,
                        fontSize = 11.sp,
                        color = Slate400,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Cyan400),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Slate950,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// TAB 5: ABOUT & PRIVACY
// -----------------------------------------------------------------------------------------
@Composable
private fun AboutTab(
    hardware: HardwareInfo,
    viewModel: ChatViewModel
) {
    val settings by viewModel.runtimeSettings.collectAsState()
    val activeEngine = settings.activeEngine()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(440.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Slate900)
                .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brush.linearGradient(listOf(Cyan400, Indigo600))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "About",
                        tint = Slate950,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "AgentLM",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate100
                    )
                    Text(
                        text = "v3.0.0 • Real streaming engines",
                        fontSize = 11.sp,
                        color = Slate400
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "AgentLM answers only from a real model: token-streamed Gemini / Ollama / llama.cpp / " +
                    "LM Studio / OpenRouter endpoints, or the weights you download from Hugging Face. " +
                    "Nothing is scripted — if an engine cannot answer you get an explanation instead of a reply.",
                fontSize = 12.sp,
                color = Slate300,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            InfoLine(label = "Active engine", value = activeEngine.label)
            InfoLine(label = "Native runtime", value = viewModel.nativeEngineSummary)
            InfoLine(label = "This device", value = "${"$"}{hardware.chipset} • ${"$"}{hardware.totalRamGb} GB RAM")

            Spacer(modifier = Modifier.height(12.dp))

            // Privacy Guarantee
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Emerald400.copy(alpha = 0.12f))
                    .border(1.dp, Emerald400.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Private",
                    tint = Emerald400,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "100% Private & On-Device. Zero telemetry and zero cloud eavesdropping.",
                    fontSize = 11.sp,
                    color = Emerald400,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// =========================================================================================
// TAB 1: RESPONSE TUNING — how long/often the model may talk, bounded by real hardware
// =========================================================================================
@Composable
private fun ResponseTuningTab(
    viewModel: ChatViewModel,
    hardware: HardwareInfo
) {
    val settings by viewModel.runtimeSettings.collectAsState()
    val advice by viewModel.budgetAdvice.collectAsState()
    val policy = settings.policy

    val deviceTokenCap = advice.maxOutputTokens
    val effectiveMax = policy.effectiveMaxTokens(deviceTokenCap)
    val effectiveCtx = policy.effectiveContextBudget(advice.contextTokenBudget)
    val effectiveTurns = policy.effectiveHistoryTurns(advice.historyTurns)
    val kvPerTurnMb = effectiveCtx * advice.kvPerTokenKb / 1024.0
    val repaintsPerSecond = 1000.0 / policy.flushIntervalMs.coerceAtLeast(1L).toDouble()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(440.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---- verdict ----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Slate900)
                .border(
                    1.dp,
                    when (advice.verdict) {
                        BudgetAdvice.Verdict.OPTIMAL -> Emerald400.copy(alpha = 0.4f)
                        BudgetAdvice.Verdict.TIGHT -> Amber400.copy(alpha = 0.4f)
                        BudgetAdvice.Verdict.OVER -> Rose500.copy(alpha = 0.4f)
                    },
                    RoundedCornerShape(14.dp)
                )
                .padding(13.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Device capacity verdict",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate100
                    )
                    Text(
                        text = "${hardware.manufacturer} ${hardware.model} • ${hardware.totalRamMb} MB RAM " +
                            "• ${hardware.availRamMb} MB free now",
                        fontSize = 10.sp,
                        color = Slate400
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when (advice.verdict) {
                                BudgetAdvice.Verdict.OPTIMAL -> Emerald400.copy(alpha = 0.18f)
                                BudgetAdvice.Verdict.TIGHT -> Amber400.copy(alpha = 0.18f)
                                BudgetAdvice.Verdict.OVER -> Rose500.copy(alpha = 0.18f)
                            }
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = when (advice.verdict) {
                            BudgetAdvice.Verdict.OPTIMAL -> "HEADROOM OK"
                            BudgetAdvice.Verdict.TIGHT -> "TIGHT"
                            BudgetAdvice.Verdict.OVER -> "OVER BUDGET"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (advice.verdict) {
                            BudgetAdvice.Verdict.OPTIMAL -> Emerald400
                            BudgetAdvice.Verdict.TIGHT -> Amber400
                            BudgetAdvice.Verdict.OVER -> Rose500
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Weights ≈ ${advice.modelResidentMb} MB resident + ${advice.kvPerTokenKb} KB KV per token. " +
                    "Free after load ≈ ${advice.freeRamAfterLoadMb} MB. Recommended ceiling for one reply: " +
                    "$deviceTokenCap tokens.",
                fontSize = 11.sp,
                color = Slate300,
                lineHeight = 15.sp
            )

            advice.warnings.forEach { warning ->
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Amber400,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = warning, fontSize = 10.sp, color = Amber400, lineHeight = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniButton(
                    label = "Apply for this phone",
                    accent = true,
                    onClick = { viewModel.applyDeviceAdvice() }
                )
                MiniButton(
                    label = "Re-measure",
                    accent = false,
                    onClick = { viewModel.reprobeHardware() }
                )
            }
        }

        // ---- safety preset ----
        TuningCard(
            title = "Behaviour preset",
            subtitle = "How aggressive the reply length and repaint cadence are allowed to be."
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SafetyMode.entries.forEach { mode ->
                    val selected = policy.safetyMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) Cyan400 else Slate800)
                            .clickable { viewModel.setSafetyMode(mode) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.label,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) Slate950 else Slate300
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = policy.safetyMode.blurb,
                fontSize = 10.sp,
                color = Slate400
            )
        }

        // ---- the three response limits ----
        TuningCard(
            title = "How much the model may answer",
            subtitle = "0 = auto. Every value is clamped to what this phone can hold, so a reply can never outgrow RAM."
        ) {
            SliderRow(
                label = "Max response tokens",
                valueText = if (policy.maxOutputTokens == 0) "Auto → $effectiveMax" else "$effectiveMax",
                value = effectiveMax.toFloat(),
                range = 64f..2048f,
                onValue = { viewModel.setMaxTokens(it.toInt()) }
            )
            Text(
                text = "≈ ${effectiveMax * 4} characters per reply. Long answers are the #1 cause of a " +
                    "stalled chat UI on mid-range phones.",
                fontSize = 10.sp,
                color = Slate400,
                lineHeight = 13.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            SliderRow(
                label = "Prompt + attachment budget",
                valueText = "$effectiveCtx tokens",
                value = effectiveCtx.toFloat(),
                range = 256f..8192f,
                onValue = { viewModel.setContextBudget(it.toInt()) }
            )
            Text(
                text = "Prefill cost ≈ %.1f MB KV. Attached file bodies are truncated to fit — this is the " +
                    "other main freeze source.".format(kvPerTurnMb),
                fontSize = 10.sp,
                color = Slate400,
                lineHeight = 13.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            StepperRow(
                label = "Conversation turns kept",
                desc = "Older turns are dropped first when the budget runs out.",
                value = effectiveTurns,
                min = 0,
                max = 16,
                onChange = { viewModel.setHistoryTurns(it) }
            )
        }

        // ---- streaming cadence ----
        TuningCard(
            title = "Streaming smoothness",
            subtitle = "Tokens are coalesced before they reach Compose: fewer, bigger repaints instead of one per token."
        ) {
            SliderRow(
                label = "Repaint interval",
                valueText = "${policy.flushIntervalMs.toInt()} ms  (≈%.0f fps)".format(repaintsPerSecond),
                value = policy.flushIntervalMs.toFloat(),
                range = 30f..300f,
                onValue = { viewModel.setFlushInterval(it.toLong()) }
            )
            SliderRow(
                label = "Minimum new characters per repaint",
                valueText = "${policy.minFlushChars}",
                value = policy.minFlushChars.toFloat(),
                range = 1f..80f,
                onValue = { viewModel.setMinFlushChars(it.toInt()) }
            )
            SwitchRow(
                label = "Render Markdown while streaming",
                desc = "Off = plain text while typing, formatted when finished (cheapest). On = live formatting, costs re-parsing.",
                checked = policy.renderMarkdownWhileStreaming,
                onChange = { viewModel.setMarkdownWhileStreaming(it) }
            )
            SwitchRow(
                label = "Auto-follow the bottom",
                desc = "Scroll only while you are already near the bottom; scrolling up detaches immediately.",
                checked = policy.autoFollowScroll,
                onChange = { viewModel.setAutoFollowScroll(it) }
            )
        }

        // ---- timeouts ----
        TuningCard(
            title = "Anti-hang guards",
            subtitle = "A wedged engine can never hold the screen: each limit ends the turn and keeps the partial answer."
        ) {
            StepperRow(
                label = "First-token (prefill) timeout",
                desc = "Model loaded but silent for this long → stop and report.",
                value = policy.prefillTimeoutSec,
                min = 10,
                max = 600,
                step = 5,
                unit = "s",
                onChange = {
                    viewModel.setTimeouts(it, policy.idleTokenTimeoutSec, policy.hardTimeoutSec)
                }
            )
            StepperRow(
                label = "Idle between tokens",
                desc = "Gap inside one reply that counts as a stall.",
                value = policy.idleTokenTimeoutSec,
                min = 3,
                max = 120,
                step = 1,
                unit = "s",
                onChange = {
                    viewModel.setTimeouts(policy.prefillTimeoutSec, it, policy.hardTimeoutSec)
                }
            )
            StepperRow(
                label = "Hard wall-clock limit",
                desc = "Absolute maximum duration of a single answer.",
                value = policy.hardTimeoutSec,
                min = 30,
                max = 1800,
                step = 30,
                unit = "s",
                onChange = {
                    viewModel.setTimeouts(policy.prefillTimeoutSec, policy.idleTokenTimeoutSec, it)
                }
            )
            SwitchRow(
                label = "Unload weights when idle",
                desc = "Releases several hundred MB of mapped weights after the keep-alive window so the OS never kills the app mid-answer.",
                checked = policy.releaseModelOnBackground,
                onChange = { viewModel.setReleaseModelOnBackground(it) }
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}

// =========================================================================================
// TAB 2: ENGINE & KEYS — where the answers actually come from
// =========================================================================================
@Composable
private fun EngineKeysTab(viewModel: ChatViewModel) {
    val settings by viewModel.runtimeSettings.collectAsState()
    val pings by viewModel.enginePing.collectAsState()
    val active = settings.activeEngine()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(440.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Pick one source of truth for replies. AgentLM has no scripted fallback: if the engine " +
                "cannot answer, the message shows the real error plus how to fix it.",
            fontSize = 11.sp,
            color = Slate400,
            lineHeight = 15.sp
        )

        settings.engines.forEach { profile ->
            val isActive = profile.id == active.id
            val ping = pings[profile.id]
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isActive) CyanGlow else Slate900)
                    .border(1.dp, if (isActive) Cyan400 else GlassBorder, RoundedCornerShape(14.dp))
                    .clickable { viewModel.setActiveEngine(profile.id) }
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (profile.kind) {
                            EngineKind.LOCAL_NATIVE -> Icons.Default.Memory
                            EngineKind.GEMINI -> Icons.Default.Psychology
                            EngineKind.OPENAI_COMPAT -> Icons.Default.Public
                        },
                        contentDescription = null,
                        tint = if (profile.isReady) Cyan400 else Slate400,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile.label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate100
                        )
                        Text(
                            text = when {
                                profile.kind == EngineKind.LOCAL_NATIVE -> viewModel.nativeEngineSummary
                                profile.baseUrl.isNotBlank() -> profile.baseUrl
                                else -> profile.kind.label
                            },
                            fontSize = 10.sp,
                            color = Slate400,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (profile.isReady) Emerald400.copy(alpha = 0.16f)
                                else Rose500.copy(alpha = 0.16f)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (profile.isReady) "READY" else "SETUP",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (profile.isReady) Emerald400 else Rose500
                        )
                    }
                }

                if (ping != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = (if (ping.ok) "✓ " else "✗ ") + ping.message,
                        fontSize = 10.sp,
                        color = if (ping.ok) Emerald400 else Rose500
                    )
                    ping.detail?.let {
                        Text(text = it, fontSize = 9.sp, color = Slate400, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        // ---- editor for the selected profile ----
        TuningCard(
            title = "Configure ${active.label}",
            subtitle = if (active.kind == EngineKind.LOCAL_NATIVE)
                "Runs the weight file you downloaded in Model Hub, on this device."
            else
                "Works with Ollama (http://PC-IP:11434/v1), llama.cpp server (-c 4096 --host 0.0.0.0), " +
                    "LM Studio, vLLM, OpenRouter, Groq or the Hugging Face router."
        ) {
            var baseUrl by remember(active.id) { mutableStateOf(active.baseUrl) }
            var modelId by remember(active.id) { mutableStateOf(active.modelId) }
            var apiKey by remember(active.id) { mutableStateOf(active.apiKey) }

            if (active.kind != EngineKind.LOCAL_NATIVE) {
                LabeledField(
                    label = "Base URL",
                    value = baseUrl,
                    placeholder = "http://192.168.1.20:11434/v1",
                    onValueChange = { baseUrl = it }
                )
                LabeledField(
                    label = "Model id / tag",
                    value = modelId,
                    placeholder = "qwen2.5:0.5b  •  gemini-2.5-flash",
                    onValueChange = { modelId = it }
                )
                LabeledField(
                    label = "API key",
                    value = apiKey,
                    placeholder = "leave empty for local servers",
                    onValueChange = { apiKey = it }
                )

                if (active.kind == EngineKind.GEMINI) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (EngineProfile.buildTimeGeminiKey.isNotEmpty())
                            "A GEMINI_API_KEY was baked in from .env at build time — leave this field " +
                            "empty to use it, or type a key to override."
                        else
                            "No key available. Type one here, or put GEMINI_API_KEY in .env and rebuild.",
                        fontSize = 10.sp,
                        color = if (EngineProfile.buildTimeGeminiKey.isNotEmpty()) Emerald400 else Amber400,
                        lineHeight = 14.sp
                    )
                }

                if (active.needsCleartext) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Plain HTTP to a LAN address is blocked in release builds by " +
                            "res/xml/network_security_config.xml. Use https://, or install a debug " +
                            "build while testing Ollama / llama.cpp on your machine.",
                        fontSize = 10.sp,
                        color = Amber400,
                        lineHeight = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniButton(
                        label = "Save",
                        accent = true,
                        onClick = {
                            viewModel.saveEngineProfile(
                                active.copy(
                                    baseUrl = baseUrl.trim(),
                                    modelId = modelId.trim(),
                                    apiKey = apiKey.trim()
                                )
                            )
                        }
                    )
                    MiniButton(
                        label = "Test connection",
                        accent = false,
                        onClick = { viewModel.testEngine(active.id) }
                    )
                }
            } else {
                Text(
                    text = viewModel.nativeEngineSummary,
                    fontSize = 11.sp,
                    color = Slate300,
                    lineHeight = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Preferred quantization for downloads",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Slate100
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Q2_K", "Q3_K_M", "Q4_K_S", "Q4_K_M", "Q5_K_M", "Q8_0").chunked(3).forEach { rowQuants ->
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowQuants.forEach { quant ->
                            val selected = settings.preferredHfQuant == quant
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) Cyan400 else Slate800)
                                    .clickable {
                                        viewModel.updateSettings { current ->
                                            current.copy(
                                                preferredHfQuant = quant,
                                                policy = current.policy.copy(quantization = quant)
                                            )
                                        }
                                    }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = quant,
                                    fontSize = 10.sp,
                                    color = if (selected) Slate950 else Slate300,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Model Hub picks the largest file that still fits the RAM budget measured above, " +
                    "so a download cannot leave the app short of memory while it streams.",
                fontSize = 10.sp,
                color = Slate400,
                lineHeight = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}

// ---- shared bits for the two new tabs ----
@Composable
private fun TuningCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Slate900)
            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
            .padding(13.dp)
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Slate100
        )
        Text(
            text = subtitle,
            fontSize = 10.sp,
            color = Slate400,
            lineHeight = 13.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
        )
        content()
    }
}

@Composable
private fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValue: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, fontSize = 11.sp, color = Slate300)
            Text(
                text = valueText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Cyan300
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValue,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Cyan400,
                activeTrackColor = Cyan400,
                inactiveTrackColor = Slate800
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StepperRow(
    label: String,
    desc: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
    step: Int = 1,
    unit: String = ""
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, fontSize = 11.sp, color = Slate300)
                Text(text = desc, fontSize = 9.sp, color = Slate400, lineHeight = 12.sp)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Slate800)
                    .clickable { onChange((value - step).coerceIn(min, max)) }
                    .padding(horizontal = 11.dp, vertical = 6.dp)
            ) {
                Text(text = "−", fontSize = 14.sp, color = Slate100, fontWeight = FontWeight.Bold)
            }
            Text(
                text = "$value$unit",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Cyan300,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Slate800)
                    .clickable { onChange((value + step).coerceIn(min, max)) }
                    .padding(horizontal = 11.dp, vertical = 6.dp)
            ) {
                Text(text = "+", fontSize = 14.sp, color = Slate100, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SwitchRow(
    label: String,
    desc: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontSize = 11.sp, color = Slate100, fontWeight = FontWeight.Medium)
            Text(text = desc, fontSize = 9.sp, color = Slate400, lineHeight = 12.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Slate950,
                checkedTrackColor = Cyan400,
                uncheckedThumbColor = Slate400,
                uncheckedTrackColor = Slate800
            )
        )
    }
}

@Composable
private fun MiniButton(
    label: String,
    accent: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (accent) Cyan400 else GlassSurface)
            .border(1.dp, if (accent) Cyan400 else GlassBorder, RoundedCornerShape(9.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (accent) Slate950 else Slate300
        )
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(text = label, fontSize = 10.sp, color = Slate400, modifier = Modifier.padding(bottom = 3.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            placeholder = { Text(text = placeholder, fontSize = 11.sp, color = Slate400) },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Slate100),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Cyan400,
                unfocusedBorderColor = GlassBorder,
                focusedTextColor = Slate100,
                unfocusedTextColor = Slate100,
                cursorColor = Cyan400
            )
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 10.sp, color = Slate400)
        Text(
            text = value,
            fontSize = 10.sp,
            color = Slate300,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(190.dp)
        )
    }
}
