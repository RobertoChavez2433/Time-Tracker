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
    val latestSession: AwaySessionSummary?,
    val sessionCount: Int,
    val countedSessionCount: Int,
    val manuallyAdjustedSessionCount: Int,
    val totalDrivenMiles: Double,
    val activityIntervalCount: Int,
    val trackableToday: Boolean,
    val todayDayOfWeek: String,
    val workdayCount: Int,
    val payPeriodAnchorDate: String,
    val privacyDisclosureAccepted: Boolean,
    val minimalActiveNotificationEnabled: Boolean,
    val liveTimerNotificationEnabled: Boolean,
    val reportTotals: Map<String, ReportSnapshot> = emptyMap(),
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
        "latestSession" to latestSession?.toMap(),
        "sessionCount" to sessionCount,
        "countedSessionCount" to countedSessionCount,
        "manuallyAdjustedSessionCount" to manuallyAdjustedSessionCount,
        "totalDrivenMiles" to totalDrivenMiles,
        "activityIntervalCount" to activityIntervalCount,
        "trackableToday" to trackableToday,
        "todayDayOfWeek" to todayDayOfWeek,
        "workdayCount" to workdayCount,
        "payPeriodAnchorDate" to payPeriodAnchorDate,
        "privacyDisclosureAccepted" to privacyDisclosureAccepted,
        "minimalActiveNotificationEnabled" to minimalActiveNotificationEnabled,
        "liveTimerNotificationEnabled" to liveTimerNotificationEnabled,
        "reportTotals" to reportTotals.mapValues { it.value.toMap() },
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

data class ReportSnapshot(
    val awayMinutes: Long,
    val drivenMiles: Double,
    val driveMinutes: Long,
    val idleMinutes: Long,
    val unclassifiedMinutes: Long,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "awayMinutes" to awayMinutes,
        "drivenMiles" to drivenMiles,
        "driveMinutes" to driveMinutes,
        "idleMinutes" to idleMinutes,
        "unclassifiedMinutes" to unclassifiedMinutes,
    )
}

fun AwaySession.toSummary(): AwaySessionSummary = AwaySessionSummary(
    idPrefix = id.take(8),
    startEpochMillis = start.toEpochMilli(),
    endEpochMillis = end?.toEpochMilli(),
    drivenMiles = drivenMiles,
)
