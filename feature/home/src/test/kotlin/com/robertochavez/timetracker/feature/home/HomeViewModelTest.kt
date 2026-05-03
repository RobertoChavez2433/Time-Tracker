package com.robertochavez.timetracker.feature.home

import com.robertochavez.timetracker.core.common.model.HomeLocation
import com.robertochavez.timetracker.core.location.CurrentHomeLocationProvider
import com.robertochavez.timetracker.core.location.geofence.HomeGeofenceRegistrar
import com.robertochavez.timetracker.core.testing.FakeHomeLocationRepository
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
        val geofenceRegistrar = RecordingHomeGeofenceRegistrar()
        val viewModel = HomeViewModel(
            homeLocationRepository = homeRepository,
            currentHomeLocationProvider = StaticCurrentHomeLocationProvider(homeLocation),
            homeGeofenceRegistrar = geofenceRegistrar,
            clock = clock,
        )

        viewModel.useCurrentLocation()
        advanceUntilIdle()

        assertEquals(homeLocation, homeRepository.homeLocation.value)
        assertEquals(listOf(homeLocation), geofenceRegistrar.registeredHomes)
    }

    @Test
    fun `home is saved while surfacing geofence permission warnings`() = runTest(mainDispatcherRule.testDispatcher) {
        val homeRepository = FakeHomeLocationRepository()
        val viewModel = HomeViewModel(
            homeLocationRepository = homeRepository,
            currentHomeLocationProvider = StaticCurrentHomeLocationProvider(null),
            homeGeofenceRegistrar = FailingHomeGeofenceRegistrar("Precise location is required."),
            clock = clock,
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        viewModel.updatePinLatitude("39.7392")
        viewModel.updatePinLongitude("-104.9903")
        viewModel.updatePinRadiusMeters("150")
        viewModel.saveMapPin()
        advanceUntilIdle()

        assertEquals(39.7392, homeRepository.homeLocation.value?.latitude ?: 0.0, 0.0001)
        assertTrue(viewModel.uiState.value.statusMessage.contains("Precise location is required."))
    }
}

private class StaticCurrentHomeLocationProvider(private val homeLocation: HomeLocation?) : CurrentHomeLocationProvider {
    override suspend fun currentPreciseHomeLocation(radiusMeters: Float): HomeLocation? = homeLocation
}

private class RecordingHomeGeofenceRegistrar : HomeGeofenceRegistrar {
    val registeredHomes = mutableListOf<HomeLocation>()

    override suspend fun registerHomeGeofence(homeLocation: HomeLocation) {
        registeredHomes += homeLocation
    }

    override suspend fun unregisterHomeGeofence() = Unit
}

private class FailingHomeGeofenceRegistrar(private val message: String) : HomeGeofenceRegistrar {
    override suspend fun registerHomeGeofence(homeLocation: HomeLocation) {
        error(message)
    }

    override suspend fun unregisterHomeGeofence() = Unit
}
