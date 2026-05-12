package com.robertochavez.timetracker.testing

import android.content.Context
import android.content.pm.ApplicationInfo
import com.robertochavez.timetracker.BuildConfig
import com.robertochavez.timetracker.core.common.domain.ActivityBucketPolicy
import com.robertochavez.timetracker.core.common.domain.MotionActivity
import com.robertochavez.timetracker.core.common.domain.MotionTransition
import com.robertochavez.timetracker.core.common.repository.TrackingRepository
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
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebugStateEndpointServer @Inject constructor(
    @ApplicationContext context: Context,
    private val snapshotProvider: AppStateSnapshotProvider,
    private val setupSources: SetupSnapshotSources,
    private val trackingRepository: TrackingRepository,
    private val mockDriveSimulator: MockDriveSimulator,
    private val logger: AppLogger,
    private val clock: Clock,
) {
    private val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    private val enabled = debuggable && BuildConfig.E2E_DEBUG_ENABLED
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
            val query = parseQuery(uri.rawQuery.orEmpty())
            val runId = query["runId"] ?: "local"
            val actorId = query["actorId"] ?: "local"
            val payload = when (uri.path) {
                STATE_PATH -> statePayload(runId = runId, actorId = actorId)
                SEED_JOBSITE_DRIVE_PATH -> seedJobsiteDrivePayload(runId = runId, actorId = actorId)
                PREPARE_MOCK_DRIVE_PATH -> mockDriveSimulator.prepare(query.mockDriveConfig(), runId, actorId)
                INJECT_MOCK_DRIVE_PATH -> mockDriveSimulator.inject(query.mockDriveConfig(), runId, actorId)
                FINISH_MOCK_DRIVE_PATH -> mockDriveSimulator.finish(runId, actorId)
                else -> mapOf(
                    "error" to "Unknown endpoint",
                    "endpoints" to listOf(
                        STATE_PATH,
                        SEED_JOBSITE_DRIVE_PATH,
                        PREPARE_MOCK_DRIVE_PATH,
                        INJECT_MOCK_DRIVE_PATH,
                        FINISH_MOCK_DRIVE_PATH,
                    ),
                )
            }
            if (payload.containsKey("error")) {
                writeJson(client, 404, payload)
                return
            }
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
            "debugHarness" to mapOf(
                "debuggable" to debuggable,
                "e2eDebugEnabled" to BuildConfig.E2E_DEBUG_ENABLED,
            ),
            "snapshot" to snapshot.toMap(),
            "stateMachine" to state.toMap(),
            "recentLogs" to logs,
        )
    }

    private suspend fun seedJobsiteDrivePayload(runId: String, actorId: String): Map<String, Any?> {
        val localDate = Instant.now(clock).atZone(clock.zone).toLocalDate()
        val sessionStart = localDate.atTime(12, 0).atZone(clock.zone).toInstant().truncatedTo(ChronoUnit.SECONDS)
        val drivingStart = sessionStart.plus(5, ChronoUnit.MINUTES)
        val drivingEnd = drivingStart.plus(JOBSITE_DRIVE_MINUTES, ChronoUnit.MINUTES)
        val atWorkVehicleBucket = ActivityBucketPolicy.classify(
            activity = MotionActivity.IN_VEHICLE,
            transition = MotionTransition.ENTER,
            atWork = true,
        )

        setupSources.workPresenceRepository.setAtWork(atWork = true, updatedAt = sessionStart)
        setupSources.workSiteSessionRepository.startWorkSiteSession("work", sessionStart)
        val session = trackingRepository.startManualSession(sessionStart)
        trackingRepository.recordActivityTransition(atWorkVehicleBucket, drivingStart)
        trackingRepository.stopActiveAwaySession(drivingEnd)
        trackingRepository.setDrivenMiles(session.id, JOBSITE_DRIVE_MILES)
        setupSources.workSiteSessionRepository.stopActiveWorkSiteSession("work", drivingEnd)
        setupSources.workPresenceRepository.setAtWork(atWork = false, updatedAt = drivingEnd)

        val snapshot = snapshotProvider.build(runId = runId, actorId = actorId)
        val todayReport = snapshot.reportTotals.getValue("today")
        val proof = mapOf(
            "scenario" to "jobsite_geofence_mileage_policy",
            "sessionIdPrefix" to session.id.take(8),
            "atWorkVehicleBucket" to atWorkVehicleBucket.name,
            "seededMiles" to JOBSITE_DRIVE_MILES,
            "seededJobsiteDriveMinutes" to JOBSITE_DRIVE_MINUTES,
            "todayDrivenMiles" to todayReport.drivenMiles,
            "todayDriveMinutes" to todayReport.driveMinutes,
            "todaySiteMinutes" to todayReport.siteMinutes,
            "todayIdleMinutes" to todayReport.idleMinutes,
            "todayUnclassifiedMinutes" to todayReport.unclassifiedMinutes,
        )
        logger.info(LogCategory.TESTING, "Jobsite drive policy scenario seeded", proof)
        return mapOf(
            "transportReady" to true,
            "debugHarness" to mapOf(
                "debuggable" to debuggable,
                "e2eDebugEnabled" to BuildConfig.E2E_DEBUG_ENABLED,
            ),
            "proof" to proof,
            "snapshot" to snapshot.toMap(),
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

    private fun Map<String, String>.mockDriveConfig(): MockDriveConfig = MockDriveConfig(
        startLatitude = doubleValue("startLat", DEFAULT_MOCK_START_LATITUDE),
        startLongitude = doubleValue("startLng", DEFAULT_MOCK_START_LONGITUDE),
        homeRadiusMeters = doubleValue("homeRadiusMeters", DEFAULT_MOCK_HOME_RADIUS_METERS.toDouble()).toFloat(),
        distanceMeters = doubleValue("distanceMeters", DEFAULT_MOCK_DISTANCE_METERS),
        durationSeconds = longValue("durationSeconds", DEFAULT_MOCK_DURATION_SECONDS),
        pointCount = longValue("pointCount", DEFAULT_MOCK_POINT_COUNT.toLong()).toInt(),
        accuracyMeters = doubleValue("accuracyMeters", DEFAULT_MOCK_ACCURACY_METERS.toDouble()).toFloat(),
    )

    private fun Map<String, String>.doubleValue(name: String, defaultValue: Double): Double = this[name]?.toDoubleOrNull() ?: defaultValue

    private fun Map<String, String>.longValue(name: String, defaultValue: Long): Long = this[name]?.toLongOrNull() ?: defaultValue

    companion object {
        const val DEFAULT_PORT = 4958
        const val STATE_PATH = "/testing/state"
        const val SEED_JOBSITE_DRIVE_PATH = "/testing/seed-jobsite-drive"
        const val PREPARE_MOCK_DRIVE_PATH = "/testing/prepare-mock-drive"
        const val INJECT_MOCK_DRIVE_PATH = "/testing/inject-mock-drive"
        const val FINISH_MOCK_DRIVE_PATH = "/testing/finish-mock-drive"
        private const val LOOPBACK = "127.0.0.1"
        private const val RECENT_LOG_LIMIT = 25
        private const val JOBSITE_DRIVE_MILES = 6.75
        private const val JOBSITE_DRIVE_MINUTES = 30L
        private const val DEFAULT_MOCK_START_LATITUDE = 42.64517
        private const val DEFAULT_MOCK_START_LONGITUDE = -85.27639
        private const val DEFAULT_MOCK_HOME_RADIUS_METERS = 120f
        private const val DEFAULT_MOCK_DISTANCE_METERS = 3_218.688
        private const val DEFAULT_MOCK_DURATION_SECONDS = 240L
        private const val DEFAULT_MOCK_POINT_COUNT = 8
        private const val DEFAULT_MOCK_ACCURACY_METERS = 5f
    }
}
