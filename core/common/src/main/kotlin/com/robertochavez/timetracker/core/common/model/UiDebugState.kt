package com.robertochavez.timetracker.core.common.model

data class UiDebugSnapshot(
    val currentRoute: String = "dashboard",
    val currentModal: String? = null,
    val selectedWeekStart: String = "",
    val selectedWeekEnd: String = "",
    val selectedWeekMode: String = "CURRENT_WEEK",
)

object UiDebugState {
    @Volatile
    var snapshot: UiDebugSnapshot = UiDebugSnapshot()
        private set

    fun setRoute(route: String) {
        snapshot = snapshot.copy(currentRoute = route)
    }

    fun setModal(modal: String?) {
        snapshot = snapshot.copy(currentModal = modal)
    }

    fun setSelectedWeek(start: String, end: String, mode: String) {
        snapshot = snapshot.copy(
            selectedWeekStart = start,
            selectedWeekEnd = end,
            selectedWeekMode = mode,
        )
    }
}
