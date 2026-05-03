package com.robertochavez.timetracker.core.testing

import com.robertochavez.timetracker.core.common.model.ActivityBucket
import com.robertochavez.timetracker.core.common.model.ActivityInterval
import com.robertochavez.timetracker.core.common.model.AppSettings
import com.robertochavez.timetracker.core.common.model.AwaySession
import com.robertochavez.timetracker.core.common.model.HomeLocation
import com.robertochavez.timetracker.core.common.model.PayPeriodSettings
import com.robertochavez.timetracker.core.common.model.WorkLocation
import com.robertochavez.timetracker.core.common.model.WorkPresence
import com.robertochavez.timetracker.core.common.model.WorkSchedule
import com.robertochavez.timetracker.core.common.repository.AppSettingsRepository
import com.robertochavez.timetracker.core.common.repository.HomeLocationRepository
import com.robertochavez.timetracker.core.common.repository.LocalDataResetter
import com.robertochavez.timetracker.core.common.repository.PayPeriodSettingsRepository
import com.robertochavez.timetracker.core.common.repository.TrackingRepository
import com.robertochavez.timetracker.core.common.repository.WorkLocationRepository
import com.robertochavez.timetracker.core.common.repository.WorkPresenceRepository
import com.robertochavez.timetracker.core.common.repository.WorkScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import java.time.LocalDate

class FakeHomeLocationRepository(initialHomeLocation: HomeLocation? = null) : HomeLocationRepository {
    val homeLocation = MutableStateFlow(initialHomeLocation)

    override fun observeHomeLocation(): Flow<HomeLocation?> = homeLocation

    override suspend fun getHomeLocation(): HomeLocation? = homeLocation.value

    override suspend fun setHomeLocation(homeLocation: HomeLocation) {
        this.homeLocation.value = homeLocation
    }
}

class FakeWorkLocationRepository(initialWorkLocation: WorkLocation? = null) : WorkLocationRepository {
    val workLocation = MutableStateFlow(initialWorkLocation)

    override fun observeWorkLocation(): Flow<WorkLocation?> = workLocation

    override suspend fun getWorkLocation(): WorkLocation? = workLocation.value

    override suspend fun setWorkLocation(workLocation: WorkLocation) {
        this.workLocation.value = workLocation
    }
}

class FakeWorkPresenceRepository(initialWorkPresence: WorkPresence = WorkPresence(false, Instant.EPOCH)) : WorkPresenceRepository {
    val workPresence = MutableStateFlow(initialWorkPresence)

    override fun observeWorkPresence(): Flow<WorkPresence> = workPresence

    override suspend fun getWorkPresence(): WorkPresence = workPresence.value

    override suspend fun setAtWork(atWork: Boolean, updatedAt: Instant) {
        workPresence.value = WorkPresence(atWork = atWork, updatedAt = updatedAt)
    }
}

class FakeTrackingRepository(initialSessions: List<AwaySession> = emptyList(), initialIntervals: List<ActivityInterval> = emptyList()) :
    TrackingRepository {
    val sessions = MutableStateFlow(initialSessions)
    val intervals = MutableStateFlow(initialIntervals)
    val manualStarts = mutableListOf<Instant>()
    val activeStops = mutableListOf<Instant>()
    val activityTransitions = mutableListOf<Pair<ActivityBucket, Instant>>()

    override fun observeSessions(): Flow<List<AwaySession>> = sessions

    override fun observeActivityIntervals(): Flow<List<ActivityInterval>> = intervals

    override suspend fun startManualSession(at: Instant?): AwaySession {
        val start = requireNotNull(at) { "FakeTrackingRepository requires explicit start times." }
        manualStarts += start
        val session = AwaySession(id = "manual-${manualStarts.size}", start = start, end = null)
        sessions.value = sessions.value + session
        return session
    }

    override suspend fun startAwaySessionIfTrackable(at: Instant): AwaySession? = startManualSession(at)

    override suspend fun stopActiveAwaySession(at: Instant): AwaySession? {
        activeStops += at
        val active = sessions.value.firstOrNull { it.isActive } ?: return null
        val stopped = active.copy(end = at)
        sessions.value = sessions.value.map { if (it.id == active.id) stopped else it }
        return stopped
    }

    override suspend fun recordActivityTransition(bucket: ActivityBucket, at: Instant) {
        activityTransitions += bucket to at
    }

    override suspend fun updateSessionWindow(sessionId: String, start: Instant, end: Instant?): AwaySession? =
        updateSession(sessionId) { it.copy(start = start, end = end, manuallyAdjusted = true) }

    override suspend fun setCountsTowardTotals(sessionId: String, countsTowardTotals: Boolean): AwaySession? =
        updateSession(sessionId) { it.copy(countsTowardTotals = countsTowardTotals, manuallyAdjusted = true) }

    override suspend fun setDrivenMiles(sessionId: String, drivenMiles: Double): AwaySession? =
        updateSession(sessionId) { it.copy(drivenMiles = drivenMiles, manuallyAdjusted = true) }

    override suspend fun replaceActivityIntervals(sessionId: String, intervals: List<ActivityInterval>): AwaySession? {
        this.intervals.value = this.intervals.value.filterNot { it.sessionId == sessionId } + intervals
        return updateSession(sessionId) { it.copy(manuallyAdjusted = true) }
    }

    private fun updateSession(sessionId: String, transform: (AwaySession) -> AwaySession): AwaySession? {
        var updated: AwaySession? = null
        sessions.value = sessions.value.map {
            if (it.id == sessionId) {
                transform(it).also { session -> updated = session }
            } else {
                it
            }
        }
        return updated
    }
}

class FakeWorkScheduleRepository(initialSchedule: WorkSchedule = WorkSchedule()) : WorkScheduleRepository {
    val schedule = MutableStateFlow(initialSchedule)

    override fun observeWorkSchedule(): Flow<WorkSchedule> = schedule

    override suspend fun getWorkSchedule(): WorkSchedule = schedule.value

    override suspend fun setWorkSchedule(schedule: WorkSchedule) {
        this.schedule.value = schedule
    }
}

class FakePayPeriodSettingsRepository(initialSettings: PayPeriodSettings = PayPeriodSettings(LocalDate.of(2026, 4, 20))) :
    PayPeriodSettingsRepository {
    val settings = MutableStateFlow(initialSettings)

    override fun observeSettings(): Flow<PayPeriodSettings> = settings

    override suspend fun getSettings(): PayPeriodSettings = settings.value

    override suspend fun setSettings(settings: PayPeriodSettings) {
        this.settings.value = settings
    }
}

class FakeAppSettingsRepository(
    initialSettings: AppSettings = AppSettings(
        minimalActiveNotificationEnabled = false,
        liveTimerNotificationEnabled = false,
        privacyDisclosureAccepted = false,
    ),
) : AppSettingsRepository {
    override val settings = MutableStateFlow(initialSettings)

    override suspend fun setMinimalActiveNotificationEnabled(enabled: Boolean) {
        settings.value = settings.value.copy(minimalActiveNotificationEnabled = enabled)
    }

    override suspend fun setLiveTimerNotificationEnabled(enabled: Boolean) {
        settings.value = settings.value.copy(liveTimerNotificationEnabled = enabled)
    }

    override suspend fun setPrivacyDisclosureAccepted(accepted: Boolean) {
        settings.value = settings.value.copy(privacyDisclosureAccepted = accepted)
    }

    override suspend fun resetSettings() {
        settings.value = AppSettings(
            minimalActiveNotificationEnabled = false,
            liveTimerNotificationEnabled = false,
            privacyDisclosureAccepted = false,
        )
    }
}

class FakeLocalDataResetter : LocalDataResetter {
    var deleteCount = 0

    override suspend fun deleteAllLocalData() {
        deleteCount += 1
    }
}
