package com.robertochavez.timetracker.core.logging

data class AppLogEvent(
    val timestampEpochMillis: Long,
    val level: LogLevel,
    val category: LogCategory,
    val message: String,
    val data: Map<String, Any?> = emptyMap(),
    val error: String? = null,
    val stackTrace: String? = null,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "timestampEpochMillis" to timestampEpochMillis,
        "level" to level.name.lowercase(),
        "category" to category.wireName,
        "message" to message,
        "data" to data,
        "error" to error,
        "stackTrace" to stackTrace,
    ).filterValues { it != null }
}

object JsonEncoding {
    fun encode(value: Any?): String = when (value) {
        null -> "null"
        is String -> "\"${escape(value)}\""
        is Number,
        is Boolean,
        -> value.toString()
        is Map<*, *> -> value.entries.joinToString(prefix = "{", postfix = "}") { (key, item) ->
            "${encode(key.toString())}:${encode(item)}"
        }
        is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]") { encode(it) }
        is Array<*> -> value.joinToString(prefix = "[", postfix = "]") { encode(it) }
        else -> encode(value.toString())
    }

    private fun escape(value: String): String = buildString(value.length + 16) {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }
}
