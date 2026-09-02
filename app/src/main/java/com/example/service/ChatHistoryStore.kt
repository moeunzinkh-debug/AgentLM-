package com.example.service

import android.content.Context
import com.example.model.ChatMessage
import com.example.model.ChatSession
import com.example.model.MessageRole
import com.example.model.MessageStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Real chat persistence (SharedPreferences + JSON), replacing the two hard-coded sample
 * conversations the app used to ship with. Bitmaps and base64 payloads are intentionally not
 * stored — only what is needed to re-read the transcript later.
 */
class ChatHistoryStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    suspend fun loadSessions(): List<ChatSession> = withContext(Dispatchers.IO) {
        mutex.withLock { readSessions() }
    }

    suspend fun saveSessions(sessions: List<ChatSession>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val arr = JSONArray()
            sessions.take(MAX_SESSIONS).forEach { session -> arr.put(encodeSession(session)) }
            prefs.edit().putString(KEY_SESSIONS, arr.toString()).apply()
        }
    }

    suspend fun appendSession(session: ChatSession): List<ChatSession> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = listOf(session) + readSessions()
            val trimmed = updated.take(MAX_SESSIONS)
            val arr = JSONArray()
            trimmed.forEach { arr.put(encodeSession(it)) }
            prefs.edit().putString(KEY_SESSIONS, arr.toString()).apply()
            trimmed
        }
    }

    suspend fun deleteSession(sessionId: String): List<ChatSession> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val kept = readSessions().filterNot { it.id == sessionId }
            val arr = JSONArray()
            kept.forEach { arr.put(encodeSession(it)) }
            prefs.edit().putString(KEY_SESSIONS, arr.toString()).apply()
            kept
        }
    }

    suspend fun clearSessions() = withContext(Dispatchers.IO) {
        mutex.withLock { prefs.edit().remove(KEY_SESSIONS).apply() }
    }

    /** The transcript currently on screen, so a process kill mid-reply is not lost. */
    suspend fun saveDraft(messages: List<ChatMessage>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val arr = JSONArray()
            messages.take(MAX_DRAFT_MESSAGES).forEach { arr.put(encodeMessage(it)) }
            prefs.edit().putString(KEY_DRAFT, arr.toString()).apply()
        }
    }

    suspend fun loadDraft(): List<ChatMessage> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val raw = prefs.getString(KEY_DRAFT, null) ?: return@withLock emptyList()
            decodeMessages(raw)
        }
    }

    suspend fun clearDraft() = withContext(Dispatchers.IO) {
        mutex.withLock { prefs.edit().remove(KEY_DRAFT).apply() }
    }

    private fun readSessions(): List<ChatSession> {
        val raw = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<ChatSession>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                out.add(decodeSession(obj))
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun encodeSession(session: ChatSession): JSONObject {
        val msgs = JSONArray()
        session.messages.forEach { msgs.put(encodeMessage(it)) }
        return JSONObject()
            .put("id", session.id)
            .put("title", session.title)
            .put("timestamp", session.timestamp)
            .put("agentEmoji", session.agentEmoji)
            .put("modelName", session.modelName)
            .put("messages", msgs)
    }

    private fun decodeSession(obj: JSONObject): ChatSession {
        val messages = decodeMessages(obj.optJSONArray("messages")?.toString() ?: "[]")
        val preview = messages.lastOrNull()?.content.orEmpty().take(120)
        val title = obj.optString("title").ifBlank {
            messages.firstOrNull { it.role == MessageRole.USER }?.content?.take(36) ?: "Conversation"
        }
        return ChatSession(
            id = obj.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
            title = title,
            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
            messageCount = messages.size,
            previewText = preview.ifBlank { "No text" },
            messages = messages,
            agentEmoji = obj.optString("agentEmoji", "🤖"),
            modelName = obj.optString("modelName", "AgentLM")
        )
    }

    private fun encodeMessage(msg: ChatMessage): JSONObject = JSONObject()
        .put("id", msg.id)
        .put("role", msg.role.name)
        .put("content", msg.content)
        .put("timestamp", msg.timestamp)
        .put("status", msg.status.name)
        .put("agentEmoji", msg.agentEmoji ?: "")
        .put("local", msg.isLocalExecution)
        .put("attachment", msg.attachment?.let {
            JSONObject().put("name", it.name).put("size", it.formattedSize)
        } ?: JSONObject())

    private fun decodeMessages(json: String): List<ChatMessage> {
        return try {
            val arr = JSONArray(json)
            val out = ArrayList<ChatMessage>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val role = runCatching { MessageRole.valueOf(obj.optString("role", "USER")) }
                    .getOrDefault(MessageRole.USER)
                val status = runCatching { MessageStatus.valueOf(obj.optString("status", "SUCCESS")) }
                    .getOrDefault(MessageStatus.SUCCESS)
                out.add(
                    ChatMessage(
                        id = obj.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                        role = role,
                        content = obj.optString("content", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        status = status,
                        agentEmoji = obj.optString("agentEmoji", "").ifBlank { null },
                        isLocalExecution = obj.optBoolean("local", false)
                    )
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val PREFS = "agentlm_history"
        private const val KEY_SESSIONS = "sessions_v1"
        private const val KEY_DRAFT = "draft_v1"
        private const val MAX_SESSIONS = 40
        private const val MAX_DRAFT_MESSAGES = 60
    }
}
