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
    val speedMbps: Double = 0.0
)

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
    val sizeBytes: Long = 367001600L
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

    fun detectDevice(): DeviceSpecs {
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(4)
        val maxMemoryMB = (Runtime.getRuntime().maxMemory() / (1024 * 1024)).toInt()
        val totalRamGB = when {
            maxMemoryMB > 512 -> 8
            maxMemoryMB > 256 -> 6
            else -> 4
        }
        val availableRamGB = (totalRamGB * 0.65).toInt().coerceAtLeast(2)
        val tier = if (totalRamGB >= 6 && cores >= 6) "high" else if (cores >= 4) "medium" else "low"
        val recId = when (tier) {
            "high" -> "onnx-community/Qwen2.5-0.5B-Instruct"
            "low" -> "onnx-community/SmolLM2-135M-Instruct"
            else -> "onnx-community/Qwen2.5-0.5B-Instruct"
        }
        val reason = when (tier) {
            "high" -> "Detected flagship multicore hardware ($cores Cores, $totalRamGB GB RAM). Optimal for Qwen 2.5 and Multimodal models."
            "low" -> "Detected battery-saver profile. Recommended lightweight SmolLM2 or Qwen 0.5B for fast responsiveness."
            else -> "Detected balanced mobile profile ($cores Cores, $totalRamGB GB RAM). Recommended Qwen 2.5 0.5B for fast reasoning."
        }
        return DeviceSpecs(
            hasHardwareGpu = true,
            gpuRenderer = "Vulkan / Adreno NPU",
            cpuArch = "ARM64-v8a",
            cores = cores,
            memoryEstimateGB = totalRamGB,
            totalRamGB = totalRamGB,
            availableRamGB = availableRamGB,
            tier = tier,
            recommendedModelId = recId,
            reason = reason
        )
    }

    fun getRamRecommendations(specs: DeviceSpecs): List<RamModelRecommendation> {
        val ram = specs.totalRamGB
        return PRESET_MODELS.map { model ->
            val required = when {
                model.sizeBytes > 1000_000_000L -> 6
                model.sizeBytes > 500_000_000L -> 4
                model.sizeBytes > 200_000_000L -> 3
                else -> 2
            }
            val isOptimal = ram >= required + 1
            val isSupported = ram >= required
            val statusLabel = when {
                isOptimal -> "Optimal (Fastest)"
                isSupported -> "Supported (Normal)"
                else -> "High RAM (May throttle)"
            }
            val tip = when {
                isOptimal -> "Device has plenty of RAM headroom for fast KV-caching."
                isSupported -> "Runs stably on your ${ram}GB device."
                else -> "Needs ~${required}GB free RAM for peak multi-turn context."
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

