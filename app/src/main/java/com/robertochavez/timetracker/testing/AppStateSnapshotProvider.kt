package com.robertochavez.timetracker.testing

import com.robertochavez.timetracker.core.common.repository.TrackingRepository
import com.robertochavez.timetracker.core.logging.AppLogger
import kotlinx.coroutines.flow.first
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppStateSnapshotProvider @Inject constructor(
    private val setupSources: SetupSnapshotSources,
    private val trackingRepository: TrackingRepository,
    private val logger: AppLogger,
    private val clock: Clock,
) {
    suspend fun build(runId: String, actorId: String): AppStateSnapshot {
        val home = setupSources.homeLocationRepository.getHomeLocation()
        val work = setupSources.workLocationRepository.getWorkLocation()
        val workPresence = setupSources.workPresenceRepository.getWorkPresence()
        val sessions = trackingRepository.observeSessions().first()
        val intervals = trackingRepository.observeActivityIntervals().first()
        val schedule = setupSources.workScheduleRepository.getWorkSchedule()
        val settings = setupSources.appSettingsRepository.settings.first()
        val today = clock.instant().atZone(clock.zone).toLocalDate()
        val recentLogs = logger.recentEvents(limit = 50)

        return AppStateSnapshot(
            runId = runId,
            actorId = actorId,
            capturedAtEpochMillis = clock.millis(),
            homeSet = home != null,
            homeRadiusMeters = home?.radiusMeters,
            workSet = work != null,
            workRadiusMeters = work?.radiusMeters,
            atWork = workPresence.atWork,
            activeSession = sessions.firstOrNull { it.isActive }?.toSummary(),
            sessionCount = sessions.size,
            activityIntervalCount = intervals.size,
            trackableToday = schedule.isTrackable(today),
            workdayCount = schedule.trackableDays.size,
            privacyDisclosureAccepted = settings.privacyDisclosureAccepted,
            minimalActiveNotificationEnabled = settings.minimalActiveNotificationEnabled,
            liveTimerNotificationEnabled = settings.liveTimerNotificationEnabled,
            recentLogCount = recentLogs.size,
        )
    }
}
