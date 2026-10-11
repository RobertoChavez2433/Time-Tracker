package com.robertochavez.timetracker.testing

import java.io.IOException
import java.net.BindException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket

/** Side-by-side debug installations must not share another application's inspection endpoint. */
internal fun openDebugStateSocket(preferredPort: Int): ServerSocket = try {
    bindDebugSocket(preferredPort)
} catch (_: BindException) {
    bindDebugSocket(0)
}

private fun bindDebugSocket(port: Int): ServerSocket {
    val socket = ServerSocket()
    try {
        socket.reuseAddress = true
        socket.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port))
        return socket
    } catch (error: IOException) {
        socket.close()
        throw error
    }
}
