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
                "activeSession" to (snapshot.activeSession != null),
                "sessionCount" to snapshot.sessionCount,
                "activityIntervalCount" to snapshot.activityIntervalCount,
                "workdayCount" to snapshot.workdayCount,
                "recentLogCount" to snapshot.recentLogCount,
            ),
        )
    }
}
