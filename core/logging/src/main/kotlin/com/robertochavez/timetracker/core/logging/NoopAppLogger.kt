package com.robertochavez.timetracker.core.logging

class NoopAppLogger : AppLogger {
    override fun log(level: LogLevel, category: LogCategory, message: String, data: Map<String, Any?>, error: Throwable?) = Unit

    override fun recentEvents(limit: Int): List<AppLogEvent> = emptyList()
}
