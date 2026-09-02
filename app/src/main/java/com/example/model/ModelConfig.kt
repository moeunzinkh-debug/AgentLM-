package com.example.model

enum class ModelType(val label: String) {
    INSTANT("Instant"),
    UNCENSORED("Uncensored"),
    CODER("Coder"),
    VISION("Vision & Multimodal"),
    STANDARD("Standard"),
    CUSTOM("Custom")
}

enum class DownloadStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED
}

data class ModelDownloadProgress(
    val modelId: String,
    val status: DownloadStatus = DownloadStatus.NOT_DOWNLOADED,
    val progress: Float = 0f, // 0.0 to 1.0
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    /** Real measured throughput in MB/s (updated on a time budget, not per chunk). */
    val speedMbps: Double = 0.0,
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long = -1L,
    val isPaused: Boolean = false,
    val error: String? = null,
    val fileName: String = "",
    val filePath: String = ""
) {
    val hasPartialData: Boolean get() = isPaused && downloadedBytes > 0L

    fun etaLabel(): String {
        if (etaSeconds <= 0L) return "--:--"
        val m = etaSeconds / 60
        val sec = etaSeconds % 60
        return "%d:%02d".format(m, sec)
    }
}

data class HFModelConfig(
    val id: String,
    val url: String,
    val name: String,
    val size: String,
    val type: ModelType,
    val description: String,
    val badge: String? = null,
    val tags: List<String> = emptyList(),
    val minTier: String = "medium",
    val supportsVision: Boolean = false,
    val supportsFiles: Boolean = true,
    val sizeBytes: Long = 367001600L,
    /** Weight container actually resolved on Hugging Face: "gguf" | "litertlm" | "task" | "onnx". */
    val kind: String = "gguf",
    /** Exact file name inside the repo that was chosen for this device's RAM budget. */
    val preferredFile: String = "",
    /** True when [preferredFile] matched the quantization recommended for this device. */
    val isQuantRecommended: Boolean = false
)

data class RamModelRecommendation(
    val model: HFModelConfig,
    val requiredRamGb: Int,
    val isOptimal: Boolean,
    val isSupported: Boolean,
    val statusLabel: String,
    val tip: String
)

data class DeviceSpecs(
    val hasHardwareGpu: Boolean,
    /** SoC string from Build.SOC_MANUFACTURER / SOC_MODEL (API 31+) or Build.HARDWARE. */
    val chipset: String = "",
    val deviceModel: String = "",
    val deviceAbis: List<String> = emptyList(),
    /** Real measured memory, in MB (not the JVM heap ceiling). */
    val totalRamMb: Long = 0L,
    val availRamMb: Long = 0L,
    val freeDiskMb: Long = 0L,
    val vulkanComputeLevel: Int = 0,
    val gpuRenderer: String = "Vulkan / Adreno NPU",
    val cpuArch: String = "ARM64-v8a",
    val cores: Int,
    val memoryEstimateGB: Int,
    val totalRamGB: Int = 8,
    val availableRamGB: Int = 5,
    val tier: String,
    val recommendedModelId: String,
    val reason: String
)

object ModelCatalog {
    val PRESET_MODELS = listOf(
        HFModelConfig(
            id = "onnx-community/Qwen2.5-0.5B-Instruct",
            url = "https://huggingface.co/onnx-community/Qwen2.5-0.5B-Instruct",
            name = "Qwen 2.5 (0.5B Instruct)",
            size = "~350 MB (Q4)",
            type = ModelType.INSTANT,
            description = "Alibaba's fast, versatile, multilingual instruction model. Super lightweight & responsive.",
            badge = "Balanced Pick",
            tags = listOf("instant", "fast", "multilingual", "balanced", "qwen", "recommended", "alibaba"),
            minTier = "medium",
            supportsVision = false,
            supportsFiles = true,
            sizeBytes = 367001600L
        ),
        HFModelConfig(
            id = "onnx-community/Qwen2-VL-2B-Instruct",
            url = "https://huggingface.co/onnx-community/Qwen2-VL-2B-Instruct",
            name = "Qwen 2-VL (Vision & Files 2B)",
            size = "~1.2 GB (Q4)",
            type = ModelType.VISION,
            description = "Vision-Language model capable of reading Images, OCR text, UI mockups, documents, and ZIP project files.",
            badge = "Vision & OCR",
            tags = listOf("vision", "multimodal", "image", "ocr", "qwen", "files", "zip"),
            minTier = "high",
            supportsVision = true,
            supportsFiles = true,
            sizeBytes = 1258291200L
        ),
        HFModelConfig(
            id = "onnx-community/Qwen2.5-Coder-0.5B-Instruct",
            url = "https://huggingface.co/onnx-community/Qwen2.5-Coder-0.5B-Instruct",
            name = "Qwen 2.5 Coder (0.5B)",
            size = "~360 MB (Q4)",
            type = ModelType.CODER,
            description = "Specialized code generation, debugging, Kotlin/Python developer assistant & ZIP repository extractor.",
            badge = "Code & ZIP",
            tags = listOf("coder", "code", "python", "kotlin", "developer", "instant", "qwen", "zip"),
            minTier = "medium",
            supportsVision = false,
            supportsFiles = true,
            sizeBytes = 377487360L
        ),
        HFModelConfig(
            id = "onnx-community/SmolVLM-Instruct",
            url = "https://huggingface.co/onnx-community/SmolVLM-Instruct",
            name = "SmolVLM (Mobile Vision)",
            size = "~480 MB (Q4)",
            type = ModelType.VISION,
            description = "Ultra-efficient Hugging Face vision model designed for fast on-device image captioning, OCR, and multimodal reasoning.",
            badge = "Light Vision",
            tags = listOf("vision", "smol", "image", "multimodal", "fast"),
            minTier = "medium",
            supportsVision = true,
            supportsFiles = true,
            sizeBytes = 503316480L
        ),
        HFModelConfig(
            id = "cognitivecomputations/dolphin-2.9.3-qwen2-0.5b",
            url = "https://huggingface.co/cognitivecomputations/dolphin-2.9.3-qwen2-0.5b",
            name = "Dolphin 2.9.3 Qwen 2 (0.5B Uncensored)",
            size = "~360 MB (Q4)",
            type = ModelType.UNCENSORED,
            description = "Cognitive Computations' flagship Dolphin dataset fine-tuned on Qwen2 0.5B. Completely uncensored, unfiltered, and highly creative.",
            badge = "Dolphin Uncensored",
            tags = listOf("dolphin", "dolphin-qwen", "dolphin qwen", "qwen", "qwen2", "uncensored", "unfiltered", "cognitivecomputations", "instant", "open"),
            minTier = "medium",
            supportsVision = false,
            supportsFiles = true,
            sizeBytes = 377487360L
        ),
        HFModelConfig(
            id = "cognitivecomputations/dolphin-2.6-qwen-1.5b",
            url = "https://huggingface.co/cognitivecomputations/dolphin-2.6-qwen-1.5b",
            name = "Dolphin 2.6 Qwen (1.5B Uncensored)",
            size = "~980 MB (Q4)",
            type = ModelType.UNCENSORED,
            description = "Dolphin 2.6 instruction-tuned on Qwen 1.5B. Outstanding unrestricted conversational capability, multi-turn roleplay & zero refusals.",
            badge = "Dolphin 1.5B",
            tags = listOf("dolphin", "dolphin-qwen", "dolphin qwen", "qwen", "qwen1.5", "uncensored", "unfiltered", "cognitivecomputations", "roleplay"),
            minTier = "high",
            supportsVision = false,
            supportsFiles = true,
            sizeBytes = 1027604480L
        ),
        HFModelConfig(
            id = "cognitivecomputations/dolphin-2.9.2-qwen2-7b",
            url = "https://huggingface.co/cognitivecomputations/dolphin-2.9.2-qwen2-7b",
            name = "Dolphin 2.9.2 Qwen 2 (7B Flagship Uncensored)",
            size = "~4.2 GB (Q4)",
            type = ModelType.UNCENSORED,
            description = "State-of-the-art Eric Hartford / Dolphin 2.9.2 Qwen2 7B model. Deep uncensored synthetic dataset training for complex coding & unrestricted reasoning.",
            badge = "Dolphin Flagship",
            tags = listOf("dolphin", "dolphin-qwen", "dolphin qwen", "qwen", "qwen2", "7b", "uncensored", "unfiltered", "cognitivecomputations"),
            minTier = "high",
            supportsVision = false,
            supportsFiles = true,
            sizeBytes = 4404019200L
        ),
        HFModelConfig(
            id = "Felladrin/onnx-Qwen2-0.5B-Instruct",
            url = "https://huggingface.co/Felladrin/onnx-Qwen2-0.5B-Instruct",
            name = "Qwen 2 (0.5B Uncensored / Open)",
            size = "~350 MB (Q4)",
            type = ModelType.UNCENSORED,
            description = "Uncensored & open weights Qwen 2 fine-tuned with zero refusals for open discussion, files, and unrestrained creative prompts.",
            badge = "Qwen Uncensored",
            tags = listOf("uncensored", "open", "qwen", "qwen2", "unfiltered", "creative", "roleplay", "instant"),
            minTier = "medium",
            supportsVision = false,
            supportsFiles = true,
            sizeBytes = 367001600L
        ),
        HFModelConfig(
            id = "deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B",
            url = "https://huggingface.co/deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B",
            name = "DeepSeek R1 Distill Qwen (1.5B)",
            size = "~1.1 GB (Q4)",
            type = ModelType.CODER,
            description = "DeepSeek R1 reasoning architecture distilled into lightweight Qwen 1.5B. Exceptional mathematical logic & Chain-of-Thought output.",
            badge = "DeepSeek R1",
            tags = listOf("deepseek", "deepseek-r1", "r1", "qwen", "reasoning", "cot", "coder", "logic"),
            minTier = "high",
            supportsVision = false,
            supportsFiles = true,
            sizeBytes = 1153433600L
        ),
        HFModelConfig(
            id = "Felladrin/onnx-Qwen1.5-0.5B-Chat",
            url = "https://huggingface.co/Felladrin/onnx-Qwen1.5-0.5B-Chat",
            name = "Qwen 1.5 (0.5B Unfiltered Chat)",
            size = "~340 MB (Q4)",
            type = ModelType.UNCENSORED,
            description = "Unrestricted open chat model based on Qwen 1.5 architecture. Freeform conversationalist with file analysis.",
            badge = "Qwen Open",
            tags = listOf("uncensored", "qwen", "qwen1.5", "chat", "open", "unfiltered", "instant"),
            minTier = "medium",
            supportsVision = false,
            supportsFiles = true,
            sizeBytes = 356515840L
        ),
        HFModelConfig(
            id = "onnx-community/SmolLM2-135M-Instruct",
            url = "https://huggingface.co/onnx-community/SmolLM2-135M-Instruct",
            name = "SmolLM2 (135M Ultra-Instant)",
            size = "~90 MB (Q4)",
            type = ModelType.INSTANT,
            description = "Ultra-compact sub-100MB model for lightning instant loading on any phone, tablet, or low-spec CPU. Text-focused.",
            badge = "Fastest Sub-100MB",
            tags = listOf("instant", "lightweight", "mobile", "ultra-fast", "smol", "smollm", "smollm2", "sub-100mb"),
            minTier = "low",
            supportsVision = false,
            supportsFiles = false,
            sizeBytes = 94371840L
        ),
        HFModelConfig(
            id = "Felladrin/onnx-SmolLM-135M-Instruct",
            url = "https://huggingface.co/Felladrin/onnx-SmolLM-135M-Instruct",
            name = "SmolLM 135M (Uncensored / Open)",
            size = "~90 MB (Q4)",
            type = ModelType.UNCENSORED,
            description = "Uncensored pocket-sized SmolLM model. Fast, unfiltered output with zero artificial guardrails.",
            badge = "Smol Uncensored",
            tags = listOf("uncensored", "smol", "smollm", "135m", "instant", "open", "unfiltered", "pocket"),
            minTier = "low",
            supportsVision = false,
            supportsFiles = false,
            sizeBytes = 94371840L
        ),
        HFModelConfig(
            id = "onnx-community/SmolLM2-360M-Instruct",
            url = "https://huggingface.co/onnx-community/SmolLM2-360M-Instruct",
            name = "SmolLM2 (360M Instruct)",
            size = "~240 MB (Q4)",
            type = ModelType.INSTANT,
            description = "High-quality compact model from Hugging Face with remarkable reasoning for its small footprint.",
            badge = "Instant 240MB",
            tags = listOf("instant", "smol", "smollm", "smollm2", "fast", "huggingface", "reasoning"),
            minTier = "low",
            supportsVision = false,
            supportsFiles = true,
            sizeBytes = 251658240L
        ),
        HFModelConfig(
            id = "Felladrin/onnx-TinyLlama-1.1B-Chat-v1.0",
            url = "https://huggingface.co/Felladrin/onnx-TinyLlama-1.1B-Chat-v1.0",
            name = "TinyLlama 1.1B (Uncensored / Open)",
            size = "~650 MB (Q4)",
            type = ModelType.UNCENSORED,
            description = "Uncensored, open-domain conversational model trained on wide open datasets with zero refusal guards.",
            badge = "Uncensored 1.1B",
            tags = listOf("uncensored", "open", "creative", "tinyllama", "llama", "unfiltered", "roleplay"),
            minTier = "medium",
            supportsVision = false,
            supportsFiles = true,
            sizeBytes = 681574400L
        ),
        HFModelConfig(
            id = "onnx-community/Llama-3.2-1B-Instruct",
            url = "https://huggingface.co/onnx-community/Llama-3.2-1B-Instruct",
            name = "Llama 3.2 (1B Instruct)",
            size = "~750 MB (Q4)",
            type = ModelType.STANDARD,
            description = "Meta's flagship 1B instruct LLM. Exceptional knowledge retrieval, structured instruction following, and logic.",
            badge = "Meta Flagship",
            tags = listOf("standard", "llama", "meta", "smart", "reasoning", "1b", "flagship"),
            minTier = "high",
            supportsVision = false,
            supportsFiles = true,
            sizeBytes = 786432000L
        )
    )

    val DEFAULT_MODEL = PRESET_MODELS[0]

    /**
     * Builds the device profile from **measured** values (ActivityManager / Build / StatFs)
     * instead of guessing RAM from the JVM heap ceiling.
     */
    fun deviceSpecsFrom(hardware: HardwareInfo): DeviceSpecs {
        val totalRamGB = hardware.totalRamGb
        val availableRamGB = (hardware.availRamMb / 1024.0).toInt().coerceAtLeast(1)
        val tier = hardware.tier
        val recId = when (tier) {
            "high" -> DEFAULT_MODEL.id
            "low" -> PRESET_MODELS.firstOrNull { it.minTier == "low" }?.id ?: DEFAULT_MODEL.id
            else -> PRESET_MODELS.firstOrNull { it.sizeBytes <= 400_000_000L }?.id ?: DEFAULT_MODEL.id
        }
        val renderer = when {
            !hardware.hasVulkan -> "CPU only (no Vulkan device found)"
            hardware.vulkanComputeLevel >= 42 -> "Vulkan ${hardware.vulkanComputeLevel} compute-capable GPU"
            else -> "Vulkan ${hardware.vulkanComputeLevel} (limited compute — CPU preferred)"
        }
        val reason = buildString {
            append("${hardware.manufacturer} ${hardware.model} • ${hardware.chipset} • ")
            append("${hardware.cores} cores • ${hardware.totalRamGb} GB RAM (~${hardware.availRamGb} GB free)")
            if (hardware.powerSaveMode) append(" • battery saver ON")
            if (hardware.thermalStatus >= 3) append(" • thermally throttled")
            append(". ").append(when (tier) {
                "high" -> "Can hold a 1-2B Q4 model with a multi-turn KV cache without swapping."
                "low" -> "Keep answers short and prefer sub-400MB weights to avoid UI stalls."
                else -> "Best served by a sub-1B Q4 model with clamped output length."
            })
        }
        return DeviceSpecs(
            hasHardwareGpu = hardware.vulkanComputeLevel >= 42,
            chipset = hardware.chipset,
            deviceModel = "${hardware.manufacturer} ${hardware.model}",
            deviceAbis = listOf(hardware.abi),
            totalRamMb = hardware.totalRamMb,
            availRamMb = hardware.availRamMb,
            freeDiskMb = hardware.freeDiskMb,
            vulkanComputeLevel = hardware.vulkanComputeLevel,
            gpuRenderer = renderer,
            cpuArch = hardware.abi,
            cores = hardware.cores,
            memoryEstimateGB = totalRamGB,
            totalRamGB = totalRamGB,
            availableRamGB = availableRamGB,
            tier = tier,
            recommendedModelId = recId,
            reason = reason
        )
    }

    fun getRamRecommendations(
        specs: DeviceSpecs,
        hardware: HardwareInfo? = null
    ): List<RamModelRecommendation> {
        val free = if (specs.availRamMb > 0) specs.availRamMb else specs.totalRamGB * 1024L / 2
        return PRESET_MODELS.map { model ->
            // Real weight size + measured KV-cache growth, not a size bucket guess.
            val advice = hardware?.let { ResponseBudgetAdvisor.advise(it, model) }
            val residentMb = advice?.modelResidentMb
                ?: (model.sizeBytes / 1_048_576L * 1.3L).coerceAtLeast(32L)
            val required = ((residentMb + 420L) / 1024.0).let { kotlin.math.ceil(it.toDouble()) }.toInt().coerceAtLeast(2)
            val fits = free >= residentMb + 420L
            val isOptimal = free >= residentMb + 1_100L
            val isSupported = fits
            val ram = specs.totalRamGB
            val statusLabel = when {
                isOptimal -> "Optimal (fast KV cache)"
                isSupported -> "Supported (tight)"
                else -> "Over budget (will stall)"
            }
            val tip = when {
                isOptimal -> "~${residentMb} MB resident leaves ${(free - residentMb) / 1024} GB spare for KV + UI."
                isSupported -> "Only ~${free} MB free vs ${residentMb} MB resident — clamp output length."
                else -> "Needs ~${required} GB free RAM. Use ${advice?.quantization ?: "a smaller"} quantization instead."
            }
            RamModelRecommendation(
                model = model,
                requiredRamGb = required,
                isOptimal = isOptimal,
                isSupported = isSupported,
                statusLabel = statusLabel,
                tip = tip
            )
        }
    }

    fun extractHfModelId(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.contains("huggingface.co/")) {
            val after = trimmed.substringAfter("huggingface.co/").trim('/')
            val parts = after.split('/')
            if (parts.size >= 2) return "${parts[0]}/${parts[1]}"
        }
        return trimmed
    }
}

