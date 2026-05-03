package com.robertochavez.timetracker.core.logging

interface AppLogger {
    fun log(level: LogLevel, category: LogCategory, message: String, data: Map<String, Any?> = emptyMap(), error: Throwable? = null)

    fun recentEvents(limit: Int = 100): List<AppLogEvent>
}

fun AppLogger.debug(category: LogCategory, message: String, data: Map<String, Any?> = emptyMap()) {
    log(LogLevel.DEBUG, category, message, data)
}

fun AppLogger.info(category: LogCategory, message: String, data: Map<String, Any?> = emptyMap()) {
    log(LogLevel.INFO, category, message, data)
}

fun AppLogger.warn(category: LogCategory, message: String, data: Map<String, Any?> = emptyMap(), error: Throwable? = null) {
    log(LogLevel.WARN, category, message, data, error)
}

fun AppLogger.error(category: LogCategory, message: String, data: Map<String, Any?> = emptyMap(), error: Throwable? = null) {
    log(LogLevel.ERROR, category, message, data, error)
}

enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

enum class LogCategory(val wireName: String) {
    APP("app"),
    LIFECYCLE("lifecycle"),
    UI("ui"),
    TRACKING("tracking"),
    LOCATION("location"),
    ACTIVITY("activity"),
    DATABASE("database"),
    SETTINGS("settings"),
    REPORTS("reports"),
    TESTING("testing"),
}
