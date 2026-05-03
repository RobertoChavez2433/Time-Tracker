package com.robertochavez.timetracker.testing

import android.content.Context
import android.content.pm.ApplicationInfo
import com.robertochavez.timetracker.core.logging.AppLogger
import com.robertochavez.timetracker.core.logging.JsonEncoding
import com.robertochavez.timetracker.core.logging.LogCategory
import com.robertochavez.timetracker.core.logging.LogLevel
import com.robertochavez.timetracker.core.logging.info
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebugStateEndpointServer @Inject constructor(
    @ApplicationContext context: Context,
    private val snapshotProvider: AppStateSnapshotProvider,
    private val logger: AppLogger,
) {
    private val enabled = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null

    fun start(port: Int = DEFAULT_PORT) {
        if (!enabled || serverSocket != null) return
        scope.launch {
            runCatching {
                val socket = ServerSocket()
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(InetAddress.getByName(LOOPBACK), port))
                serverSocket = socket
                logger.info(LogCategory.TESTING, "Debug state endpoint listening", mapOf("port" to port))
                acceptLoop(socket)
            }.onFailure { error ->
                logger.log(LogLevel.ERROR, LogCategory.TESTING, "Debug state endpoint failed", error = error)
            }
        }
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (!socket.isClosed) {
            val client = runCatching { socket.accept() }.getOrNull() ?: continue
            scope.launch { handleClient(client) }
        }
    }

    private suspend fun handleClient(socket: Socket) {
        socket.use { client ->
            val reader = client.getInputStream().bufferedReader()
            val requestLine = reader.readLine().orEmpty()
            while (!reader.readLine().isNullOrEmpty()) {
                // Drain headers.
            }
            val parts = requestLine.split(" ")
            if (parts.size < 2 || parts[0] != "GET") {
                writeJson(client, 405, mapOf("error" to "Use GET /testing/state"))
                return
            }

            val uri = URI("http://$LOOPBACK${parts[1]}")
            if (uri.path != STATE_PATH) {
                writeJson(client, 404, mapOf("error" to "Unknown endpoint", "endpoint" to STATE_PATH))
                return
            }

            val query = parseQuery(uri.rawQuery.orEmpty())
            val payload = statePayload(
                runId = query["runId"] ?: "local",
                actorId = query["actorId"] ?: "local",
            )
            writeJson(client, 200, payload)
        }
    }

    private suspend fun statePayload(runId: String, actorId: String): Map<String, Any?> {
        val snapshot = snapshotProvider.build(runId = runId, actorId = actorId)
        val state = TestingStateMachine.evaluate(snapshot)
        val logs = logger.recentEvents(limit = RECENT_LOG_LIMIT).map { it.toMap() }
        logger.info(LogCategory.TESTING, "Testing state requested", mapOf("posture" to state.posture))
        return mapOf(
            "transportReady" to true,
            "snapshot" to snapshot.toMap(),
            "stateMachine" to state.toMap(),
            "recentLogs" to logs,
        )
    }

    private fun writeJson(socket: Socket, status: Int, payload: Map<String, Any?>) {
        val body = JsonEncoding.encode(payload).toByteArray(Charsets.UTF_8)
        val statusText = if (status in 200..299) "OK" else "ERROR"
        val header = "HTTP/1.1 $status $statusText\r\n" +
            "Content-Type: application/json\r\n" +
            "Content-Length: ${body.size}\r\n\r\n"
        socket.getOutputStream().write(header.toByteArray(Charsets.UTF_8))
        socket.getOutputStream().write(body)
        socket.getOutputStream().flush()
    }

    private fun parseQuery(rawQuery: String): Map<String, String> {
        if (rawQuery.isBlank()) return emptyMap()
        return rawQuery.split("&").mapNotNull { part ->
            val pieces = part.split("=", limit = 2)
            if (pieces.size != 2) return@mapNotNull null
            decode(pieces[0]) to decode(pieces[1])
        }.toMap()
    }

    private fun decode(value: String): String = URLDecoder.decode(value, Charsets.UTF_8.name())

    companion object {
        const val DEFAULT_PORT = 4948
        const val STATE_PATH = "/testing/state"
        private const val LOOPBACK = "127.0.0.1"
        private const val RECENT_LOG_LIMIT = 25
    }
}
