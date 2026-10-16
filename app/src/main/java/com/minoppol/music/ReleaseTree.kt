package com.minoppol.music

import android.content.Context
import android.os.Build
import android.util.Log
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A release-optimized Timber Tree that:
 * - Only logs WARN, ERROR, and WTF (suppresses VERBOSE, DEBUG, and INFO)
 * - Strips method/line information for performance
 */
class ReleaseTree(
    private val logToLogcat: Boolean = true
) : Timber.Tree() {

    override fun isLoggable(tag: String?, priority: Int): Boolean {
        return true
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        record(priority, tag, message, t)
        if (!logToLogcat) return
        if (priority < Log.WARN) return

        when (priority) {
            Log.WARN -> Log.w(tag, message, t)
            Log.ERROR -> Log.e(tag, message, t)
            Log.ASSERT -> Log.wtf(tag, message, t)
        }

    }

    private fun record(priority: Int, tag: String?, message: String, t: Throwable?) {
        synchronized(entries) {
            entries.addLast(formatEntry(priority, tag, message, t))
            while (entries.size > RING_CAPACITY) {
                entries.removeFirst()
            }
        }
    }

    private fun formatEntry(priority: Int, tag: String?, message: String, t: Throwable?): String {
        val builder = StringBuilder(message.length.coerceAtLeast(64))
        builder.append(timestampFormat.get()!!.format(Date(System.currentTimeMillis())))
            .append(' ')
            .append(priorityLabel(priority))
            .append('/')
            .append(tag ?: "unknown")
            .append(": ")
        builder.append(
            if (message.length > MAX_ENTRY_LENGTH) {
                message.take(MAX_ENTRY_LENGTH) + "..."
            } else {
                message
            }
        )
        if (t != null) {
            builder.append('\n').append(Log.getStackTraceString(t).take(MAX_ENTRY_LENGTH))
        }
        return builder.toString()
    }

    private fun priorityLabel(priority: Int): String = when (priority) {
        Log.VERBOSE -> "V"
        Log.DEBUG -> "D"
        Log.INFO -> "I"
        Log.WARN -> "W"
        Log.ERROR -> "E"
        Log.ASSERT -> "A"
        else -> "?"
    }

    companion object {
        private const val RING_CAPACITY = 600
        private const val MAX_ENTRY_LENGTH = 2000
        private const val MAX_DUMP_FILES = 2
        private const val DUMP_FILE_PREFIX = "runlog-"

        private val entries = ArrayDeque<String>(RING_CAPACITY)
        private val timestampFormat = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat =
                SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
        }

        fun dumpToString(): String? = synchronized(entries) {
            if (entries.isEmpty()) {
                null
            } else {
                buildString {
                    appendLine(
                        "=== PixelPlayerOLX Run Log (${BuildConfig.BUILD_TYPE} " +
                            "${BuildConfig.VERSION_NAME}, Android ${Build.VERSION.RELEASE}) ==="
                    )
                    append(entries.joinToString("\n"))
                }
            }
        }

        fun writeDumpTo(context: Context): File? = try {
            val root = context.getExternalFilesDir(null) ?: return null
            val dir = File(root, "logs").apply {
                if (!exists()) mkdirs()
            }
            val dump = dumpToString() ?: return null
            val file = File(dir, "$DUMP_FILE_PREFIX${System.currentTimeMillis()}.txt")
            file.writeText(dump)
            dir.listFiles { f -> f.name.startsWith(DUMP_FILE_PREFIX) }
                ?.sortedByDescending { it.name }
                ?.drop(MAX_DUMP_FILES)
                ?.forEach { it.delete() }
            file
        } catch (_: Exception) {
            null
        }
    }
}
