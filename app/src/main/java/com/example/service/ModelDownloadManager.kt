package com.example.service

import android.content.Context
import android.os.StatFs
import com.example.model.DownloadStatus
import com.example.model.HFModelConfig
import com.example.model.ModelDownloadProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Downloads **real** weights from the Hugging Face Hub and keeps them on disk.
 *
 * Design notes (taken from the anti-freeze work in the PrivateLM client):
 *  * progress is emitted on a *time budget* (≈180 ms) instead of per 8 KB read chunk, so a
 *    4 GB transfer does not fire tens of thousands of state copies and recompositions;
 *  * interrupted transfers resume via HTTP `Range`, and the on-disk manifest is reconciled at
 *    start-up, so a process kill never shows a phantom "0 % / downloaded" mismatch;
 *  * free space is verified before a single byte is written.
 */
class ModelDownloadManager(
    context: Context,
    private val scope: CoroutineScope,
    private val api: HfRepositoryClient = HfRepositoryClient()
) {

    private val appContext = context.applicationContext

    val modelsDir: File by lazy {
        val base = appContext.getExternalFilesDir(null) ?: appContext.filesDir
        File(base, "models").apply { if (!exists()) mkdirs() }
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val _states = MutableStateFlow<Map<String, ModelDownloadProgress>>(emptyMap())
    val states: StateFlow<Map<String, ModelDownloadProgress>> = _states.asStateFlow()

    private val jobs = HashMap<String, Job>()
    private val lock = Any()

    /** In-memory mirror of manifest.json so non-suspend callers (UI) can query instantly. */
    private val manifest = LinkedHashMap<String, JSONObject>()
    private val manifestLoaded = java.util.concurrent.atomic.AtomicBoolean(false)

    private val manifestFile: File get() = File(modelsDir, "manifest.json")

    fun stateFor(modelId: String): ModelDownloadProgress? = _states.value[modelId.trim('/')]

    fun isDownloading(modelId: String): Boolean =
        synchronized(lock) { jobs[modelId.trim('/')]?.isActive == true }

    /** The finished weight file on disk, or null if it is not (fully) downloaded. */
    fun localFileFor(modelId: String): File? {
        ensureManifestLoadedBlocking()
        val entry = manifest[modelId.trim('/')] ?: return null
        if (entry.optBoolean("partial", false)) return null
        val file = File(modelsDir, entry.optString("fileName"))
        return if (file.exists() && file.length() > 0) file else null
    }

    fun localPathFor(modelId: String): String? = localFileFor(modelId)?.absolutePath

    fun isDownloaded(modelId: String): Boolean = localFileFor(modelId) != null

    fun kindFor(modelId: String): String {
        val name = localFileFor(modelId)?.name?.lowercase() ?: return "gguf"
        return when {
            name.endsWith(".gguf") -> "gguf"
            name.endsWith(".litertlm") -> "litertlm"
            name.endsWith(".task") -> "task"
            name.endsWith(".onnx") -> "onnx"
            else -> "bin"
        }
    }

    fun quantFor(modelId: String): String {
        ensureManifestLoadedBlocking()
        return manifest[modelId.trim('/')]?.optString("quant", "") ?: ""
    }

    fun totalModelBytesOnDisk(): Long = try {
        modelsDir.listFiles()?.sumOf { if (it.isFile) it.length() else 0L } ?: 0L
    } catch (e: Exception) {
        0L
    }

    /** Rebuilds UI state from disk after a restart — no fake "already downloaded" flags. */
    fun reconcile() {
        scope.launch(Dispatchers.IO) {
            loadManifest()
            val rebuilt = HashMap<String, ModelDownloadProgress>()
            for ((modelId, entry) in manifest) {
                val file = File(modelsDir, entry.optString("fileName"))
                val partial = entry.optBoolean("partial", false)
                val total = entry.optLong("size", 0L)
                when {
                    !partial && file.exists() && file.length() > 0 -> rebuilt[modelId] = ModelDownloadProgress(
                        modelId = modelId,
                        status = DownloadStatus.DOWNLOADED,
                        progress = 1.0f,
                        downloadedBytes = file.length(),
                        totalBytes = if (total > 0) total else file.length(),
                        speedMbps = 0.0,
                        etaSeconds = 0,
                        fileName = file.name,
                        filePath = file.absolutePath
                    )
                    partial && file.exists() -> {
                        val have = file.length()
                        rebuilt[modelId] = ModelDownloadProgress(
                            modelId = modelId,
                            status = DownloadStatus.DOWNLOADING,
                            progress = if (total > 0) (have.toFloat() / total).coerceIn(0f, 1f) else 0f,
                            downloadedBytes = have,
                            totalBytes = total,
                            speedMbps = 0.0,
                            isPaused = true,
                            fileName = file.name.removeSuffix(".part"),
                            filePath = file.absolutePath
                        )
                    }
                }
            }
            _states.value = rebuilt
        }
    }

    /**
     * Resolves the exact file this device can run (real sizes from the Hub) and starts the
     * transfer. [residentBudgetBytes] comes from [com.example.model.ResponseBudgetAdvisor].
     */
    fun start(
        model: HFModelConfig,
        residentBudgetBytes: Long,
        preferredQuant: String,
        token: String? = null,
        preferKind: String? = null,
        onResolved: (HfRemoteFile) -> Unit = {}
    ) {
        val modelId = model.id.trim('/')
        synchronized(lock) {
            if (jobs[modelId]?.isActive == true) return
        }

        val job = scope.launch(Dispatchers.IO) {
            setState(
                modelId,
                ModelDownloadProgress(
                    modelId = modelId,
                    status = DownloadStatus.DOWNLOADING,
                    progress = 0f,
                    totalBytes = 0,
                    speedMbps = 0.0,
                    fileName = "",
                    filePath = ""
                )
            )
            try {
                val files = api.listFiles(modelId, token)
                val weights = files.filter { HfRepositoryClient.isWeightFile(it.path) }
                val picked = HfRepositoryClient.pickFor(
                    files = weights,
                    preferredQuant = preferredQuant,
                    residentBudgetBytes = residentBudgetBytes.coerceAtLeast(120L * 1024 * 1024),
                    preferKind = preferKind
                )
                if (picked == null) {
                    setState(
                        modelId,
                        ModelDownloadProgress(
                            modelId = modelId,
                            status = DownloadStatus.NOT_DOWNLOADED,
                            error = if (weights.isEmpty()) {
                                "No on-device weights in this repo (.gguf / .litertlm / .task). " +
                                    "It ships PyTorch/safetensors only."
                            } else {
                                "Every weight file here is larger than this device's RAM budget. " +
                                    "Try a smaller quantization or a smaller model."
                            }
                        )
                    )
                    return@launch
                }
                onResolved(picked)
                transfer(modelId, picked, token)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setState(
                    modelId,
                    (_states.value[modelId] ?: ModelDownloadProgress(modelId = modelId))
                        .copy(
                            status = DownloadStatus.NOT_DOWNLOADED,
                            isPaused = false,
                            error = "Download failed: ${e.message ?: "network error"}"
                        )
                )
            } finally {
                synchronized(lock) { jobs.remove(modelId) }
            }
        }
        synchronized(lock) { jobs[modelId] = job }
    }

    /** Resumes the partial file already on disk, if any. */
    fun resume(model: HFModelConfig, residentBudgetBytes: Long, preferredQuant: String, token: String? = null) {
        start(model, residentBudgetBytes, preferredQuant, token)
    }

    private suspend fun transfer(modelId: String, file: HfRemoteFile, token: String?) {
        val url = HfRepositoryClient.downloadUrl(modelId, file.path)
        val safeName = file.fileName.replace('/', '_')
        val target = File(modelsDir, safeName)
        val part = File(modelsDir, "$safeName.part")

        if (target.exists() && target.length() > 0) {
            recordEntry(modelId, file, target, partial = false)
            publishDownloaded(modelId, file, target)
            return
        }

        val existing = if (part.exists()) part.length() else 0L
        var bytesDone = existing
        var total = file.sizeBytes
        if (total <= 0L) total = api.contentLength(url, token)

        val needed = (total - existing).coerceAtLeast(0L)
        val freeSpace = try {
            StatFs(modelsDir.absolutePath).availableBytes
        } catch (e: Exception) {
            Long.MAX_VALUE
        }
        if (total > 0 && freeSpace != Long.MAX_VALUE && freeSpace < needed + RESERVE_BYTES) {
            setState(
                modelId,
                ModelDownloadProgress(
                    modelId = modelId,
                    status = DownloadStatus.NOT_DOWNLOADED,
                    downloadedBytes = existing,
                    totalBytes = total,
                    error = "Not enough storage: ${formatBytes(needed + RESERVE_BYTES)} needed, " +
                        "${formatBytes(freeSpace)} free. Delete another model first."
                )
            )
            return
        }

        recordEntry(modelId, file, part, partial = true)

        val canResume = existing > 0 && api.supportsResume(url)
        if (!canResume && existing > 0) {
            part.delete()
            bytesDone = 0L
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "AgentLM/2.0")
        if (!token.isNullOrBlank()) requestBuilder.header("Authorization", "Bearer $token")
        if (canResume) requestBuilder.header("Range", "bytes=$existing-")

        val call = http.newCall(requestBuilder.build())
        val disposal = coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) runCatching { call.cancel() }
        }

        var lastEmitMs = 0L
        var windowStartMs = System.currentTimeMillis()
        var windowStartBytes = bytesDone

        try {
            call.execute().use { response ->
                if (!response.isSuccessful && response.code != 206) {
                    throw java.io.IOException("HTTP ${response.code} from Hugging Face")
                }
                val append = response.code == 206 && canResume
                if (!append) {
                    bytesDone = 0L
                    windowStartBytes = 0L
                }

                val stream: InputStream = response.body?.byteStream()
                    ?: throw java.io.IOException("Empty response body")
                stream.use { input ->
                    FileOutputStream(part, append).use { raw ->
                        BufferedOutputStream(raw, 256 * 1024).use { out ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                if (!coroutineContext.isActive) throw CancellationException("cancelled")
                                val read = input.read(buffer)
                                if (read <= 0) break
                                out.write(buffer, 0, read)
                                bytesDone += read

                                val now = System.currentTimeMillis()
                                if (now - lastEmitMs >= EMIT_INTERVAL_MS) {
                                    val elapsedSec = (now - windowStartMs).coerceAtLeast(1L) / 1000.0
                                    val bytesPerSec = (bytesDone - windowStartBytes) / elapsedSec
                                    val eta = if (total > 0 && bytesPerSec > 4096) {
                                        ((total - bytesDone) / bytesPerSec).toLong().coerceIn(0L, 86_400L)
                                    } else {
                                        0L
                                    }
                                    setState(
                                        modelId,
                                        ModelDownloadProgress(
                                            modelId = modelId,
                                            status = DownloadStatus.DOWNLOADING,
                                            progress = if (total > 0) (bytesDone.toFloat() / total).coerceIn(0f, 1f) else 0f,
                                            downloadedBytes = bytesDone,
                                            totalBytes = total,
                                            speedMbps = bytesPerSec / (1024.0 * 1024.0),
                                            speedBytesPerSec = bytesPerSec.toLong(),
                                            etaSeconds = eta,
                                            fileName = safeName,
                                            filePath = part.absolutePath
                                        )
                                    )
                                    lastEmitMs = now
                                    windowStartMs = now
                                    windowStartBytes = bytesDone
                                }
                            }
                            out.flush()
                            // Durability before the atomic rename: flushing the BufferedOutputStream
                            // only hands bytes to the OS page cache, and a process kill mid-download
                            // would otherwise leave a .part shorter than the manifest claims — the
                            // next resume would then append onto stale offsets and corrupt the GGUF.
                            runCatching { raw.channel.force(false) }
                        }
                    }
                }
            }

            if (!part.exists()) {
                throw java.io.IOException("Transfer ended without writing data")
            }
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) {
                throw java.io.IOException("Could not finalize ${target.name}")
            }
            recordEntry(modelId, file, target, partial = false)
            publishDownloaded(modelId, file, target)
        } catch (e: CancellationException) {
            // Pause/cancel: flush what the OS still holds so the .part is exactly `bytesDone` long.
            runCatching {
                java.io.RandomAccessFile(part, "rw").use { it.fd.sync() }
            }
            // Keep the .part file: it is resumable on the next attempt (see reconcile()).
            setState(
                modelId,
                ModelDownloadProgress(
                    modelId = modelId,
                    status = DownloadStatus.DOWNLOADING,
                    progress = if (total > 0) (bytesDone.toFloat() / total).coerceIn(0f, 1f) else 0f,
                    downloadedBytes = bytesDone,
                    totalBytes = total,
                    speedMbps = 0.0,
                    isPaused = true,
                    fileName = safeName,
                    filePath = part.absolutePath
                )
            )
            throw e
        } finally {
            disposal?.dispose()
        }
    }

    private fun publishDownloaded(modelId: String, file: HfRemoteFile, target: File) {
        setState(
            modelId,
            ModelDownloadProgress(
                modelId = modelId,
                status = DownloadStatus.DOWNLOADED,
                progress = 1.0f,
                downloadedBytes = target.length(),
                totalBytes = if (file.sizeBytes > 0) file.sizeBytes else target.length(),
                speedMbps = 0.0,
                etaSeconds = 0,
                fileName = target.name,
                filePath = target.absolutePath
            )
        )
    }

    /** Pauses the transfer but keeps the partial file for a later resume. */
    fun pause(modelId: String) {
        val id = modelId.trim('/')
        val job = synchronized(lock) { jobs.remove(id) }
        job?.cancel()
        setState(
            id,
            (_states.value[id] ?: ModelDownloadProgress(modelId = id)).copy(
                isPaused = true,
                speedMbps = 0.0
            )
        )
    }

    /** Cancels and discards the partial download. */
    fun cancel(modelId: String) {
        val id = modelId.trim('/')
        val job = synchronized(lock) { jobs.remove(id) }
        job?.cancel()
        scope.launch(Dispatchers.IO) {
            val fileName = _states.value[id]?.filePath?.let { File(it).name }
            try {
                if (fileName != null) File(modelsDir, fileName).delete()
            } catch (e: Exception) {
                // ignore
            }
            setState(
                id,
                ModelDownloadProgress(
                    modelId = id,
                    status = DownloadStatus.NOT_DOWNLOADED,
                    progress = 0f,
                    downloadedBytes = 0L,
                    totalBytes = 0L
                )
            )
        }
    }

    fun deleteDownload(modelId: String) {
        val id = modelId.trim('/')
        cancel(id)
        scope.launch(Dispatchers.IO) {
            try {
                localFileFor(id)?.delete()
                modelsDir.listFiles()?.filter { it.name.startsWith(id.replace('/', '_')) }?.forEach { it.delete() }
            } catch (e: Exception) {
                // ignore
            }
            ensureManifestLoadedBlocking()
            manifest.remove(id)
            writeManifest()
            setState(id, ModelDownloadProgress(modelId = id, status = DownloadStatus.NOT_DOWNLOADED, progress = 0f))
        }
    }

    /** Clears abandoned partials + stale temp files. Returns the number of bytes freed. */
    suspend fun clearCaches(): Long = withContext(Dispatchers.IO) {
        var freed = 0L
        val now = System.currentTimeMillis()
        val activeNames = synchronized(lock) { jobs.keys.toSet() }
        val tempRoots = listOfNotNull(
            modelsDir,
            File(appContext.cacheDir, "agentlm_tmp"),
            appContext.cacheDir
        )
        for (root in tempRoots) {
            try {
                if (!root.exists()) continue
                root.listFiles()?.forEach { f ->
                    if (!f.isFile) return@forEach
                    val isStaleTemp = f.name.endsWith(".part") &&
                        activeNames.none { f.name.startsWith(it.replace('/', '_')) }
                    val isOldCache = root != modelsDir &&
                        !f.name.endsWith(".part") &&
                        now - f.lastModified() > STALE_CACHE_MS
                    if (isStaleTemp || isOldCache) {
                        freed += f.length()
                        f.delete()
                    }
                }
            } catch (e: Exception) {
                // keep going with the next root
            }
        }
        freed
    }

    // ---------------------------------------------------------------- manifest ------

    private fun ensureManifestLoadedBlocking() {
        if (manifestLoaded.get()) return
        synchronized(manifest) {
            if (manifestLoaded.get()) return
            try {
                if (manifestFile.exists()) {
                    val root = JSONObject(manifestFile.readText())
                    val keys = root.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        root.optJSONObject(k)?.let { manifest[k] = it }
                    }
                }
            } catch (e: Exception) {
                // Corrupt manifest → rebuild from directory listing next reconcile().
            }
            manifestLoaded.set(true)
        }
    }

    private suspend fun loadManifest() = withContext(Dispatchers.IO) {
        synchronized(manifest) {
            manifest.clear()
            manifestLoaded.set(false)
        }
        ensureManifestLoadedBlocking()
    }

    private fun recordEntry(modelId: String, file: HfRemoteFile, onDisk: File, partial: Boolean) {
        ensureManifestLoadedBlocking()
        synchronized(manifest) {
            manifest[modelId] = JSONObject()
                .put("fileName", onDisk.name)
                .put("size", if (file.sizeBytes > 0) file.sizeBytes else onDisk.length())
                .put("sha", file.sha)
                .put("path", file.path)
                .put("quant", file.quant)
                .put("kind", file.kind)
                .put("partial", partial)
                .put("ts", System.currentTimeMillis())
        }
        writeManifest()
    }

    private fun writeManifest() {
        try {
            if (!modelsDir.exists()) modelsDir.mkdirs()
            val root = JSONObject()
            synchronized(manifest) {
                manifest.forEach { (k, v) -> root.put(k, v) }
            }
            manifestFile.writeText(root.toString())
        } catch (e: Exception) {
            // Non-fatal: state stays in memory for this session.
        }
    }

    private fun setState(modelId: String, progress: ModelDownloadProgress) {
        val map = HashMap(_states.value)
        map[modelId] = progress
        _states.value = map
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes == Long.MAX_VALUE -> "plenty"
        bytes >= 1_073_741_824L -> String.format("%.2f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> String.format("%.0f MB", bytes / 1_048_576.0)
        else -> "${bytes / 1024} KB"
    }

    companion object {
        private const val EMIT_INTERVAL_MS = 180L
        private const val RESERVE_BYTES = 96L * 1024 * 1024
        private const val STALE_CACHE_MS = 3L * 24 * 60 * 60 * 1000
    }
}
