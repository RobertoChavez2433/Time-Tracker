package com.robertochavez.timetracker.core.logging

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimeTrackerLogger @Inject constructor(@ApplicationContext context: Context) : AppLogger {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val buffer = ArrayDeque<AppLogEvent>()
    private val logFile = File(File(appContext.filesDir, "logs").also { it.mkdirs() }, "time_tracker.log")
    private val hostDrainEnabled = appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    override fun log(level: LogLevel, category: LogCategory, message: String, data: Map<String, Any?>, error: Throwable?) {
        val event = AppLogEvent(
            timestampEpochMillis = System.currentTimeMillis(),
            level = level,
            category = category,
            message = LogPayloadSanitizer.scrubMessage(message),
            data = LogPayloadSanitizer.scrubData(data),
            error = error?.message?.let(LogPayloadSanitizer::scrubMessage),
            stackTrace = error?.stackTraceToString()?.let(LogPayloadSanitizer::scrubMessage),
        )
        remember(event)
        writeLogcat(event)
        scope.launch {
            appendFile(event)
            drainToHost(event)
        }
    }

    override fun recentEvents(limit: Int): List<AppLogEvent> = synchronized(buffer) {
        buffer.takeLast(limit.coerceAtLeast(0))
    }

    private fun remember(event: AppLogEvent) {
        synchronized(buffer) {
            buffer.addLast(event)
            while (buffer.size > MAX_BUFFERED_EVENTS) {
                buffer.removeFirst()
            }
        }
    }

    private fun writeLogcat(event: AppLogEvent) {
        val tag = "TimeTracker/${event.category.wireName}"
        val line = buildString {
            append(event.message)
            if (event.data.isNotEmpty()) {
                append(" ")
                append(JsonEncoding.encode(event.data))
            }
        }
        when (event.level) {
            LogLevel.DEBUG -> Log.d(tag, line)
            LogLevel.INFO -> Log.i(tag, line)
            LogLevel.WARN -> Log.w(tag, line)
            LogLevel.ERROR -> Log.e(tag, line)
        }
    }

    private fun appendFile(event: AppLogEvent) {
        runCatching {
            rotateIfNeeded()
            logFile.appendText("${JsonEncoding.encode(event.toMap())}\n")
        }
    }

    private fun rotateIfNeeded() {
        if (logFile.length() <= MAX_LOG_BYTES) return
        val archive = File(logFile.parentFile, "time_tracker.previous.log")
        if (archive.exists()) {
            archive.delete()
        }
        logFile.renameTo(archive)
    }

    private fun drainToHost(event: AppLogEvent) {
        if (!hostDrainEnabled) return
        runCatching {
            val connection = URL(HOST_LOG_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = HOST_TIMEOUT_MILLIS
            connection.readTimeout = HOST_TIMEOUT_MILLIS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { stream ->
                stream.write(JsonEncoding.encode(event.toMap()).toByteArray())
            }
            connection.inputStream.use { it.readBytes() }
            connection.disconnect()
        }
    }

    companion object {
        private const val HOST_LOG_URL = "http://127.0.0.1:3947/log"
        private const val HOST_TIMEOUT_MILLIS = 750
        private const val MAX_BUFFERED_EVENTS = 500
        private const val MAX_LOG_BYTES = 2 * 1024 * 1024
    }
}
