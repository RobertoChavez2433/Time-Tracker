package com.robertochavez.timetracker.core.location

import com.robertochavez.timetracker.core.common.model.AppSettings
import com.robertochavez.timetracker.core.common.model.HomeLocation
import com.robertochavez.timetracker.core.common.model.WorkLocation
import com.robertochavez.timetracker.core.location.activity.ActivityTransitionRegistrar
import com.robertochavez.timetracker.core.location.geofence.HomeGeofenceRegistrar
import com.robertochavez.timetracker.core.location.geofence.WorkGeofenceRegistrar
import com.robertochavez.timetracker.core.logging.NoopAppLogger
import com.robertochavez.timetracker.core.testing.FakeAppSettingsRepository
import com.robertochavez.timetracker.core.testing.FakeHomeLocationRepository
import com.robertochavez.timetracker.core.testing.FakeWorkLocationRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class TrackingRegistrationSynchronizerTest {
    @Test
    fun `sync registers saved geofences and enabled activity transitions`() = runTest {
        val home = homeLocation()
        val work = workLocation()
        val homeRegistrar = RecordingHomeGeofenceRegistrar()
        val workRegistrar = RecordingWorkGeofenceRegistrar()
        val activityRegistrar = RecordingActivityTransitionRegistrar()
        val synchronizer = synchronizer(
            home = home,
            work = work,
            activityEnabled = true,
            homeRegistrar = homeRegistrar,
            workRegistrar = workRegistrar,
            activityRegistrar = activityRegistrar,
        )

        synchronizer.synchronize("test")

        assertEquals(listOf(home), homeRegistrar.registeredHomes)
        assertEquals(listOf(listOf(work)), workRegistrar.registeredWorkLocationBatches)
        assertEquals(1, activityRegistrar.registerCount)
    }

    @Test
    fun `sync leaves activity transitions off when setting is disabled`() = runTest {
        val activityRegistrar = RecordingActivityTransitionRegistrar()
        val synchronizer = synchronizer(
            home = null,
            work = null,
            activityEnabled = false,
            activityRegistrar = activityRegistrar,
        )

        synchronizer.synchronize("test")

        assertEquals(0, activityRegistrar.registerCount)
    }

    private fun synchronizer(
        home: HomeLocation?,
        work: WorkLocation?,
        activityEnabled: Boolean,
        homeRegistrar: RecordingHomeGeofenceRegistrar = RecordingHomeGeofenceRegistrar(),
        workRegistrar: RecordingWorkGeofenceRegistrar = RecordingWorkGeofenceRegistrar(),
        activityRegistrar: RecordingActivityTransitionRegistrar = RecordingActivityTransitionRegistrar(),
    ): TrackingRegistrationSynchronizer = PlayServicesTrackingRegistrationSynchronizer(
        homeLocationRepository = FakeHomeLocationRepository(home),
        workLocationRepository = FakeWorkLocationRepository(work),
        appSettingsRepository = FakeAppSettingsRepository(
            AppSettings(
                minimalActiveNotificationEnabled = false,
                liveTimerNotificationEnabled = false,
                privacyDisclosureAccepted = true,
                activityDetectionEnabled = activityEnabled,
            ),
        ),
        homeGeofenceRegistrar = homeRegistrar,
        workGeofenceRegistrar = workRegistrar,
        activityTransitionRegistrar = activityRegistrar,
        logger = NoopAppLogger(),
    )
}

private class RecordingHomeGeofenceRegistrar : HomeGeofenceRegistrar {
    val registeredHomes = mutableListOf<HomeLocation>()

    override suspend fun registerHomeGeofence(homeLocation: HomeLocation) {
        registeredHomes += homeLocation
    }

    override suspend fun unregisterHomeGeofence() = Unit
}

private class RecordingWorkGeofenceRegistrar : WorkGeofenceRegistrar {
    val registeredWorkLocationBatches = mutableListOf<List<WorkLocation>>()

    override suspend fun registerWorkGeofence(workLocation: WorkLocation) {
        registerWorkGeofences(listOf(workLocation))
    }

    override suspend fun registerWorkGeofences(workLocations: List<WorkLocation>) {
        registeredWorkLocationBatches += workLocations
    }

    override suspend fun unregisterWorkGeofence() = Unit

    override suspend fun unregisterWorkGeofences(workLocations: List<WorkLocation>) = Unit
}

private class RecordingActivityTransitionRegistrar : ActivityTransitionRegistrar {
    var registerCount = 0

    override suspend fun registerDriveAndIdleTransitions() {
        registerCount += 1
    }

    override suspend fun unregisterDriveAndIdleTransitions() = Unit
}

private fun homeLocation(): HomeLocation = HomeLocation(
    latitude = 42.3314,
    longitude = -83.0458,
    radiusMeters = 150f,
    updatedAt = Instant.parse("2026-05-04T12:00:00Z"),
)

private fun workLocation(): WorkLocation = WorkLocation(
    id = "work",
    label = "Work",
    latitude = 42.335,
    longitude = -83.050,
    radiusMeters = 150f,
    updatedAt = Instant.parse("2026-05-04T12:00:00Z"),
)
