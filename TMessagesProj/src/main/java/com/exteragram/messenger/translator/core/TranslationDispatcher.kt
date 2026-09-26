package com.exteragram.messenger.translator.core

import android.os.SystemClock
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLog
import org.telegram.messenger.Utilities
import java.io.IOException

object TranslationDispatcher {

    private val lock = Any()
    private val gates = HashMap<String, Gate>()

    fun interface Completion {
        fun onDone(texts: List<String>?, error: TranslationError?)
    }

    class Job(
        val translator: HttpTranslator,
        val tag: Long,
        val texts: List<String>,
        val fromLang: String,
        val toLang: String,
        val completion: Completion,
    ) {
        var attempt = 0
        var cancelled = false
        var call: Call? = null
    }

    class Gate(val limits: ProviderLimits) {
        val queue = ArrayDeque<Job>()
        val running = ArrayList<Job>()
        var lastStartedAt = 0L
        var cooldownUntil = 0L
        var scheduledPumpAt = 0L
        lateinit var pumpRunnable: Runnable
    }

    private fun gateOf(translator: HttpTranslator): Gate {
        val name = translator.javaClass.name
        return gates.getOrPut(name) {
            Gate(translator.limits).also { gate -> gate.pumpRunnable = Runnable { pump(gate) } }
        }
    }

    @JvmStatic
    @JvmOverloads
    fun enqueue(
        translator: HttpTranslator,
        tag: Long,
        texts: List<String>,
        fromLang: String,
        toLang: String,
        priority: Boolean = false,
        completion: Completion,
    ) {
        if (texts.isEmpty()) {
            AndroidUtilities.runOnUIThread { completion.onDone(emptyList(), null) }
            return
        }
        val gate = synchronized(lock) {
            gateOf(translator).also { gate ->
                val job = Job(translator, tag, texts, fromLang, toLang, completion)
                if (priority) {
                    gate.queue.addFirst(job)
                } else {
                    gate.queue.addLast(job)
                }
            }
        }
        Utilities.globalQueue.postRunnable { pump(gate) }
    }

    @JvmStatic
    fun cancel(tag: Long) {
        cancelMatching { it.tag == tag }
    }

    @JvmStatic
    fun cancelAll() {
        cancelMatching { true }
    }

    private fun cancelMatching(predicate: (Job) -> Boolean) {
        val calls = ArrayList<Call>()
        val removed = ArrayList<Job>()
        synchronized(lock) {
            for (gate in gates.values) {
                val matching = gate.queue.filter(predicate)
                gate.queue.removeAll(matching)
                removed.addAll(matching)
                for (job in gate.running) {
                    if (predicate(job)) {
                        job.cancelled = true
                        job.call?.let { calls.add(it) }
                    }
                }
            }
        }
        calls.forEach { it.cancel() }
        if (removed.isEmpty()) {
            return
        }
        AndroidUtilities.runOnUIThread {
            removed.forEach { it.completion.onDone(null, TranslationError.Cancelled) }
        }
    }

    private fun pump(gate: Gate) {
        while (true) {
            val job = nextJob(gate) ?: return
            start(gate, job)
        }
    }

    private fun nextJob(gate: Gate): Job? = synchronized(lock) {
        val limits = gate.limits
        if (gate.queue.isEmpty() || gate.running.size >= limits.maxConcurrent) {
            return@synchronized null
        }
        val now = SystemClock.elapsedRealtime()
        val readyAt = maxOf(gate.cooldownUntil, gate.lastStartedAt + limits.minIntervalMs)
        if (now < readyAt) {
            schedulePump(gate, readyAt - now)
            null
        } else {
            gate.queue.removeFirst().also {
                gate.lastStartedAt = now
                gate.running.add(it)
            }
        }
    }

    private fun schedulePump(gate: Gate, delay: Long) {
        val now = SystemClock.elapsedRealtime()
        val target = now + delay
        val scheduled = gate.scheduledPumpAt
        if (scheduled < now + 1 || scheduled > target) {
            gate.scheduledPumpAt = target
            Utilities.globalQueue.cancelRunnable(gate.pumpRunnable)
            Utilities.globalQueue.postRunnable(gate.pumpRunnable, delay)
        }
    }

    private fun start(gate: Gate, job: Job) {
        try {
            val call = job.translator.client.newCall(job.translator.buildRequest(job.texts, job.fromLang, job.toLang))
            val cancelled = synchronized(lock) {
                if (!job.cancelled) {
                    job.call = call
                }
                job.cancelled
            }
            if (cancelled) {
                finish(gate, job, null, null)
                return
            }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (call.isCanceled()) {
                        finish(gate, job, null, null)
                    } else {
                        FileLog.e(e)
                        retryOrFail(gate, job, TranslationError.Transient)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = try {
                        response.use { job.translator.parseResponse(it, job.texts.size) }
                    } catch (e: Exception) {
                        FileLog.e(e)
                        ProviderResponse.Failure(TranslationError.Transient)
                    }
                    when (result) {
                        is ProviderResponse.Success -> finish(gate, job, result.texts, null)
                        is ProviderResponse.Failure -> retryOrFail(gate, job, result.error)
                    }
                }
            })
        } catch (e: Exception) {
            FileLog.e(e)
            finish(gate, job, null, TranslationError.Fatal)
        }
    }

    private fun retryOrFail(gate: Gate, job: Job, error: TranslationError) {
        job.attempt++
        val limits = gate.limits
        if (!error.isRetryable || job.attempt >= limits.maxAttempts) {
            finish(gate, job, null, error)
            return
        }
        val delay = if (error is TranslationError.RateLimited && error.retryAfterMs > 0) {
            error.retryAfterMs
        } else {
            backoff(limits, job.attempt)
        }
        val cancelled = synchronized(lock) {
            gate.running.remove(job)
            job.call = null
            if (!job.cancelled) {
                if (error is TranslationError.RateLimited) {
                    gate.cooldownUntil = maxOf(gate.cooldownUntil, SystemClock.elapsedRealtime() + delay)
                }
                gate.queue.addFirst(job)
                schedulePump(gate, delay)
            }
            job.cancelled
        }
        if (cancelled) {
            AndroidUtilities.runOnUIThread { job.completion.onDone(null, TranslationError.Cancelled) }
            Utilities.globalQueue.postRunnable { pump(gate) }
            return
        }
        FileLog.d("translator: retrying in ${delay}ms after $error (attempt ${job.attempt})")
    }

    private fun backoff(limits: ProviderLimits, attempt: Int): Long {
        val delay = minOf(limits.baseBackoffMs shl (attempt - 1).coerceIn(0, 16), limits.maxBackoffMs)
        return delay + Utilities.random.nextInt((delay / 4).toInt().coerceAtLeast(1))
    }

    private fun finish(gate: Gate, job: Job, texts: List<String>?, error: TranslationError?) {
        val cancelled = synchronized(lock) {
            gate.running.remove(job)
            job.call = null
            job.cancelled
        }
        AndroidUtilities.runOnUIThread {
            if (cancelled) {
                job.completion.onDone(null, TranslationError.Cancelled)
            } else {
                job.completion.onDone(texts, error)
            }
        }
        Utilities.globalQueue.postRunnable { pump(gate) }
    }
}
