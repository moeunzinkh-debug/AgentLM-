package com.example.service

import com.example.model.ResponsePolicy
import com.example.service.engine.GenEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CancellationException

/**
 * The single most important class for "it must not freeze while answering".
 *
 * A model can produce 20-60 tokens/second. If every token became a state write, one long reply
 * would mean thousands of recompositions, each re-parsing Markdown and re-measuring the chat
 * list - exactly how a Compose chat UI turns into an ANR. So:
 *
 *  1. deltas land in a StringBuilder and are **coalesced** into at most one snapshot per
 *     [ResponsePolicy.flushIntervalMs], and only after [ResponsePolicy.minFlushChars];
 *  2. the messages list is never mutated mid-stream - the caller exposes this snapshot as its
 *     own StateFlow, so the LazyColumn keeps a stable item count while typing;
 *  3. every wait for the next token is bounded (prefill timeout, idle-token timeout, hard
 *     wall-clock cap), so a wedged engine ends the turn with the partial text instead of
 *     spinning forever;
 *  4. garbage deltas (control chars, template leakage, empty chunks) are dropped before they
 *     can trigger a repaint.
 *
 * Cancellation is immediate: CancellationException propagates to the engine, which cancels the
 * underlying HTTP call or native generation.
 */
class ResponseStreamer {

    class Stats(
        var tokensOut: Int = 0,
        var charsOut: Int = 0,
        var flushes: Int = 0,
        val startedAt: Long = System.currentTimeMillis(),
        var elapsedMs: Long = 0L,
        var timedOut: String? = null,
        var finishReason: String = "",
        var phase: String = "connecting"
    ) {
        val tokensPerSecond: Double
            get() = if (elapsedMs > 250) tokensOut * 1000.0 / elapsedMs else 0.0
    }

    sealed interface Outcome {
        data class Completed(val text: String, val stats: Stats) : Outcome
        data class Failed(val message: String, val hint: String?, val partial: String?) : Outcome
    }

    /**
     * @param onText  coalesced full-text snapshot for the streaming bubble
     * @param onStats coalesced telemetry (tokens/s, phase) for the status line
     * @param onFirstToken fires once when the first real token lands (TTFT marker)
     */
    suspend fun run(
        scope: CoroutineScope,
        events: Flow<GenEvent>,
        policy: ResponsePolicy,
        onText: (String) -> Unit,
        onStats: (Stats) -> Unit,
        onFirstToken: () -> Unit = {}
    ): Outcome {
        val acc = StringBuilder()
        val stats = Stats()
        var lastFlushAt = 0L
        var lastFlushLen = 0
        var firstTokenSeen = false
        var finished = false
        var failure: Outcome.Failed? = null
        val hardDeadline = stats.startedAt + policy.hardTimeoutSec * 1000L

        fun flush(force: Boolean) {
            val now = System.currentTimeMillis()
            val grew = acc.length - lastFlushLen
            if (!force) {
                if (now - lastFlushAt < policy.flushIntervalMs) return
                if (grew < policy.minFlushChars && !finished) return
            } else if (grew == 0 && lastFlushLen > 0) {
                return
            }
            lastFlushAt = now
            lastFlushLen = acc.length
            stats.charsOut = acc.length
            stats.flushes++
            onText(acc.toString())
            onStats(stats)
        }

        val channel: Channel<GenEvent> = events.buffer(128).produceIn(scope)
        try {
            while (true) {
                val now = System.currentTimeMillis()
                if (now >= hardDeadline) {
                    stats.timedOut = "hard"
                    stats.phase = "timeout"
                    break
                }
                val base = if (!firstTokenSeen) policy.prefillTimeoutSec * 1000L
                else policy.idleTokenTimeoutSec * 1000L
                val waitMs = base.coerceAtMost((hardDeadline - now).coerceAtLeast(1L))

                val received = withTimeoutOrNull(waitMs) { channel.receiveCatching() }
                if (received == null) {
                    stats.timedOut = if (!firstTokenSeen) "prefill" else "idle"
                    stats.phase = "timeout"
                    break
                }
                val event = received.getOrNull()
                if (event == null) break // upstream closed without a terminal event

                when (event) {
                    is GenEvent.Progress -> {
                        stats.phase = event.phase
                        onStats(stats)
                    }

                    is GenEvent.Delta -> {
                        val clean = sanitize(event.text)
                        if (clean.isNotEmpty()) {
                            if (!firstTokenSeen) {
                                firstTokenSeen = true
                                stats.phase = "streaming"
                                onFirstToken()
                            }
                            if (policy.maxResponseChars in 1..acc.length) {
                                stats.finishReason = "length-cap"
                                finished = true
                                flush(true)
                                break
                            }
                            acc.append(clean)
                            stats.tokensOut++
                            flush(false)
                        }
                    }

                    is GenEvent.Done -> {
                        val finalText = event.fullText.ifBlank { acc.toString() }
                        // Prefer the engine's own full text, but never regress to something shorter
                        // than what the user has already watched being typed.
                        if (finalText.length >= acc.length) {
                            acc.setLength(0)
                            acc.append(finalText)
                        }
                        if (event.tokensOut > 0) stats.tokensOut = event.tokensOut
                        stats.finishReason = event.finishReason
                        stats.elapsedMs = if (event.elapsedMs > 0) event.elapsedMs
                        else System.currentTimeMillis() - stats.startedAt
                        stats.phase = "done"
                        finished = true
                        flush(true)
                    }

                    is GenEvent.Failed -> {
                        failure = Outcome.Failed(event.message, event.hint, acc.toString())
                        stats.phase = "failed"
                        finished = true
                        flush(true)
                    }
                }
                if (finished) break
            }
        } catch (e: CancellationException) {
            flush(true)
            throw e
        } finally {
            channel.cancel()
            if (stats.elapsedMs <= 0L) stats.elapsedMs = System.currentTimeMillis() - stats.startedAt
        }

        failure?.let { return it }

        val text = acc.toString()
        if (text.isBlank()) {
            val why = when (stats.timedOut) {
                "prefill" -> "The engine accepted the request but produced no token for " + policy.prefillTimeoutSec + "s."
                "idle" -> "Token generation stalled after " + stats.tokensOut + " tokens."
                "hard" -> "Generation exceeded the " + policy.hardTimeoutSec + "s hard limit and was stopped."
                else -> "The engine closed the stream without any text."
            }
            val hint = when (stats.timedOut) {
                "hard", "idle" ->
                    "Lower Max response tokens, shorten the attached file, or pick a smaller model in Model Hub."
                else -> "Check the model tag and key in Settings - Inference Engine, then press Test."
            }
            return Outcome.Failed(why, hint, null)
        }
        if (stats.finishReason.isEmpty()) {
            stats.finishReason = if (stats.timedOut != null) "truncated" else "stop"
        }
        flush(true)
        return Outcome.Completed(text, stats)
    }

    /**
     * Drops tokenizer noise that would otherwise cause repaints and visible garbage - the same
     * class of bug that made Gemma replies come back empty on some phones.
     */
    private fun sanitize(delta: String): String {
        if (delta.isEmpty()) return ""
        var out = delta
        for (marker in TEMPLATE_MARKERS) {
            val idx = out.indexOf(marker)
            if (idx >= 0) out = out.substring(0, idx)
        }
        if (out.isEmpty()) return ""

        val sb = StringBuilder(out.length)
        for (ch in out) {
            when {
                ch == '\n' || ch == '\t' || ch == '\r' -> sb.append(ch)
                ch.code < 0x20 -> Unit
                ch.code == 0xFEFF -> Unit
                ch.isHighSurrogate() -> sb.append(ch)
                ch.isLowSurrogate() -> {
                    if (sb.isNotEmpty() && sb.last().isHighSurrogate()) sb.append(ch)
                }
                else -> sb.append(ch)
            }
        }
        var cleaned = sb.toString()
        if (cleaned.isEmpty()) return ""
        // A pure whitespace burst still repaints; collapse it.
        if (cleaned.length > 2 && cleaned.all { it == ' ' || it == '\n' }) cleaned = "\n\n"
        return cleaned
    }

    companion object {
        private val TEMPLATE_MARKERS = listOf(
            "<" + "|im_end|>",
            "<" + "|end|>",
            "</s>",
            "<" + "|user|>",
            "<" + "|assistant|>",
            "<" + "|system|>"
        )
    }
}
