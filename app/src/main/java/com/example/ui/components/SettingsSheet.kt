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
import com.example.model.HFModelConfig
import com.example.model.ModelCatalog
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
    onOpenModelHub: () -> Unit
) {
    if (!isOpen) return

    var selectedTab by remember { mutableIntStateOf(0) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    var showCacheClearedToast by remember { mutableStateOf(false) }

    val tabs = listOf(
        "Hardware Detective",
        "GPU / CPU",
        "Cache & Storage",
        "History",
        "Personas",
        "About"
    )

    val ramRecommendations = remember(deviceSpecs) {
        ModelCatalog.getRamRecommendations(deviceSpecs)
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
                            text = "Settings & Device Detective",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate100
                        )
                        Text(
                            text = "Hardware diagnostics, memory analysis & storage",
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
                    1 -> GpuCpuTab(
                        deviceSpecs = deviceSpecs,
                        isGpuEnabled = isGpuEnabled,
                        cpuThreads = cpuThreads,
                        onToggleGpu = onToggleGpu,
                        onSetCpuThreads = onSetCpuThreads
                    )
                    2 -> CacheStorageTab(
                        cacheSizeBytes = cacheSizeBytes,
                        onClearCache = {
                            onClearCache()
                            showCacheClearedToast = true
                        }
                    )
                    3 -> HistoryTab(
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
                    4 -> PersonasTab(
                        currentAgent = currentAgent,
                        onSelectAgent = { agent ->
                            onSelectAgent(agent)
                            onDismiss()
                        }
                    )
                    5 -> AboutTab()
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
    onSetCpuThreads: (Int) -> Unit
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
                    text = if (isGpuEnabled) "Using Vulkan / NNAPI hardware shaders for ~3.5x token throughput"
                    else "Using standard CPU fallback execution",
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
                text = "Number of active CPU cores dedicated to ONNX local tensor calculations (Device has ${deviceSpecs.cores} cores).",
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
                    text = "ONNX Runtime Mobile v1.20 (XNNPACK + Vulkan)",
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

            Text(
                text = "Includes HuggingFace tokenizer token maps, temporary uploaded ZIP extractions, image tensors, and KV-cache buffers.",
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
private fun AboutTab() {
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
                        text = "Version 2.5.0 • Mobile ONNX Edition",
                        fontSize = 11.sp,
                        color = Slate400
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "An ultra-fast on-device AI assistant engineered with Alibaba's Qwen 2.5, Hugging Face ONNX Runtime, and multimodal code/ZIP analysis.",
                fontSize = 12.sp,
                color = Slate300,
                lineHeight = 17.sp
            )

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
