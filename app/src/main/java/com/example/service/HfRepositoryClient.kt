package com.example.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** A real weight file living in a Hugging Face repo. */
data class HfRemoteFile(
    val path: String,
    val sizeBytes: Long,
    val sha: String,
    val quant: String,
    val kind: String
) {
    val fileName: String get() = path.substringAfterLast('/')
    val readableSize: String
        get() = when {
            sizeBytes >= 1_073_741_824L -> String.format("%.2f GB", sizeBytes / 1_073_741_824.0)
            sizeBytes >= 1_048_576L -> String.format("%.1f MB", sizeBytes / 1_048_576.0)
            else -> "${sizeBytes / 1024} KB"
        }
}

/**
 * Talks to the public Hugging Face Hub API for real metadata: which weight files a repo
 * actually contains, their real byte sizes, and their download URLs. No estimates.
 */
class HfRepositoryClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    suspend fun listFiles(modelId: String, token: String? = null): List<HfRemoteFile> =
        withContext(Dispatchers.IO) {
            val url = "https://huggingface.co/api/models/${modelId.trim('/')}/tree/main?recursive=true"
            val builder = Request.Builder()
                .url(url)
                .header("User-Agent", "AgentLM/2.0")
                .get()
            if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer $token")

            try {
                http.newCall(builder.build()).execute().use { resp ->
                    val body = resp.body?.string() ?: return@use emptyList<HfRemoteFile>()
                    if (!resp.isSuccessful) return@use emptyList<HfRemoteFile>()
                    parseTree(body)
                } ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }

    private fun parseTree(json: String): List<HfRemoteFile> {
        val out = ArrayList<HfRemoteFile>()
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val node = arr.optJSONObject(i) ?: continue
            if (node.optString("type") != "file") continue
            val path = node.optString("path")
            if (!isWeightFile(path)) continue
            val size = node.optLong("size", 0L).let {
                // LFS pointers sometimes report 0 in the tree; fall back to lfs.size.
                val lfs = node.optJSONObject("lfs")
                if (it > 0) it else lfs?.optLong("size", 0L) ?: 0L
            }
            val sha = node.optString("sha", "")
            out.add(
                HfRemoteFile(
                    path = path,
                    sizeBytes = size,
                    sha = sha,
                    quant = quantOf(path),
                    kind = kindOf(path)
                )
            )
        }
        return out.sortedBy { it.sizeBytes }
    }

    /** Real content length via HEAD — used when the tree API reports 0 bytes. */
    suspend fun contentLength(url: String, token: String? = null): Long = withContext(Dispatchers.IO) {
        try {
            val builder = Request.Builder().url(url).head()
            if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer $token")
            http.newCall(builder.build()).execute().use { resp ->
                val len = resp.header("Content-Length")?.toLongOrNull()
                val isRange = resp.code == 206
                val total = resp.header("Content-Range")?.substringAfter('/', "")?.toLongOrNull()
                when {
                    isRange && total != null && total > 0L -> total
                    len != null && len > 0L -> len
                    else -> 0L
                }
            }
        } catch (e: Exception) {
            0L
        }
    }

    /** Does the server accept byte ranges? (needed before we resume a partial file). */
    suspend fun supportsResume(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-0")
                .head()
                .build()
            http.newCall(request).execute().use { it.code == 206 }
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        val WEIGHT_EXTENSIONS = listOf(".gguf", ".litertlm", ".task", ".bin", ".onnx")

        /** Quantization tags ordered from smallest to largest. */
        private val QUANT_ORDER = listOf(
            "IQ2_XXS", "IQ2_XS", "Q2_K", "Q2_K_S", "IQ3_XXS", "Q3_K_S", "Q3_K_M", "Q3_K_L",
            "IQ4_XS", "Q4_0", "Q4_K_S", "Q4_K_M", "IQ4_XL", "Q4_1", "Q5_K_S", "Q5_K_M",
            "Q6_K", "Q8_0", "FP16", "BF16", "F16"
        )

        fun isWeightFile(path: String): Boolean {
            val lower = path.lowercase()
            if (!WEIGHT_EXTENSIONS.any { lower.endsWith(it) }) return false
            // Skip the tiny tokenizer/config shards and split pieces of multi-part weights.
            if (lower.contains("-0000")) return false
            if (lower.endsWith(".json") || lower.endsWith(".txt")) return false
            return true
        }

        fun kindOf(path: String): String = when {
            path.endsWith(".gguf", true) -> "gguf"
            path.endsWith(".litertlm", true) -> "litertlm"
            path.endsWith(".task", true) -> "task"
            path.endsWith(".onnx", true) -> "onnx"
            else -> "bin"
        }

        fun quantOf(path: String): String {
            val name = path.substringAfterLast('/').uppercase()
            QUANT_ORDER.forEach { if (name.contains(it.uppercase())) return it }
            Regex("Q[0-9](_[A-Z0-9]+)*").find(name)?.value?.let { return it }
            return when {
                name.contains("F32") || name.contains("FP32") -> "F32"
                name.contains("F16") || name.contains("FP16") -> "FP16"
                else -> "Q4_K_M"
            }
        }

        fun quantRank(quant: String): Int =
            QUANT_ORDER.indexOfFirst { it.equals(quant, ignoreCase = true) }.let { if (it < 0) 12 else it }

        /**
         * Chooses the file this device can actually run: largest quantization that still fits
         * the RAM-derived resident budget, preferring the recommended quant tag.
         */
        fun pickFor(
            files: List<HfRemoteFile>,
            preferredQuant: String,
            residentBudgetBytes: Long,
            preferKind: String? = null
        ): HfRemoteFile? {
            if (files.isEmpty()) return null
            val kindFiltered = if (preferKind != null) {
                files.filter { it.kind == preferKind }.ifEmpty { files }
            } else {
                files
            }
            val fitting = kindFiltered.filter { it.sizeBytes in 1..residentBudgetBytes }
            val pool = fitting.ifEmpty { listOfNotNull(kindFiltered.minByOrNull { it.sizeBytes }) }
            if (pool.isEmpty()) return kindFiltered.minByOrNull { it.sizeBytes }

            val exact = pool.filter { it.quant.equals(preferredQuant, ignoreCase = true) }
            if (exact.isNotEmpty()) return exact.maxByOrNull { it.sizeBytes }

            // Nearest smaller quant, then nearest larger one.
            val targetRank = quantRank(preferredQuant)
            val smaller = pool.filter { quantRank(it.quant) <= targetRank }
            if (smaller.isNotEmpty()) return smaller.maxByOrNull { quantRank(it.quant) }
            return pool.minByOrNull { quantRank(it.quant) }
        }

        fun downloadUrl(modelId: String, path: String): String =
            "https://huggingface.co/${modelId.trim('/')}/resolve/$path"

        fun parseJson(text: String): JSONObject? = try {
            JSONObject(text)
        } catch (e: Exception) {
            null
        }
    }
}
