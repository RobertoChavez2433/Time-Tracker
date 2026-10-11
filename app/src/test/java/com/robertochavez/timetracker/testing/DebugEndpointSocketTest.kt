package com.robertochavez.timetracker.testing

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugEndpointSocketTest {
    @Test fun `occupied debug port falls back to an isolated loopback listener`() {
        openDebugStateSocket(0).use { first ->
            openDebugStateSocket(first.localPort).use { second ->
                assertNotEquals(first.localPort, second.localPort)
                assertTrue(first.inetAddress.isLoopbackAddress)
                assertTrue(second.inetAddress.isLoopbackAddress)
            }
        }
    }
}
