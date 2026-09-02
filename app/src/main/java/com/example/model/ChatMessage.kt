package com.example.model

import android.graphics.Bitmap

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

enum class MessageStatus {
    SUCCESS,
    STREAMING,
    ERROR
}

data class ZipEntryInfo(
    val name: String,
    val sizeBytes: Long,
    val isDirectory: Boolean,
    val isReadable: Boolean,
    val previewSnippet: String? = null,
    val reason: String = "Opened & Read"
)

data class Attachment(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val extension: String,
    val mimeType: String,
    val sizeBytes: Long,
    val formattedSize: String,
    val isImage: Boolean,
    val isZip: Boolean,
    val isCodeOrText: Boolean,
    val uri: String? = null,
    val base64Data: String? = null,
    val extractedText: String? = null,
    val previewBitmap: Bitmap? = null,
    val zipEntries: List<ZipEntryInfo> = emptyList()
)

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SUCCESS,
    val agentEmoji: String? = null,
    val attachment: Attachment? = null,
    val isLocalExecution: Boolean = false
)

data class ChatSession(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
    val messageCount: Int,
    val previewText: String,
    val messages: List<ChatMessage>,
    val agentEmoji: String = "🤖",
    val modelName: String = "Qwen 2.5"
)

