package com.robertochavez.timetracker.core.logging

object LogPayloadSanitizer {
    private val sensitiveKeyFragments = setOf(
        "access",
        "address",
        "email",
        "fine",
        "lat",
        "lng",
        "location",
        "lon",
        "password",
        "precise",
        "token",
    )
    private val emailRegex = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val longNumberRegex = Regex("[-+]?\\d{2,3}\\.\\d{4,}")

    fun scrubMessage(message: String): String = message
        .replace(emailRegex, "[redacted-email]")
        .replace(longNumberRegex, "[redacted-coordinate]")

    fun scrubData(data: Map<String, Any?>): Map<String, Any?> = data.mapValues { (key, value) ->
        if (isSensitiveKey(key)) {
            "[redacted]"
        } else {
            scrubValue(value)
        }
    }

    fun scrubValue(value: Any?): Any? = when (value) {
        null -> null
        is String -> scrubMessage(value)
        is Number,
        is Boolean,
        -> value
        is Map<*, *> -> scrubData(value.mapKeys { it.key.toString() })
        is Iterable<*> -> value.map(::scrubValue)
        is Array<*> -> value.map(::scrubValue)
        else -> scrubMessage(value.toString())
    }

    private fun isSensitiveKey(key: String): Boolean {
        val normalized = key.lowercase()
        return sensitiveKeyFragments.any { normalized.contains(it) }
    }
}
