package com.example.service

import com.example.model.HFModelConfig
import com.example.model.ModelType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

class HfModelSearchService {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun searchModels(query: String): List<HFModelConfig> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()

        // Normalize query for Hugging Face search API:
        // Handle "dolphin -qwen" -> "dolphin qwen"
        val apiQuery = cleanQuery.replace(Regex("[\\-_]"), " ").replace(Regex("\\s+"), " ").trim()
        val encodedQuery = java.net.URLEncoder.encode(apiQuery, "UTF-8")

        val url = "https://huggingface.co/api/models?search=$encodedQuery&limit=30"

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AIStudio-QwenAgent/1.0")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext emptyList()

            if (!response.isSuccessful) {
                return@withContext emptyList()
            }

            val jsonArray = JSONArray(responseBody)
            val results = mutableListOf<HFModelConfig>()

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val modelId = item.optString("id", "")
                if (modelId.isBlank()) continue

                val isPrivate = item.optBoolean("private", false)
                if (isPrivate) continue

                val downloads = item.optInt("downloads", 0)
                val likes = item.optInt("likes", 0)
                val pipelineTag = item.optString("pipeline_tag", "")

                val tagsJson = item.optJSONArray("tags")
                val tags = mutableListOf<String>()
                if (tagsJson != null) {
                    for (t in 0 until tagsJson.length()) {
                        tags.add(tagsJson.getString(t))
                    }
                }

                val lowerId = modelId.lowercase()
                val lowerTags = tags.map { it.lowercase() }
                val isDolphin = lowerId.contains("dolphin") || lowerTags.contains("dolphin")
                val isUncensored = isDolphin || lowerId.contains("uncensored") || lowerId.contains("unfiltered") ||
                        lowerId.contains("abliterated") || lowerId.contains("hermes") || lowerTags.any { it.contains("uncensored") }
                val isCoder = lowerId.contains("coder") || lowerId.contains("code") || lowerTags.contains("code")
                val isVision = lowerId.contains("vision") || lowerId.contains("vl") || pipelineTag == "image-to-text" || lowerTags.contains("vision")
                val isInstant = lowerId.contains("0.5b") || lowerId.contains("135m") || lowerId.contains("360m") || lowerId.contains("tiny")

                val type = when {
                    isUncensored -> ModelType.UNCENSORED
                    isCoder -> ModelType.CODER
                    isVision -> ModelType.VISION
                    isInstant -> ModelType.INSTANT
                    else -> ModelType.STANDARD
                }

                val estimatedSize = when {
                    lowerId.contains("70b") -> "~40 GB"
                    lowerId.contains("32b") -> "~18 GB"
                    lowerId.contains("14b") -> "~8.5 GB"
                    lowerId.contains("7b") || lowerId.contains("8b") -> "~4.2 GB"
                    lowerId.contains("3b") || lowerId.contains("4b") -> "~2.2 GB"
                    lowerId.contains("2b") || lowerId.contains("1.5b") -> "~1.1 GB"
                    lowerId.contains("1b") || lowerId.contains("0.5b") -> "~360 MB"
                    lowerId.contains("135m") || lowerId.contains("360m") -> "~90-250 MB"
                    else -> "~700 MB"
                }

                val sizeBytes = when {
                    lowerId.contains("7b") || lowerId.contains("8b") -> 4404019200L
                    lowerId.contains("2b") || lowerId.contains("1.5b") -> 1153433600L
                    lowerId.contains("1b") || lowerId.contains("0.5b") -> 377487360L
                    lowerId.contains("135m") -> 94371840L
                    else -> 734003200L
                }

                val parts = modelId.split("/")
                val author = if (parts.size > 1) parts[0] else "Hugging Face"
                val rawName = if (parts.size > 1) parts[1] else modelId
                val displayName = formatModelDisplayName(rawName, isDolphin, isUncensored)

                val badge = when {
                    isDolphin -> "Dolphin Uncensored"
                    isUncensored -> "Uncensored / Open"
                    isCoder -> "Coder Hub"
                    isVision -> "Vision Hub"
                    downloads > 50000 -> "Popular (${downloads / 1000}k dl)"
                    else -> "Hugging Face Live"
                }

                val desc = "By $author • ${formatCount(downloads)} downloads • ${formatCount(likes)} likes • ${pipelineTag.ifBlank { "text-generation" }}"

                results.add(
                    HFModelConfig(
                        id = modelId,
                        url = "https://huggingface.co/$modelId",
                        name = displayName,
                        size = estimatedSize,
                        type = type,
                        description = desc,
                        badge = badge,
                        tags = tags + listOf("hf-live", if (isUncensored) "uncensored" else "", if (isDolphin) "dolphin" else ""),
                        minTier = if (sizeBytes > 2000000000L) "high" else "medium",
                        supportsVision = isVision,
                        supportsFiles = true,
                        sizeBytes = sizeBytes
                    )
                )
            }
            results
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun formatModelDisplayName(rawName: String, isDolphin: Boolean, isUncensored: Boolean): String {
        var clean = rawName.replace("-", " ").replace("_", " ")
        if (isDolphin && !clean.lowercase().contains("dolphin")) {
            clean = "Dolphin $clean"
        }
        if (isUncensored && !clean.lowercase().contains("uncensored") && !isDolphin) {
            clean = "$clean (Uncensored)"
        }
        return clean.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }

    private fun formatCount(count: Int): String {
        return when {
            count >= 1_000_000 -> "${count / 1_000_000}M"
            count >= 1_000 -> "${count / 1_000}k"
            else -> "$count"
        }
    }
}
