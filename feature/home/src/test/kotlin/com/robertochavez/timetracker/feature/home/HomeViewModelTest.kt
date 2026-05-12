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
@Suppress("LargeClass")
class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-05-04T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `saved home location hydrates pin editor fields`() = runTest(mainDispatcherRule.testDispatcher) {
        val homeLocation = HomeLocation(
            latitude = 39.7392,
            longitude = -104.9903,
            radiusMeters = 150f,
            updatedAt = Instant.parse("2026-05-04T12:00:00Z"),
        )
        val viewModel = HomeViewModel(
            homeLocationRepository = FakeHomeLocationRepository(initialHomeLocation = homeLocation),
            workLocationRepository = FakeWorkLocationRepository(),
            currentGeofenceLocationProvider = StaticCurrentGeofenceLocationProvider(),
            homeGeofenceRegistrar = RecordingHomeGeofenceRegistrar(),
            workGeofenceRegistrar = RecordingWorkGeofenceRegistrar(),
            clock = clock,
            logger = NoopAppLogger(),
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        assertEquals("39.7392", viewModel.uiState.value.homeLatitude)
        assertEquals("-104.9903", viewModel.uiState.value.homeLongitude)
        assertEquals("15.24", viewModel.uiState.value.homeRadiusMeters)
    }

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
    fun `current home location clamps invalid radius input before requesting location`() = runTest(mainDispatcherRule.testDispatcher) {
        val homeLocation = HomeLocation(
            latitude = 39.7392,
            longitude = -104.9903,
            radiusMeters = 100f,
            updatedAt = Instant.parse("2026-05-04T12:00:00Z"),
        )
        val currentLocationProvider = StaticCurrentGeofenceLocationProvider(homeLocation = homeLocation)
        val viewModel = HomeViewModel(
            homeLocationRepository = FakeHomeLocationRepository(),
            workLocationRepository = FakeWorkLocationRepository(),
            currentGeofenceLocationProvider = currentLocationProvider,
            homeGeofenceRegistrar = RecordingHomeGeofenceRegistrar(),
            workGeofenceRegistrar = RecordingWorkGeofenceRegistrar(),
            clock = clock,
            logger = NoopAppLogger(),
        )

        viewModel.updateHomeField(LocationField.RADIUS_METERS, "0")
        viewModel.useCurrentHomeLocation()
        advanceUntilIdle()

        assertEquals(listOf(HomeLocation.MINIMUM_RADIUS_METERS), currentLocationProvider.homeRadiusRequests)
    }

    @Test
    fun `saved home radius can be changed without editing coordinates`() = runTest(mainDispatcherRule.testDispatcher) {
        val homeRepository = FakeHomeLocationRepository(
            HomeLocation(
                latitude = 39.7392,
                longitude = -104.9903,
                radiusMeters = HomeLocation.MINIMUM_RADIUS_METERS,
                updatedAt = Instant.parse("2026-05-04T12:00:00Z"),
            ),
        )
        val geofenceRegistrar = RecordingHomeGeofenceRegistrar()
        val viewModel = HomeViewModel(
            homeLocationRepository = homeRepository,
            workLocationRepository = FakeWorkLocationRepository(),
            currentGeofenceLocationProvider = StaticCurrentGeofenceLocationProvider(),
            homeGeofenceRegistrar = geofenceRegistrar,
            workGeofenceRegistrar = RecordingWorkGeofenceRegistrar(),
            clock = clock,
            logger = NoopAppLogger(),
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()
        viewModel.updateHomeRadius(402.336f)
        viewModel.saveHomePin()
        advanceUntilIdle()

        assertEquals(402.336f, homeRepository.homeLocation.value?.radiusMeters ?: 0f, 0.001f)
        assertEquals(402.336f, geofenceRegistrar.registeredHomes.last().radiusMeters, 0.001f)
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
    fun `manual work site save uses custom name`() = runTest(mainDispatcherRule.testDispatcher) {
        val workRepository = FakeWorkLocationRepository()
        val viewModel = HomeViewModel(
            homeLocationRepository = FakeHomeLocationRepository(),
            workLocationRepository = workRepository,
            currentGeofenceLocationProvider = StaticCurrentGeofenceLocationProvider(),
            homeGeofenceRegistrar = RecordingHomeGeofenceRegistrar(),
            workGeofenceRegistrar = RecordingWorkGeofenceRegistrar(),
            clock = clock,
            logger = NoopAppLogger(),
        )

        viewModel.updateWorkField(LocationField.LABEL, "North Yard")
        viewModel.updateWorkField(LocationField.LATITUDE, "39.7292")
        viewModel.updateWorkField(LocationField.LONGITUDE, "-104.9803")
        viewModel.updateWorkField(LocationField.RADIUS_METERS, "150")
        viewModel.saveWorkPin()
        advanceUntilIdle()

        assertEquals("North Yard", workRepository.workLocation.value?.label)
    }

    @Test
    fun `latest work site can be renamed while saving changes`() = runTest(mainDispatcherRule.testDispatcher) {
        val workRepository = FakeWorkLocationRepository(
            WorkLocation(
                id = "existing-work",
                label = "Existing work",
                latitude = 39.7292,
                longitude = -104.9803,
                radiusMeters = WorkLocation.MINIMUM_RADIUS_METERS,
                updatedAt = Instant.parse("2026-05-04T12:00:00Z"),
            ),
        )
        val viewModel = HomeViewModel(
            homeLocationRepository = FakeHomeLocationRepository(),
            workLocationRepository = workRepository,
            currentGeofenceLocationProvider = StaticCurrentGeofenceLocationProvider(),
            homeGeofenceRegistrar = RecordingHomeGeofenceRegistrar(),
            workGeofenceRegistrar = RecordingWorkGeofenceRegistrar(),
            clock = clock,
            logger = NoopAppLogger(),
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()
        viewModel.updateWorkField(LocationField.LABEL, "Renamed work")
        viewModel.saveWorkPin(replaceLatest = true)
        advanceUntilIdle()

        val savedWork = workRepository.workLocations.value.single()
        assertEquals("existing-work", savedWork.id)
        assertEquals("Renamed work", savedWork.label)
    }

    @Test
    fun `saved latest work radius can be changed without adding another site`() = runTest(mainDispatcherRule.testDispatcher) {
        val workRepository = FakeWorkLocationRepository(
            WorkLocation(
                id = "existing-work",
                label = "Existing work",
                latitude = 39.7292,
                longitude = -104.9803,
                radiusMeters = WorkLocation.MINIMUM_RADIUS_METERS,
                updatedAt = Instant.parse("2026-05-04T12:00:00Z"),
            ),
        )
        val workGeofenceRegistrar = RecordingWorkGeofenceRegistrar()
        val viewModel = HomeViewModel(
            homeLocationRepository = FakeHomeLocationRepository(),
            workLocationRepository = workRepository,
            currentGeofenceLocationProvider = StaticCurrentGeofenceLocationProvider(),
            homeGeofenceRegistrar = RecordingHomeGeofenceRegistrar(),
            workGeofenceRegistrar = workGeofenceRegistrar,
            clock = clock,
            logger = NoopAppLogger(),
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()
        viewModel.updateWorkRadius(804.672f)
        viewModel.saveWorkPin(replaceLatest = true)
        advanceUntilIdle()

        val savedWork = workRepository.workLocations.value.single()
        assertEquals("existing-work", savedWork.id)
        assertEquals("Existing work", savedWork.label)
        assertEquals(804.672f, savedWork.radiusMeters, 0.001f)
        assertEquals(listOf(savedWork), workGeofenceRegistrar.registeredWorkLocationBatches.last())
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
    val homeRadiusRequests = mutableListOf<Float>()

    override suspend fun currentPreciseHomeLocation(radiusMeters: Float): HomeLocation? {
        homeRadiusRequests += radiusMeters
        return homeLocation
    }

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
    val registeredWorkLocationBatches = mutableListOf<List<WorkLocation>>()

    override suspend fun registerWorkGeofence(workLocation: WorkLocation) {
        registeredWorkLocations += workLocation
    }

    override suspend fun registerWorkGeofences(workLocations: List<WorkLocation>) {
        registeredWorkLocationBatches += workLocations
        registeredWorkLocations += workLocations
    }

    override suspend fun unregisterWorkGeofence() = Unit

    override suspend fun unregisterWorkGeofences(workLocations: List<WorkLocation>) = Unit
}

private class FailingHomeGeofenceRegistrar(private val message: String) : HomeGeofenceRegistrar {
    override suspend fun registerHomeGeofence(homeLocation: HomeLocation) {
        error(message)
    }

    override suspend fun unregisterHomeGeofence() = Unit
}
