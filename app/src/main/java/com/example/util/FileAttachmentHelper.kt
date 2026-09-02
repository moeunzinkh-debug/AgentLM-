package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.model.Attachment
import com.example.model.ZipEntryInfo
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream

object FileAttachmentHelper {

    fun parseUri(context: Context, uri: Uri): Attachment {
        val contentResolver = context.contentResolver
        var fileName = "attachment"
        var fileSize = 0L

        // Query metadata
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: fileName
                    if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                }
            }
        } catch (_: Exception) {}

        val mimeType = contentResolver.getType(uri) ?: getMimeTypeFromExtension(fileName)
        val ext = fileName.substringAfterLast('.', "").lowercase()

        val isImg = mimeType.startsWith("image/") || ext in listOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
        val isZipFile = mimeType.contains("zip") || ext in listOf("zip", "jar", "apk")
        val isCodeOrTextFile = ext in listOf("kt", "java", "py", "js", "ts", "json", "txt", "md", "xml", "html", "css", "c", "cpp", "rs", "go", "sql", "sh", "yaml", "yml", "csv")

        var base64Data: String? = null
        var extractedText: String? = null
        var previewBitmap: Bitmap? = null
        val zipEntries = mutableListOf<ZipEntryInfo>()

        try {
            if (isImg) {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        previewBitmap = bitmap
                        val outputStream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                        val bytes = outputStream.toByteArray()
                        base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        if (fileSize == 0L) fileSize = bytes.size.toLong()
                    }
                }
            } else if (isZipFile) {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val (entries, fullSummary) = extractZipEntries(stream)
                    zipEntries.addAll(entries)
                    extractedText = fullSummary
                }
            } else {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val bytes = stream.readBytes()
                    if (fileSize == 0L) fileSize = bytes.size.toLong()
                    extractedText = String(bytes, Charsets.UTF_8).take(30000)
                }
            }
        } catch (e: Exception) {
            extractedText = "Error reading file: ${e.localizedMessage}"
        }

        val typeLabel = when {
            isImg -> "image"
            isZipFile -> "zip"
            isCodeOrTextFile -> "code"
            else -> "document"
        }

        return Attachment(
            id = UUID.randomUUID().toString(),
            name = fileName,
            extension = ext,
            mimeType = mimeType,
            sizeBytes = fileSize,
            formattedSize = formatFileSize(fileSize),
            isImage = isImg,
            isZip = isZipFile,
            isCodeOrText = isCodeOrTextFile,
            uri = uri.toString(),
            base64Data = base64Data,
            extractedText = extractedText,
            previewBitmap = previewBitmap,
            zipEntries = zipEntries
        )
    }

    fun extractZipEntries(inputStream: InputStream): Pair<List<ZipEntryInfo>, String> {
        val entries = mutableListOf<ZipEntryInfo>()
        val summaryBuilder = StringBuilder()
        val textExtensions = setOf(
            "kt", "java", "py", "js", "ts", "json", "txt", "md", "xml",
            "html", "css", "c", "cpp", "rs", "go", "sql", "sh", "yaml", "yml", "csv", "gradle", "properties"
        )

        try {
            val zis = ZipInputStream(inputStream)
            var entry = zis.nextEntry
            var totalOpened = 0
            var totalSkipped = 0

            while (entry != null) {
                val name = entry.name
                val size = if (entry.size >= 0) entry.size else 0L
                val isDir = entry.isDirectory
                val ext = name.substringAfterLast('.', "").lowercase()

                if (!isDir) {
                    val isText = ext in textExtensions || ext.isEmpty()
                    if (isText) {
                        // Read snippet up to 10KB
                        val buffer = ByteArray(10240)
                        val bytesRead = zis.read(buffer)
                        val snippet = if (bytesRead > 0) String(buffer, 0, bytesRead, Charsets.UTF_8) else ""
                        entries.add(
                            ZipEntryInfo(
                                name = name,
                                sizeBytes = size,
                                isDirectory = false,
                                isReadable = true,
                                previewSnippet = snippet.take(1500),
                                reason = "Text / Source file (Opened & Read)"
                            )
                        )
                        totalOpened++
                    } else {
                        entries.add(
                            ZipEntryInfo(
                                name = name,
                                sizeBytes = size,
                                isDirectory = false,
                                isReadable = false,
                                previewSnippet = null,
                                reason = "Binary format ($ext) - skipped raw text parsing"
                            )
                        )
                        totalSkipped++
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }

            summaryBuilder.append("### 📦 ZIP Extraction Results\n")
            summaryBuilder.append("- Total Files: ${entries.size} (${totalOpened} readable text/code, ${totalSkipped} binary files)\n\n")

            summaryBuilder.append("#### ✅ Opened & Read Files:\n")
            val opened = entries.filter { it.isReadable }
            if (opened.isEmpty()) {
                summaryBuilder.append("*No text or code files found in archive.*\n")
            } else {
                for (op in opened.take(15)) {
                    summaryBuilder.append("- **`${op.name}`** (${formatFileSize(op.sizeBytes)})\n")
                    if (!op.previewSnippet.isNullOrBlank()) {
                        summaryBuilder.append("  ```\n  ${op.previewSnippet!!.take(200).replace("\n", "\n  ")}\n  ```\n")
                    }
                }
            }

            val skipped = entries.filter { !it.isReadable }
            if (skipped.isNotEmpty()) {
                summaryBuilder.append("\n#### 🔒 Binary / Unopened Files:\n")
                for (sk in skipped.take(10)) {
                    summaryBuilder.append("- `${sk.name}`: ${sk.reason}\n")
                }
            }
        } catch (e: Exception) {
            summaryBuilder.append("Error extracting ZIP: ${e.localizedMessage}")
        }

        return Pair(entries, summaryBuilder.toString())
    }

    fun createSampleZipAttachment(): Attachment {
        val sampleEntries = listOf(
            ZipEntryInfo(
                name = "src/main/kotlin/App.kt",
                sizeBytes = 1420L,
                isDirectory = false,
                isReadable = true,
                previewSnippet = "package com.example\n\nfun main() {\n    println(\"Hello from Qwen Agent Local ONNX!\")\n}",
                reason = "Text / Source file (Opened & Read)"
            ),
            ZipEntryInfo(
                name = "README.md",
                sizeBytes = 850L,
                isDirectory = false,
                isReadable = true,
                previewSnippet = "# Qwen Mobile Agent\n\nHigh performance local LLM agent for mobile.",
                reason = "Text / Source file (Opened & Read)"
            ),
            ZipEntryInfo(
                name = "config/settings.json",
                sizeBytes = 320L,
                isDirectory = false,
                isReadable = true,
                previewSnippet = "{\n  \"model\": \"Qwen2.5-0.5B-Instruct\",\n  \"quantization\": \"Q4\"\n}",
                reason = "Text / Source file (Opened & Read)"
            ),
            ZipEntryInfo(
                name = "assets/logo.png",
                sizeBytes = 45200L,
                isDirectory = false,
                isReadable = false,
                previewSnippet = null,
                reason = "Binary format (png) - image asset"
            ),
            ZipEntryInfo(
                name = "bin/model.onnx.data",
                sizeBytes = 350000000L,
                isDirectory = false,
                isReadable = false,
                previewSnippet = null,
                reason = "Binary format (data) - model weight binary"
            )
        )

        val summary = """
            ### 📦 ZIP Extraction Results: `qwen-project-sample.zip`
            - **Total Files in Archive:** 5 files
            - **Readable Source/Text Files:** 3 opened successfully
            - **Binary/Media Files:** 2 binary files identified

            #### ✅ Opened & Read Files:
            - **`src/main/kotlin/App.kt`** (1.4 KB)
              ```kotlin
              package com.example

              fun main() {
                  println("Hello from Qwen Agent Local ONNX!")
              }
              ```
            - **`README.md`** (850 B)
              ```markdown
              # Qwen Mobile Agent
              High performance local LLM agent for mobile.
              ```
            - **`config/settings.json`** (320 B)
              ```json
              {
                "model": "Qwen2.5-0.5B-Instruct",
                "quantization": "Q4"
              }
              ```

            #### 🔒 Binary / Unopened Files:
            - `assets/logo.png`: Binary image format (skipped raw text extraction)
            - `bin/model.onnx.data`: Binary ONNX weight payload (350 MB)
        """.trimIndent()

        return Attachment(
            id = UUID.randomUUID().toString(),
            name = "qwen-project-sample.zip",
            extension = "zip",
            mimeType = "application/zip",
            sizeBytes = 48500L,
            formattedSize = "48.5 KB",
            isImage = false,
            isZip = true,
            isCodeOrText = false,
            uri = null,
            base64Data = null,
            extractedText = summary,
            previewBitmap = null,
            zipEntries = sampleEntries
        )
    }

    fun createSampleCodeAttachment(): Attachment {
        val code = """
            // Qwen2.5-Coder Sample Script
            package com.example.service
            
            import kotlinx.coroutines.flow.Flow
            import kotlinx.coroutines.flow.flow
            
            class FastTokenizer {
                fun tokenize(text: String): List<Int> {
                    return text.split(" ").map { it.hashCode() % 32000 }
                }
            }
        """.trimIndent()

        return Attachment(
            id = UUID.randomUUID().toString(),
            name = "FastTokenizer.kt",
            extension = "kt",
            mimeType = "text/x-kotlin",
            sizeBytes = 280L,
            formattedSize = "280 B",
            isImage = false,
            isZip = false,
            isCodeOrText = true,
            uri = null,
            base64Data = null,
            extractedText = code,
            previewBitmap = null,
            zipEntries = emptyList()
        )
    }

    fun createSampleImageAttachment(): Attachment {
        return Attachment(
            id = UUID.randomUUID().toString(),
            name = "chart_dashboard_mockup.png",
            extension = "png",
            mimeType = "image/png",
            sizeBytes = 125000L,
            formattedSize = "125 KB",
            isImage = true,
            isZip = false,
            isCodeOrText = false,
            uri = null,
            base64Data = null,
            extractedText = "[Image: Dashboard UI Mockup showing token speed metrics, active model Qwen 2.5 0.5B, and 120 tokens/sec throughput]",
            previewBitmap = null,
            zipEntries = emptyList()
        )
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format("%.2f GB", gb)
    }

    private fun getMimeTypeFromExtension(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "zip" -> "application/zip"
            "json" -> "application/json"
            "kt" -> "text/x-kotlin"
            "java" -> "text/x-java"
            "py" -> "text/x-python"
            "js" -> "application/javascript"
            "ts" -> "application/typescript"
            "txt" -> "text/plain"
            "md" -> "text/markdown"
            "csv" -> "text/csv"
            "xml" -> "application/xml"
            "html" -> "text/html"
            else -> "application/octet-stream"
        }
    }
}
