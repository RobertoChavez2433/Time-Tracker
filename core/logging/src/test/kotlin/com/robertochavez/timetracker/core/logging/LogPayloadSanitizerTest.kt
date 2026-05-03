package com.robertochavez.timetracker.core.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LogPayloadSanitizerTest {
    @Test
    fun scrubDataRedactsSensitiveKeysAndCoordinates() {
        val scrubbed = LogPayloadSanitizer.scrubData(
            mapOf(
                "latitude" to 42.123456,
                "longitude" to -83.123456,
                "message" to "email user@example.com at 42.123456",
                "safe" to "tracking started",
            ),
        )

        assertEquals("[redacted]", scrubbed["latitude"])
        assertEquals("[redacted]", scrubbed["longitude"])
        assertEquals("email [redacted-email] at [redacted-coordinate]", scrubbed["message"])
        assertEquals("tracking started", scrubbed["safe"])
    }

    @Test
    fun jsonEncodingEscapesStrings() {
        val encoded = JsonEncoding.encode(mapOf("message" to "line\n\"quoted\""))

        assertFalse(encoded.contains("\n"))
        assertEquals("{\"message\":\"line\\n\\\"quoted\\\"\"}", encoded)
    }
}
