package com.example.service.engine

import android.os.Process
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher

/**
 * A tiny resource governor for on-device generation.
 *
 * Two knobs decide whether a reply freezes the phone, and both come from the reference client's
 * rule "never give the model every core":
 *
 * 1. **How many threads may run inference at all** — a fixed pool of `n` threads. The native
 *    runtime is invoked from this dispatcher, so at most `n` of *our* threads are ever busy and
 *    the caller cannot accidentally fan out across all cores.
 * 2. **At what priority they run** — [Process.THREAD_PRIORITY_BACKGROUND] (nice 10). Android's
 *    UI, input and SurfaceFlinger threads then always win the scheduler, so even a fully loaded
 *    NPU/CPU shows up as "a bit slower" instead of "the whole phone hung for 4 seconds".
 *    Because Linux children inherit the parent's nice value, native worker threads *spawned by
 *    the engine from these threads* are background-priority too, which is the part that actually
 *    keeps the screen responsive.
 */
internal object InferenceThreads {

    private const val TAG_POOL = "agentlm-infer"

    private class Pool(
        val size: Int,
        val lowPriority: Boolean,
        val executor: ExecutorService
    ) {
        val context: CoroutineDispatcher = executor.asCoroutineDispatcher()
        fun shutdown() = executor.shutdownNow()
    }

    @Volatile
    private var pool: Pool? = null

    /**
     * Returns the dispatcher to run generation on. Rebuilding the pool on a settings change is
     * deliberate: the alternative (re-nicing shared [kotlinx.coroutines.Dispatchers.IO] threads)
     * would leak background priority into unrelated work such as downloads and Room writes.
     */
    @Synchronized
    fun contextFor(threadCount: Int, lowPriority: Boolean): CoroutineContext {
        val size = threadCount.coerceIn(1, 16)
        pool?.let { existing ->
            if (existing.size == size && existing.lowPriority == lowPriority) return existing.context
            existing.shutdown()
        }
        val counter = AtomicInteger()
        val factory = ThreadFactory { runnable ->
            Thread(runnable, "$TAG_POOL-${counter.incrementAndGet()}").apply { isDaemon = true }
        }
        val created = Pool(size, lowPriority, Executors.newFixedThreadPool(size, factory))
        // setThreadPriority only affects the *calling* thread, so prime each pool thread with a
        // task that sets its own nice value before any real work is queued.
        repeat(size) {
            created.executor.submit {
                runCatching {
                    Process.setThreadPriority(
                        if (lowPriority) Process.THREAD_PRIORITY_BACKGROUND
                        else Process.THREAD_PRIORITY_DEFAULT
                    )
                }
            }
        }
        pool = created
        return created.context
    }

    /** Approximate CPU share the governor will allow, for display in Settings. */
    fun percentOfCores(cores: Int): Int =
        ((pool?.size ?: 0) * 100.0 / cores.coerceAtLeast(1)).toInt().coerceIn(0, 100)

    @Synchronized
    fun currentSize(): Int = pool?.size ?: 0

    @Synchronized
    fun release() {
        pool?.shutdown()
        pool = null
    }
}
