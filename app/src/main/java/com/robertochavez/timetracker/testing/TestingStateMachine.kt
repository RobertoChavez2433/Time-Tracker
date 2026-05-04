package com.robertochavez.timetracker.testing

data class TestingState(
    val posture: String,
    val interactionReady: Boolean,
    val interactionBlockers: List<String>,
    val facts: Map<String, Any?>,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "posture" to posture,
        "interactionReady" to interactionReady,
        "interactionBlockers" to interactionBlockers,
        "facts" to facts,
    )
}

object TestingStateMachine {
    fun evaluate(snapshot: AppStateSnapshot): TestingState {
        val blockers = buildList {
            if (!snapshot.privacyDisclosureAccepted) add("privacy_disclosure_pending")
            if (!snapshot.homeSet) add("home_location_missing")
            if (!snapshot.trackableToday) add("today_not_trackable")
        }
        val posture = when {
            blockers.isNotEmpty() -> "setupRequired"
            snapshot.activeSession != null -> "tracking"
            else -> "idle"
        }
        return TestingState(
            posture = posture,
            interactionReady = blockers.isEmpty(),
            interactionBlockers = blockers,
            facts = mapOf(
                "homeSet" to snapshot.homeSet,
                "homeLatitude" to snapshot.homeLatitude,
                "homeLongitude" to snapshot.homeLongitude,
                "workSet" to snapshot.workSet,
                "workLocationCount" to snapshot.workLocationCount,
                "workLocations" to snapshot.workLocations,
                "workLatitude" to snapshot.workLatitude,
                "workLongitude" to snapshot.workLongitude,
                "atWork" to snapshot.atWork,
                "activeSession" to (snapshot.activeSession != null),
                "sessionCount" to snapshot.sessionCount,
                "countedSessionCount" to snapshot.countedSessionCount,
                "manuallyAdjustedSessionCount" to snapshot.manuallyAdjustedSessionCount,
                "totalDrivenMiles" to snapshot.totalDrivenMiles,
                "activityIntervalCount" to snapshot.activityIntervalCount,
                "trackableToday" to snapshot.trackableToday,
                "todayDayOfWeek" to snapshot.todayDayOfWeek,
                "workdayCount" to snapshot.workdayCount,
                "workdays" to snapshot.workdays,
                "reportTotals" to snapshot.reportTotals.mapValues { it.value.toMap() },
                "recentLogCount" to snapshot.recentLogCount,
            ),
        )
    }
}
