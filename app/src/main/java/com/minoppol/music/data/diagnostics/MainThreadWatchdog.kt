package com.minoppol.music.data.diagnostics

import android.os.Looper
import android.os.SystemClock
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

class MainThreadWatchdog(
    private val outputDir: File,
    private val stallThresholdMs: Long = 3000L,
) {
    private val mainHandler = android.os.Handler(Looper.getMainLooper())
    private val postedTick = AtomicLong(0L)
    private val handledTick = AtomicLong(0L)

    @Volatile
    private var running = false

    fun start() {
        if (running) return
        running = true
        Thread({
            while (running) {
                val tick = postedTick.incrementAndGet()
                mainHandler.post { handledTick.set(tick) }
                SystemClock.sleep(1000L)
                val posted = postedTick.get()
                val handled = handledTick.get()
                if (posted - handled >= stallThresholdMs / 1000L) {
                    dumpStack()
                    handledTick.set(posted)
                }
            }
        }, "PixelPlayer-MainThreadWatchdog").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
    }

    private fun dumpStack() {
        runCatching {
            outputDir.mkdirs()
            val time = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
            val file = File(outputDir, "stall_${time}.txt")
            val sb = StringBuilder()
            sb.appendLine("=== MainThread stall dump ===")
            sb.appendLine("elapsedRealtime: ${SystemClock.elapsedRealtime()}")

            val runtime = Runtime.getRuntime()
            val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
            val heapMb = runtime.totalMemory() / 1024 / 1024
            val maxMb = runtime.maxMemory() / 1024 / 1024
            sb.appendLine("heap used=${usedMb}MB allocated=${heapMb}MB max=${maxMb}MB")

            sb.appendLine()
            sb.appendLine("=== Main thread stack ===")
            Looper.getMainLooper().thread.stackTrace.forEach { sb.appendLine(it.toString()) }

            sb.appendLine()
            sb.appendLine("=== All threads ===")
            for ((thread, stack) in Thread.getAllStackTraces()) {
                sb.appendLine("\n-- ${thread.name} (${thread.state}) --")
                stack.forEach { sb.appendLine(it.toString()) }
            }

            file.writeText(sb.toString())
            Timber.d("MainThreadWatchdog dumped stall stack to ${file.absolutePath}")
        }.onFailure {
            Timber.e(it, "MainThreadWatchdog dump failed")
        }
    }
}