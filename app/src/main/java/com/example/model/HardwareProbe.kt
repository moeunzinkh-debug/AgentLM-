package com.example.model

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs

/**
 * Real hardware probing (no mocked numbers).
 *
 * Everything here is read from the platform so the response policy, quantization
 * advice and the "will it freeze?" verdict are based on the actual device rather
 * than on a guessed heap size.
 */
data class HardwareInfo(
    val manufacturer: String,
    val model: String,
    val chipset: String,
    val abi: String,
    val is64Bit: Boolean,
    val cores: Int,
    val totalRamMb: Long,
    val availRamMb: Long,
    val appHeapMb: Long,
    val freeDiskMb: Long,
    val vulkanComputeLevel: Int,
    val hasVulkan: Boolean,
    val powerSaveMode: Boolean,
    val thermalStatus: Int
) {
    val totalRamGb: Int get() = (totalRamMb / 1024.0).toInt().coerceAtLeast(1)
    val availRamGb: Int get() = (availRamMb / 1024.0).toInt().coerceAtLeast(1)

    /**
     * True when the platform declares Vulkan hardware-level support, i.e. a driver that can run
     * compute shaders (CTS requires Vulkan 1.1 compute for this feature). The LiteRT-LM GPU
     * delegate is only worth requesting when this holds; otherwise CPU is genuinely faster.
     */
    val hasVulkanCompute: Boolean get() = hasVulkan && vulkanComputeLevel > 0

    /** True when the SoC is one of the Google Tensor parts with known quant races. */
    val isTensorSoC: Boolean
        get() = chipset.contains("tensor", ignoreCase = true) ||
            model.contains("pixel", ignoreCase = true)

    val isFlagship: Boolean
        get() = totalRamMb >= 7_000 && cores >= 8 && is64Bit && !powerSaveMode

    val isMidRange: Boolean
        get() = !isFlagship && totalRamMb >= 4_000 && cores >= 6 && is64Bit

    /** low / medium / high — matches [DeviceSpecs.tier]. */
    val tier: String
        get() = when {
            isFlagship -> "high"
            isMidRange -> "medium"
            else -> "low"
        }

    companion object {
        private const val TAG = "HardwareProbe"

        fun probe(context: Context): HardwareInfo {
            val app = context.applicationContext
            val rt = Runtime.getRuntime()
            val am = app.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

            val memInfo = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(memInfo)
            val totalRamMb = memInfo.totalMem / (1024L * 1024L)
            val availRamMb = memInfo.availMem / (1024L * 1024L)
            val heapMb = rt.maxMemory() / (1024L * 1024L)

            val diskMb = try {
                val dir = app.getExternalFilesDir(null) ?: app.filesDir
                val stat = StatFs(dir.absolutePath)
                stat.availableBytes / (1024L * 1024L)
            } catch (e: Exception) {
                0L
            }

            val pm = app.packageManager
            val hasVulkan = try {
                pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
            } catch (e: Exception) {
                false
            }
            // FEATURE_VULKAN_HARDWARE_LEVEL is only advertised on devices that expose
            // Vulkan compute 1.1+, which is the threshold the GPU delegate cares about.
            // (PackageManager#getSystemFeatureLevel is intentionally not used: it is not
            // part of every compileSdk surface and adds nothing beyond this flag.)
            val vulkanLevel = if (hasVulkan) 1 else 0

            val power = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val powerSave = try {
                power?.isPowerSaveMode == true
            } catch (e: Exception) {
                false
            }
            val thermal = try {
                if (Build.VERSION.SDK_INT >= 29) power?.currentThermalStatus ?: 0 else 0
            } catch (e: Exception) {
                0
            }

            val chipset = if (Build.VERSION.SDK_INT >= 31) {
                listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL)
                    .filter { it.isNotBlank() && it != Build.UNKNOWN }
                    .joinToString(" ")
            } else {
                Build.HARDWARE ?: Build.UNKNOWN
            }

            val abis = Build.SUPPORTED_ABIS ?: arrayOf("arm64-v8a")
            val primary = abis.firstOrNull() ?: "unknown"

            return HardwareInfo(
                manufacturer = Build.MANUFACTURER ?: "unknown",
                model = Build.MODEL ?: "unknown",
                chipset = if (chipset.isNullOrBlank()) Build.HARDWARE ?: "unknown" else chipset,
                abi = primary,
                is64Bit = primary.contains("64"),
                cores = rt.availableProcessors().coerceAtLeast(1),
                totalRamMb = if (totalRamMb > 0) totalRamMb else (heapMb * 3).coerceAtLeast(1),
                availRamMb = if (availRamMb > 0) availRamMb else (heapMb * 2).coerceAtLeast(1),
                appHeapMb = heapMb,
                freeDiskMb = diskMb,
                vulkanComputeLevel = vulkanLevel,
                hasVulkan = hasVulkan,
                powerSaveMode = powerSave,
                thermalStatus = thermal
            )
        }
    }
}

/**
 * Converts measured hardware into safe generation limits.
 *
 * This is the piece that keeps a long answer from locking the UI thread: the
 * output length, prompt budget and thread count are capped by what the device can
 * actually hold in memory *plus* the KV-cache growth of the selected model.
 */
data class BudgetAdvice(
    val maxOutputTokens: Int,
    val contextTokenBudget: Int,
    val historyTurns: Int,
    val cpuThreads: Int,
    val gpuEnabled: Boolean,
    val singleThreadGuard: Boolean,
    val quantization: String,
    val modelResidentMb: Long,
    val kvPerTokenKb: Int,
    val freeRamAfterLoadMb: Long,
    val verdict: Verdict,
    val warnings: List<String>
) {
    enum class Verdict { OPTIMAL, TIGHT, OVER }
}

object ResponseBudgetAdvisor {

    /** Approx. KV-cache bytes per token, per architecture family (fp16, GQA). */
    private fun kvKbPerToken(paramBillions: Double): Int = when {
        paramBillions <= 0.4 -> 4
        paramBillions <= 0.9 -> 6
        paramBillions <= 2.0 -> 12
        paramBillions <= 4.0 -> 20
        paramBillions <= 8.0 -> 44
        else -> 80
    }

    fun estimateParamsBillions(model: HFModelConfig): Double {
        val haystack = (model.id + " " + model.name).lowercase()
        Regex("(\\d+(?:\\.\\d+)?)\\s*b\\b").find(haystack)?.groupValues?.get(1)?.toDoubleOrNull()?.let {
            return it
        }
        Regex("(\\d+)\\s*m\\b").find(haystack)?.groupValues?.get(1)?.toDoubleOrNull()?.let {
            return (it / 1000.0).coerceAtLeast(0.1)
        }
        // Fall back to the on-disk quantized size: Q4 is roughly 0.55 GB per 3B params.
        return (model.sizeBytes / 1_073_741_824.0 * 5.4).coerceIn(0.1, 70.0)
    }

    fun advise(hardware: HardwareInfo, model: HFModelConfig?): BudgetAdvice {
        val warnings = mutableListOf<String>()
        val params = model?.let { estimateParamsBillions(it) } ?: 0.5
        val kvKb = kvKbPerToken(params)
        val fileBytes = model?.sizeBytes ?: (params * 0.62 * 1_073_741_824.0).toLong()
        // Weights stay memory-mapped; activations + KV dominate the growth curve.
        val residentMb = (fileBytes / 1_048_576.0 * 1.28).toLong().coerceAtLeast(24L)

        // Only a slice of *available* RAM is safe for a foreground app.
        val ramBudgetMb = minOf(
            hardware.appHeapMb * 2,
            hardware.availRamMb - 700L
        ).coerceAtLeast(200L)

        var freeAfterLoad = ramBudgetMb - residentMb
        if (freeAfterLoad < 0) {
            warnings += "Model needs ~${residentMb} MB resident but only ~${ramBudgetMb} MB is free — pick a smaller quantization or free up RAM."
        } else if (freeAfterLoad < 260) {
            warnings += "Only ~${freeAfterLoad} MB left after loading the model — context and output are clamped to avoid an ANR."
        }

        val kvBudgetMb = (freeAfterLoad * 0.32).toLong().coerceAtLeast(0L)
        val kvTokens = if (kvKb > 0) (kvBudgetMb * 1024 / kvKb).toInt() else 512
        val contextBudget = kvTokens.coerceIn(384, 8_192)

        // Output cap: the single most effective freeze guard on a phone.
        val outputCap = when {
            hardware.isFlagship -> 1_024
            hardware.isMidRange -> 704
            else -> 448
        }
        var maxOutput = minOf(outputCap, (contextBudget * 0.55).toInt().coerceAtLeast(128))
        if (hardware.powerSaveMode) {
            maxOutput = (maxOutput * 0.7).toInt()
            warnings += "Battery saver is on — output length and threads were reduced automatically."
        }
        if (hardware.thermalStatus >= 3) {
            maxOutput = (maxOutput * 0.6).toInt()
            warnings += "Device is thermally throttled — generation shortened to keep the UI responsive."
        }
        if (hardware.availRamMb < 1_400) {
            maxOutput = (maxOutput * 0.6).toInt()
            warnings += "Low free RAM (${hardware.availRamMb} MB) — short answers only until memory frees up."
        }
        maxOutput = maxOutput.coerceIn(96, 2_048)
        val turns = when {
            maxOutput <= 256 -> 3
            maxOutput <= 512 -> 4
            maxOutput <= 832 -> 6
            else -> 8
        }

        val useGpu = hardware.hasVulkanCompute &&
            hardware.is64Bit &&
            !hardware.powerSaveMode
        if (!useGpu && model != null) {
            warnings += "No usable Vulkan compute backend detected — running on CPU (${hardware.cores} threads max)."
        }

        val threads = when {
            hardware.cores >= 8 -> 6
            hardware.cores >= 6 -> 4
            hardware.cores >= 4 -> 3
            else -> 2
        }
        val singleThreadGuard = hardware.isTensorSoC &&
            (model?.id?.lowercase()?.contains("gemma") == true ||
                model?.name?.lowercase()?.contains("gemma") == true)
        if (singleThreadGuard) {
            warnings += "Tensor SoC + Gemma detected: inference is pinned to 1 thread to avoid corrupted logits / empty replies."
        }

        val quant = when {
            hardware.totalRamMb < 4_200 -> "Q2_K"
            hardware.totalRamMb < 6_000 -> "Q3_K_M"
            hardware.totalRamMb < 8_000 -> "Q4_K_S"
            hardware.totalRamMb < 12_000 -> "Q4_K_M"
            else -> "Q5_K_M"
        }

        val verdict = when {
            freeAfterLoad < 0 -> BudgetAdvice.Verdict.OVER
            freeAfterLoad < 320 -> BudgetAdvice.Verdict.TIGHT
            else -> BudgetAdvice.Verdict.OPTIMAL
        }

        return BudgetAdvice(
            maxOutputTokens = maxOutput,
            contextTokenBudget = contextBudget,
            historyTurns = turns,
            cpuThreads = if (useGpu) threads else (threads + 1).coerceAtMost(hardware.cores),
            gpuEnabled = useGpu,
            singleThreadGuard = singleThreadGuard,
            quantization = quant,
            modelResidentMb = residentMb,
            kvPerTokenKb = kvKb,
            freeRamAfterLoadMb = freeAfterLoad.coerceAtLeast(0L),
            verdict = verdict,
            warnings = warnings
        )
    }

    /** Streaming cadence, also tuned per tier (see docs/REAL-AGENT.md). */
    fun flushIntervalMs(hardware: HardwareInfo): Long = when {
        hardware.isFlagship -> 60L
        hardware.isMidRange -> 90L
        else -> 150L
    }

    fun minFlushChars(hardware: HardwareInfo): Int = when {
        hardware.isFlagship -> 8
        hardware.isMidRange -> 14
        else -> 24
    }

    fun modelFilesUsableLocally(model: HFModelConfig): Boolean = model.sizeBytes > 0

    fun engineFileHint(): String =
        ".gguf (llama.cpp) or .litertlm / .task (LiteRT-LM native engine)"
}
