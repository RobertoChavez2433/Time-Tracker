package com.robertochavez.timetracker.testing

import com.robertochavez.timetracker.core.common.model.AwaySession

data class AppStateSnapshot(
    val runId: String,
    val actorId: String,
    val capturedAtEpochMillis: Long,
    val homeSet: Boolean,
    val homeRadiusMeters: Float?,
    val workSet: Boolean,
    val workRadiusMeters: Float?,
    val atWork: Boolean,
    val activeSession: AwaySessionSummary?,
    val sessionCount: Int,
    val activityIntervalCount: Int,
    val trackableToday: Boolean,
    val workdayCount: Int,
    val privacyDisclosureAccepted: Boolean,
    val minimalActiveNotificationEnabled: Boolean,
    val liveTimerNotificationEnabled: Boolean,
    val recentLogCount: Int,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "runId" to runId,
        "actorId" to actorId,
        "capturedAtEpochMillis" to capturedAtEpochMillis,
        "homeSet" to homeSet,
        "homeRadiusMeters" to homeRadiusMeters,
        "workSet" to workSet,
        "workRadiusMeters" to workRadiusMeters,
        "atWork" to atWork,
        "activeSession" to activeSession?.toMap(),
        "sessionCount" to sessionCount,
        "activityIntervalCount" to activityIntervalCount,
        "trackableToday" to trackableToday,
        "workdayCount" to workdayCount,
        "privacyDisclosureAccepted" to privacyDisclosureAccepted,
        "minimalActiveNotificationEnabled" to minimalActiveNotificationEnabled,
        "liveTimerNotificationEnabled" to liveTimerNotificationEnabled,
        "recentLogCount" to recentLogCount,
    )
}

data class AwaySessionSummary(val idPrefix: String, val startEpochMillis: Long, val endEpochMillis: Long?, val drivenMiles: Double) {
    fun toMap(): Map<String, Any?> = mapOf(
        "idPrefix" to idPrefix,
        "startEpochMillis" to startEpochMillis,
        "endEpochMillis" to endEpochMillis,
        "drivenMiles" to drivenMiles,
    )
}

fun AwaySession.toSummary(): AwaySessionSummary = AwaySessionSummary(
    idPrefix = id.take(8),
    startEpochMillis = start.toEpochMilli(),
    endEpochMillis = end?.toEpochMilli(),
    drivenMiles = drivenMiles,
)
