package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DeviceSpecs
import com.example.model.DownloadStatus
import com.example.model.HFModelConfig
import com.example.model.ModelCatalog
import com.example.model.ModelDownloadProgress
import com.example.model.ModelType
import com.example.ui.theme.Amber400
import com.example.ui.theme.Cyan300
import com.example.ui.theme.Cyan400
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.Emerald400
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Pink500
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate850
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectorSheet(
    isOpen: Boolean,
    currentModel: HFModelConfig,
    deviceSpecs: DeviceSpecs,
    downloadStates: Map<String, ModelDownloadProgress> = emptyMap(),
    hfSearchResults: List<HFModelConfig> = emptyList(),
    isSearchingHf: Boolean = false,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    onSearchQueryChange: (String) -> Unit = {},
    onClearHfSearch: () -> Unit = {},
    onDismiss: () -> Unit,
    onSelectModel: (HFModelConfig) -> Unit,
    onDownloadModel: (HFModelConfig) -> Unit = {},
    onStartUsingModel: (HFModelConfig) -> Unit = {},
    onDeleteDownloadedModel: (String) -> Unit = {},
    onPauseDownload: (String) -> Unit = {},
    onCancelDownload: (String) -> Unit = {}
) {
    if (!isOpen) return

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ALL") }
    var customUrlInput by remember { mutableStateOf("") }
    var customTypeError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            onSearchQueryChange(searchQuery)
        } else {
            onClearHfSearch()
        }
    }

    // Helper for robust query matching (supports "dolphin -qwen", "dolphin-qwen", etc.)
    fun matchesModelQuery(model: HFModelConfig, query: String): Boolean {
        if (query.isBlank()) return true
        val clean = query.lowercase().trim()
        val tokens = clean.split(Regex("[\\s\\-_,]+")).filter { it.isNotBlank() }
        val searchable = (model.name + " " + model.id + " " + model.description + " " + model.tags.joinToString(" ")).lowercase()

        if (searchable.contains(clean)) return true
        if (tokens.isNotEmpty() && tokens.all { searchable.contains(it) }) return true
        return false
    }

    val filteredPresets = remember(searchQuery, selectedCategory, downloadStates) {
        ModelCatalog.PRESET_MODELS.filter { model ->
            val matchesCategory = when (selectedCategory) {
                "ALL" -> true
                "DOWNLOADED" -> downloadStates[model.id]?.status == DownloadStatus.DOWNLOADED
                "VISION" -> model.supportsVision
                "RECOMMENDED" -> model.id == deviceSpecs.recommendedModelId
                "INSTANT" -> model.type == ModelType.INSTANT
                "UNCENSORED" -> model.type == ModelType.UNCENSORED || model.tags.any { it.contains("uncensored") || it.contains("dolphin") }
                "CODER" -> model.type == ModelType.CODER
                "STANDARD" -> model.type == ModelType.STANDARD
                "HF_LIVE" -> false
                else -> true
            }
            val matchesSearch = matchesModelQuery(model, searchQuery)
            matchesCategory && matchesSearch
        }
    }

    val liveSearchResults = remember(hfSearchResults, searchQuery, downloadStates) {
        // Filter out items already in filteredPresets to avoid duplicate cards
        val presetIds = ModelCatalog.PRESET_MODELS.map { it.id.lowercase() }.toSet()
        hfSearchResults.filter { !presetIds.contains(it.id.lowercase()) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Slate900,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Brush.linearGradient(listOf(Amber400, Rose500))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = "Hugging Face",
                            tint = Slate950,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Hugging Face Model Hub",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate100
                        )
                        Text(
                            text = "Search all Hugging Face & Uncensored Dolphin models",
                            fontSize = 11.sp,
                            color = Slate400
                        )
                    }
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

            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Hardware specs banner
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Slate950)
                            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Device Acceleration",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate300
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Cyan400.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Vulkan / WebGPU Ready",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Cyan400
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${deviceSpecs.cores} CPU Cores • ~${deviceSpecs.memoryEstimateGB} GB RAM • Tier ${deviceSpecs.tier.uppercase(Locale.ROOT)}",
                            fontSize = 12.sp,
                            color = Slate100,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = deviceSpecs.reason,
                            fontSize = 11.sp,
                            color = Slate400,
                            modifier = Modifier.padding(top = 2.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Indigo500.copy(alpha = 0.35f), Cyan400.copy(alpha = 0.25f))
                                    )
                                )
                                .border(1.dp, Cyan400.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .clickable {
                                    val rec = ModelCatalog.PRESET_MODELS.find { it.id == deviceSpecs.recommendedModelId }
                                    if (rec != null) {
                                        onStartUsingModel(rec)
                                        onDismiss()
                                    }
                                }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Use Recommended",
                                tint = Amber400,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Use Recommended (${deviceSpecs.recommendedModelId.split("/").last()})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate100
                            )
                        }
                    }
                }

                // Search Bar & Filter Chips
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text("Search 'dolphin -qwen', 'uncensored', 'qwen', 'coder'...", fontSize = 12.sp, color = Slate400)
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = "Search", tint = Cyan400, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isSearchingHf) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = Cyan400,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = Slate400, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Slate850,
                                unfocusedContainerColor = GlassSurface,
                                focusedBorderColor = Cyan400,
                                unfocusedBorderColor = GlassBorder,
                                focusedTextColor = Slate100,
                                unfocusedTextColor = Slate100
                            )
                        )

                        // Quick Search Presets Tags
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val quickKeywords = listOf(
                                "🐬 dolphin -qwen" to "dolphin -qwen",
                                "🔓 uncensored" to "uncensored",
                                "⚡ qwen 2.5" to "qwen 2.5",
                                "🧠 deepseek-r1" to "deepseek r1",
                                "💻 coder" to "coder",
                                "🖼️ vision" to "vision",
                                "📦 smollm" to "smollm"
                            )
                            for ((label, queryVal) in quickKeywords) {
                                val isMatched = searchQuery.lowercase().trim() == queryVal
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (isMatched) Pink500.copy(alpha = 0.25f) else Slate850)
                                        .border(1.dp, if (isMatched) Pink500 else Slate700, RoundedCornerShape(20.dp))
                                        .clickable { searchQuery = queryVal }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isMatched) Pink500 else Slate300
                                    )
                                }
                            }
                        }

                        // Filter Categories Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val categories = listOf(
                                "ALL" to "All Models",
                                "UNCENSORED" to "🔓 Uncensored & Dolphin",
                                "DOWNLOADED" to "💾 Downloaded",
                                "VISION" to "🖼️ Vision (Images)",
                                "RECOMMENDED" to "⭐ Recommended",
                                "INSTANT" to "⚡ Instant",
                                "CODER" to "💻 Coder",
                                "STANDARD" to "🧠 Standard"
                            )
                            for ((key, label) in categories) {
                                val isSelected = selectedCategory == key
                                val bg = if (isSelected) Cyan400.copy(alpha = 0.22f) else GlassSurface
                                val border = if (isSelected) Cyan400 else GlassBorder
                                val textColor = if (isSelected) Cyan300 else Slate400

                                Row(
                                    modifier = Modifier
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(bg)
                                        .border(1.dp, border, RoundedCornerShape(8.dp))
                                        .clickable { selectedCategory = key }
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = textColor
                                    )
                                }
                            }
                        }
                    }
                }

                // Section: Verified Preset Models
                if (filteredPresets.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Verified On-Device Models (${filteredPresets.size})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate300
                            )
                            if (searchQuery.isNotBlank()) {
                                Text(
                                    text = "Matching '$searchQuery'",
                                    fontSize = 11.sp,
                                    color = Cyan400
                                )
                            }
                        }
                    }

                    items(filteredPresets) { preset ->
                        ModelCardItem(
                            preset = preset,
                            currentModel = currentModel,
                            deviceSpecs = deviceSpecs,
                            downloadStates = downloadStates,
                            onSelectModel = onSelectModel,
                            onDownloadModel = onDownloadModel,
                            onStartUsingModel = onStartUsingModel,
                            onDeleteDownloadedModel = onDeleteDownloadedModel,
                            onPauseDownload = onPauseDownload,
                            onCancelDownload = onCancelDownload,
                            onDismiss = onDismiss
                        )
                    }
                }

                // Section: Live Hugging Face Search Results
                if (liveSearchResults.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Amber400)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Hugging Face Live Repositories (${liveSearchResults.size})",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Amber400
                                    )
                                }
                                Text(
                                    text = "Online Hub",
                                    fontSize = 10.sp,
                                    color = Slate400,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = "Found from Hugging Face public repository index for '$searchQuery'",
                                fontSize = 11.sp,
                                color = Slate400,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    items(liveSearchResults) { liveModel ->
                        ModelCardItem(
                            preset = liveModel,
                            currentModel = currentModel,
                            deviceSpecs = deviceSpecs,
                            downloadStates = downloadStates,
                            onSelectModel = onSelectModel,
                            onDownloadModel = onDownloadModel,
                            onStartUsingModel = onStartUsingModel,
                            onDeleteDownloadedModel = onDeleteDownloadedModel,
                            onPauseDownload = onPauseDownload,
                            onCancelDownload = onCancelDownload,
                            onDismiss = onDismiss
                        )
                    }
                }

                // Empty state if nothing matches
                if (filteredPresets.isEmpty() && liveSearchResults.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(GlassSurface)
                                .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (isSearchingHf) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = Cyan400,
                                    strokeWidth = 3.dp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Searching Hugging Face for '$searchQuery'...",
                                    fontSize = 13.sp,
                                    color = Slate300,
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "No results",
                                    tint = Slate400,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "No models found for \"$searchQuery\"",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Slate100
                                )
                                Text(
                                    text = "Try searching 'dolphin -qwen', 'uncensored', or enter a direct Hugging Face ID below.",
                                    fontSize = 12.sp,
                                    color = Slate400,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Custom Hugging Face Model Input Section
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Slate950)
                            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Custom Hugging Face Repo / URL",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate300
                        )

                        OutlinedTextField(
                            value = customUrlInput,
                            onValueChange = {
                                customUrlInput = it
                                customTypeError = null
                            },
                            placeholder = {
                                Text("e.g. cognitivecomputations/dolphin-2.9.2-qwen2-7b", fontSize = 12.sp, color = Slate400)
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Slate850,
                                unfocusedContainerColor = Slate900,
                                focusedBorderColor = Cyan400,
                                unfocusedBorderColor = Slate700,
                                focusedTextColor = Slate100,
                                unfocusedTextColor = Slate100
                            )
                        )

                        if (customTypeError != null) {
                            Text(customTypeError!!, fontSize = 11.sp, color = Rose500)
                        }

                        Button(
                            onClick = {
                                val rawId = ModelCatalog.extractHfModelId(customUrlInput)
                                if (rawId.isBlank()) {
                                    customTypeError = "Please enter a valid Hugging Face repo ID or URL"
                                    return@Button
                                }
                                val lowerId = rawId.lowercase()
                                val isDolphin = lowerId.contains("dolphin")
                                val isUncensored = isDolphin || lowerId.contains("uncensored") || lowerId.contains("unfiltered")

                                val customConfig = HFModelConfig(
                                    id = rawId,
                                    url = if (rawId.startsWith("http")) rawId else "https://huggingface.co/$rawId",
                                    name = rawId.split("/").getOrElse(1) { rawId }.replace("-", " "),
                                    size = "~350-700 MB",
                                    type = if (isUncensored) ModelType.UNCENSORED else ModelType.CUSTOM,
                                    description = "Custom Hugging Face model ($rawId)",
                                    badge = if (isDolphin) "Dolphin Custom" else if (isUncensored) "Uncensored Custom" else "Custom ONNX",
                                    supportsVision = lowerId.contains("vl") || lowerId.contains("vision"),
                                    supportsFiles = true
                                )
                                onStartUsingModel(customConfig)
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Cyan400,
                                contentColor = Slate950
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Load",
                                tint = Slate950,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Load & Start Custom Model", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun ModelCardItem(
    preset: HFModelConfig,
    currentModel: HFModelConfig,
    deviceSpecs: DeviceSpecs,
    downloadStates: Map<String, ModelDownloadProgress>,
    onSelectModel: (HFModelConfig) -> Unit,
    onDownloadModel: (HFModelConfig) -> Unit,
    onStartUsingModel: (HFModelConfig) -> Unit,
    onDeleteDownloadedModel: (String) -> Unit,
    onPauseDownload: (String) -> Unit = {},
    onCancelDownload: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    val isSelected = preset.id == currentModel.id
    val isRecommended = preset.id == deviceSpecs.recommendedModelId
    val dlState = downloadStates[preset.id]
    val isDownloaded = dlState?.status == DownloadStatus.DOWNLOADED
    val isDownloading = dlState?.status == DownloadStatus.DOWNLOADING

    val typeColor = when (preset.type) {
        ModelType.INSTANT -> Amber400
        ModelType.UNCENSORED -> Pink500
        ModelType.CODER -> Emerald400
        ModelType.STANDARD -> Indigo500
        ModelType.VISION -> Cyan400
        ModelType.CUSTOM -> Amber400
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) CyanGlow else GlassSurface)
            .border(1.dp, if (isSelected) Cyan400 else GlassBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        // Title row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(typeColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (preset.type) {
                            ModelType.INSTANT -> Icons.Default.Speed
                            ModelType.UNCENSORED -> Icons.Default.Security
                            ModelType.CODER -> Icons.Default.Code
                            ModelType.STANDARD -> Icons.Default.Memory
                            ModelType.VISION -> Icons.Default.Image
                            ModelType.CUSTOM -> Icons.Default.Tune
                        },
                        contentDescription = preset.name,
                        tint = typeColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = preset.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate100
                    )
                    Text(
                        text = preset.id,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Slate400
                    )
                }
            }

            if (isSelected) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Cyan400)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Active", tint = Slate950, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Active", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate950)
                }
            } else if (isRecommended) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Amber400.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("Recommended", fontSize = 10.sp, color = Amber400, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Capabilities & Spec Badges
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Slate800)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(preset.size, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Slate300)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(typeColor.copy(alpha = 0.2f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(preset.type.label.uppercase(Locale.ROOT), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = typeColor)
            }
            if (preset.badge != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Pink500.copy(alpha = 0.18f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(preset.badge, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Pink500)
                }
            }
            if (preset.supportsVision) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Indigo500.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = Cyan300, modifier = Modifier.size(11.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Vision/OCR", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Cyan300)
                }
            }
            if (preset.supportsFiles) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Emerald400.copy(alpha = 0.18f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FolderZip, contentDescription = null, tint = Emerald400, modifier = Modifier.size(11.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("ZIP & Files", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Emerald400)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = preset.description,
            fontSize = 12.sp,
            color = Slate400,
            lineHeight = 16.sp
        )

        // Exact file the downloader will fetch (resolved against the real repo tree), so the
        // advertised size and the downloaded size can never disagree.
        if (preset.preferredFile.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "weights: " + preset.preferredFile + "  •  " + preset.size,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = if (preset.isQuantRecommended) Emerald400 else Slate400,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Real transfer state: bytes, speed, ETA, pause/resume and errors from the downloader
        val errorText = dlState?.error
        if (!errorText.isNullOrBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Rose500.copy(alpha = 0.09f))
                    .border(1.dp, Rose500.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(9.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Rose500,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Download problem", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Rose500)
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(text = errorText, fontSize = 10.sp, color = Slate300, lineHeight = 14.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Slate800)
                            .clickable { onDownloadModel(preset) }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Text("Retry", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Cyan300)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Slate800)
                            .clickable { onCancelDownload(preset.id) }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Text("Dismiss", fontSize = 10.sp, color = Slate300)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (isDownloading) {
            val paused = dlState?.isPaused == true
            val progressAnim by animateFloatAsState(
                targetValue = dlState?.progress ?: 0f,
                animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing),
                label = "dlProgress"
            )
            val doneMb = (dlState?.downloadedBytes ?: 0L) / 1_048_576.0
            val totalMb = (dlState?.totalBytes ?: 0L) / 1_048_576.0
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Slate950)
                    .border(1.dp, Slate800, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (paused) "Paused — partial file kept for resume"
                        else "Streaming weights from Hugging Face…",
                        fontSize = 11.sp,
                        color = if (paused) Amber400 else Cyan300,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (paused) "${(progressAnim * 100).toInt()}%"
                        else "${(progressAnim * 100).toInt()}% • %.1f MB/s • ETA %s".format(
                            dlState?.speedMbps ?: 0.0,
                            dlState?.etaLabel() ?: "--:--"
                        ),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Slate300
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = dlState?.fileName?.takeIf { it.isNotBlank() }?.let { "File: $it  •  " }.orEmpty() +
                        "%.0f / %.0f MB".format(doneMb, if (totalMb > 0) totalMb else 0.0),
                    fontSize = 9.sp,
                    color = Slate400,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progressAnim },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (paused) Amber400 else Cyan400,
                    trackColor = Slate800,
                )
                Spacer(modifier = Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Slate800)
                            .clickable { if (paused) onDownloadModel(preset) else onPauseDownload(preset.id) }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (paused) "Resume" else "Pause",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (paused) Emerald400 else Slate300
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Slate800)
                            .clickable { onCancelDownload(preset.id) }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Text("Cancel", fontSize = 10.sp, color = Rose500)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ACTION BUTTONS (Download / Start Using Model)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isDownloaded && !isDownloading) {
                // Download Button
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Slate800)
                        .border(1.dp, Cyan400.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable {
                            onDownloadModel(preset)
                        }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download",
                        tint = Cyan400,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Download (${preset.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Cyan300
                    )
                }

                // Direct Select via API
                Row(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(GlassSurface)
                        .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
                        .clickable {
                            onSelectModel(preset)
                            onDismiss()
                        }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select",
                        fontSize = 11.sp,
                        color = Slate300
                    )
                }
            } else if (isDownloading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Slate850)
                        .border(1.dp, Slate700, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Downloading ONNX shards...",
                        fontSize = 11.sp,
                        color = Cyan400,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                // Model is DOWNLOADED -> Show "Start using model"
                val buttonBrush = if (isSelected) {
                    Brush.linearGradient(listOf(Cyan400.copy(alpha = 0.25f), Cyan400.copy(alpha = 0.25f)))
                } else {
                    Brush.linearGradient(listOf(Cyan400, Indigo500))
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(buttonBrush)
                        .border(1.dp, if (isSelected) Cyan400 else Color.Transparent, RoundedCornerShape(9.dp))
                        .clickable {
                            onStartUsingModel(preset)
                            onDismiss()
                        }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.Check else Icons.Default.PlayArrow,
                        contentDescription = "Start Using Model",
                        tint = if (isSelected) Cyan400 else Slate950,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isSelected) "Currently Active (Local ONNX)" else "Start Using Model (Local)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Cyan300 else Slate950
                    )
                }

                // Delete downloaded local cache button
                IconButton(
                    onClick = { onDeleteDownloadedModel(preset.id) },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(GlassSurface)
                        .border(1.dp, GlassBorder, RoundedCornerShape(9.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete local weights",
                        tint = Slate400,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
