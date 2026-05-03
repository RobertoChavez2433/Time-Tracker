package com.robertochavez.timetracker.feature.home

import com.robertochavez.timetracker.core.common.model.HomeLocation
import com.robertochavez.timetracker.core.common.model.WorkLocation
import com.robertochavez.timetracker.core.location.CurrentGeofenceLocationProvider
import com.robertochavez.timetracker.core.location.geofence.HomeGeofenceRegistrar
import com.robertochavez.timetracker.core.location.geofence.WorkGeofenceRegistrar
import com.robertochavez.timetracker.core.logging.NoopAppLogger
import com.robertochavez.timetracker.core.testing.FakeHomeLocationRepository
import com.robertochavez.timetracker.core.testing.FakeWorkLocationRepository
import com.robertochavez.timetracker.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-05-04T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `current location saves home and registers geofence`() = runTest(mainDispatcherRule.testDispatcher) {
        val homeLocation = HomeLocation(
            latitude = 39.7392,
            longitude = -104.9903,
            radiusMeters = 150f,
            updatedAt = Instant.parse("2026-05-04T12:00:00Z"),
        )
        val homeRepository = FakeHomeLocationRepository()
        val workRepository = FakeWorkLocationRepository()
        val geofenceRegistrar = RecordingHomeGeofenceRegistrar()
        val viewModel = HomeViewModel(
            homeLocationRepository = homeRepository,
            workLocationRepository = workRepository,
            currentGeofenceLocationProvider = StaticCurrentGeofenceLocationProvider(homeLocation = homeLocation),
            homeGeofenceRegistrar = geofenceRegistrar,
            workGeofenceRegistrar = RecordingWorkGeofenceRegistrar(),
            clock = clock,
            logger = NoopAppLogger(),
        )

        viewModel.useCurrentHomeLocation()
        advanceUntilIdle()

        assertEquals(homeLocation, homeRepository.homeLocation.value)
        assertEquals(listOf(homeLocation), geofenceRegistrar.registeredHomes)
    }

    @Test
    fun `current location saves work location`() = runTest(mainDispatcherRule.testDispatcher) {
        val workLocation = WorkLocation(
            latitude = 39.7292,
            longitude = -104.9803,
            radiusMeters = 500f,
            updatedAt = Instant.parse("2026-05-04T12:00:00Z"),
        )
        val workRepository = FakeWorkLocationRepository()
        val workGeofenceRegistrar = RecordingWorkGeofenceRegistrar()
        val viewModel = HomeViewModel(
            homeLocationRepository = FakeHomeLocationRepository(),
            workLocationRepository = workRepository,
            currentGeofenceLocationProvider = StaticCurrentGeofenceLocationProvider(workLocation = workLocation),
            homeGeofenceRegistrar = RecordingHomeGeofenceRegistrar(),
            workGeofenceRegistrar = workGeofenceRegistrar,
            clock = clock,
            logger = NoopAppLogger(),
        )

        viewModel.useCurrentWorkLocation()
        advanceUntilIdle()

        assertEquals(workLocation, workRepository.workLocation.value)
        assertEquals(listOf(workLocation), workGeofenceRegistrar.registeredWorkLocations)
    }

    @Test
    fun `home is saved while surfacing geofence permission warnings`() = runTest(mainDispatcherRule.testDispatcher) {
        val homeRepository = FakeHomeLocationRepository()
        val viewModel = HomeViewModel(
            homeLocationRepository = homeRepository,
            workLocationRepository = FakeWorkLocationRepository(),
            currentGeofenceLocationProvider = StaticCurrentGeofenceLocationProvider(),
            homeGeofenceRegistrar = FailingHomeGeofenceRegistrar("Precise location is required."),
            workGeofenceRegistrar = RecordingWorkGeofenceRegistrar(),
            clock = clock,
            logger = NoopAppLogger(),
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        viewModel.updateHomeField(LocationField.LATITUDE, "39.7392")
        viewModel.updateHomeField(LocationField.LONGITUDE, "-104.9903")
        viewModel.updateHomeField(LocationField.RADIUS_METERS, "150")
        viewModel.saveHomePin()
        advanceUntilIdle()

        assertEquals(39.7392, homeRepository.homeLocation.value?.latitude ?: 0.0, 0.0001)
        assertTrue(viewModel.uiState.value.statusMessage.contains("Precise location is required."))
    }
}

private class StaticCurrentGeofenceLocationProvider(
    private val homeLocation: HomeLocation? = null,
    private val workLocation: WorkLocation? = null,
) : CurrentGeofenceLocationProvider {
    override suspend fun currentPreciseHomeLocation(radiusMeters: Float): HomeLocation? = homeLocation

    override suspend fun currentPreciseWorkLocation(radiusMeters: Float): WorkLocation? = workLocation
}

private class RecordingHomeGeofenceRegistrar : HomeGeofenceRegistrar {
    val registeredHomes = mutableListOf<HomeLocation>()

    override suspend fun registerHomeGeofence(homeLocation: HomeLocation) {
        registeredHomes += homeLocation
    }

    override suspend fun unregisterHomeGeofence() = Unit
}

private class RecordingWorkGeofenceRegistrar : WorkGeofenceRegistrar {
    val registeredWorkLocations = mutableListOf<WorkLocation>()

    override suspend fun registerWorkGeofence(workLocation: WorkLocation) {
        registeredWorkLocations += workLocation
    }

    override suspend fun unregisterWorkGeofence() = Unit
}

private class FailingHomeGeofenceRegistrar(private val message: String) : HomeGeofenceRegistrar {
    override suspend fun registerHomeGeofence(homeLocation: HomeLocation) {
        error(message)
    }

    override suspend fun unregisterHomeGeofence() = Unit
}
