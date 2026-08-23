package com.robertochavez.timetracker.feature.home

import com.robertochavez.timetracker.core.common.model.HomeLocation
import com.robertochavez.timetracker.core.common.model.WorkLocation
import com.robertochavez.timetracker.core.designsystem.TimeTrackerTestTags
import com.robertochavez.timetracker.core.location.AddressGeocoder
import com.robertochavez.timetracker.core.location.CurrentGeofenceLocationProvider
import com.robertochavez.timetracker.core.location.GeocodedAddress
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
    fun `places exposes one streamlined add flow with stable automation controls`() {
        val fields = TimeTrackerTestTags::class.java.fields.map { it.name }.toSet()

        assertTrue("Saved places needs a stable list tag", "PLACES_SAVED_LIST" in fields)
        assertTrue("Places needs one add button", "ADD_PLACE_BUTTON" in fields)
        assertTrue("Add flow needs current-location control", "ADD_PLACE_CURRENT_BUTTON" in fields)
        assertTrue("Add flow needs map control", "ADD_PLACE_MAP_BUTTON" in fields)
        assertTrue("Add flow needs address control", "ADD_PLACE_ADDRESS_BUTTON" in fields)
        assertTrue("Home edit needs current-location control", "HOME_EDIT_CURRENT_BUTTON" in fields)
        assertTrue("Home edit needs map control", "HOME_EDIT_MAP_BUTTON" in fields)
        assertTrue("Home edit needs address control", "HOME_EDIT_ADDRESS_BUTTON" in fields)
        assertTrue("Home address needs a stable field", "HOME_ADDRESS_FIELD" in fields)
        assertTrue("Home map needs a stable picker tag", "HOME_MAP_PICKER" in fields)
    }

    @Test
    fun `map pin remains a draft until explicit worksite save`() = runTest(mainDispatcherRule.testDispatcher) {
        val workRepository = FakeWorkLocationRepository()
        val geocoder = StaticAddressGeocoder(reverseResult = "Map Address")
        val viewModel = HomeViewModel(
            homeLocationRepository = FakeHomeLocationRepository(),
            workLocationRepository = workRepository,
            currentGeofenceLocationProvider = StaticCurrentGeofenceLocationProvider(),
            addressGeocoder = geocoder,
            homeGeofenceRegistrar = RecordingHomeGeofenceRegistrar(),
            workGeofenceRegistrar = RecordingWorkGeofenceRegistrar(),
            clock = clock,
            logger = NoopAppLogger(),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        viewModel.selectWorkMapPin(39.7, -104.9, 5 * WorkLocation.MINIMUM_RADIUS_METERS)
        advanceUntilIdle()

        assertTrue(workRepository.workLocations.value.isEmpty())
        assertEquals("39.7", viewModel.uiState.value.workLatitude)
        assertEquals("Map Address", viewModel.uiState.value.workAddress)

        viewModel.saveWorkPin()
        advanceUntilIdle()

        assertEquals(1, workRepository.workLocations.value.size)
        assertEquals(5 * WorkLocation.MINIMUM_RADIUS_METERS, workRepository.workLocations.value.single().radiusMeters)
    }

    @Test
    fun `opening the map centers its draft on current location without saving a place`() = runTest(mainDispatcherRule.testDispatcher) {
        val current = WorkLocation(
            latitude = 40.7128,
            longitude = -74.0060,
            radiusMeters = WorkLocation.MINIMUM_RADIUS_METERS,
            updatedAt = Instant.parse("2026-05-04T12:00:00Z"),
        )
        val workRepository = FakeWorkLocationRepository()
        val viewModel = HomeViewModel(
            homeLocationRepository = FakeHomeLocationRepository(),
            workLocationRepository = workRepository,
            currentGeofenceLocationProvider = StaticCurrentGeofenceLocationProvider(workLocation = current),
            addressGeocoder = StaticAddressGeocoder(),
            homeGeofenceRegistrar = RecordingHomeGeofenceRegistrar(),
            workGeofenceRegistrar = RecordingWorkGeofenceRegistrar(),
            clock = clock,
            logger = NoopAppLogger(),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        var mapCenter: Pair<Double?, Double?>? = null
        viewModel.prepareWorkMapFromCurrentLocation { latitude, longitude ->
            mapCenter = latitude to longitude
        }
        advanceUntilIdle()

        assertEquals(40.7128 to -74.0060, mapCenter)
        assertEquals("40.7128", viewModel.uiState.value.workLatitude)
        assertEquals("-74.006", viewModel.uiState.value.workLongitude)
        assertTrue(workRepository.workLocations.value.isEmpty())
    }

    @Test
    fun `map references label current home and every saved worksite without changing the draft`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val home = HomeLocation(
                latitude = 39.7392,
                longitude = -104.9903,
                radiusMeters = HomeLocation.MINIMUM_RADIUS_METERS,
                updatedAt = Instant.parse("2026-05-04T12:00:00Z"),
            )
            val office = WorkLocation(
                id = "office",
                label = "Office",
                latitude = 39.7292,
                longitude = -104.9803,
                radiusMeters = WorkLocation.MINIMUM_RADIUS_METERS,
                updatedAt = Instant.parse("2026-05-03T12:00:00Z"),
            )
            val current = WorkLocation(
                latitude = 39.7492,
                longitude = -104.9703,
                radiusMeters = WorkLocation.MINIMUM_RADIUS_METERS,
                updatedAt = Instant.parse("2026-05-04T12:00:00Z"),
            )
            val workRepository = FakeWorkLocationRepository(office)
            val viewModel = HomeViewModel(
                homeLocationRepository = FakeHomeLocationRepository(initialHomeLocation = home),
                workLocationRepository = workRepository,
                currentGeofenceLocationProvider = StaticCurrentGeofenceLocationProvider(workLocation = current),
                addressGeocoder = StaticAddressGeocoder(),
                homeGeofenceRegistrar = RecordingHomeGeofenceRegistrar(),
                workGeofenceRegistrar = RecordingWorkGeofenceRegistrar(),
                clock = clock,
                logger = NoopAppLogger(),
            )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }
            advanceUntilIdle()

            viewModel.prepareWorkMapFromCurrentLocation { _, _ -> }
            advanceUntilIdle()

            assertEquals(
                listOf("Current location", "Home", "Office"),
                viewModel.uiState.value.mapReferenceLocations.map { it.label },
            )
            assertEquals(office, workRepository.workLocations.value.single())
            assertEquals("39.7492", viewModel.uiState.value.workLatitude)
            assertEquals("-104.9703", viewModel.uiState.value.workLongitude)
        }

    @Test
    fun `opening the home map centers its draft on current location without saving home`() = runTest(mainDispatcherRule.testDispatcher) {
        val current = HomeLocation(
            latitude = 39.7492,
            longitude = -104.9703,
            radiusMeters = 150f,
            updatedAt = Instant.parse("2026-05-04T12:00:00Z"),
        )
        val homeRepository = FakeHomeLocationRepository()
        val viewModel = HomeViewModel(
            homeLocationRepository = homeRepository,
            workLocationRepository = FakeWorkLocationRepository(),
            currentGeofenceLocationProvider = StaticCurrentGeofenceLocationProvider(homeLocation = current),
            addressGeocoder = StaticAddressGeocoder(),
            homeGeofenceRegistrar = RecordingHomeGeofenceRegistrar(),
            workGeofenceRegistrar = RecordingWorkGeofenceRegistrar(),
            clock = clock,
            logger = NoopAppLogger(),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        var mapCenter: Pair<Double?, Double?>? = null
        viewModel.prepareHomeMapFromCurrentLocation { latitude, longitude ->
            mapCenter = latitude to longitude
        }
        advanceUntilIdle()

        assertEquals(39.7492 to -104.9703, mapCenter)
        assertEquals("39.7492", viewModel.uiState.value.homeLatitude)
        assertEquals("-104.9703", viewModel.uiState.value.homeLongitude)
        assertEquals(null, homeRepository.homeLocation.value)
    }

    @Test
    fun `home address lookup and map pin remain drafts until explicit save`() = runTest(mainDispatcherRule.testDispatcher) {
        val original = HomeLocation(
            latitude = 39.7392,
            longitude = -104.9903,
            radiusMeters = 15.24f,
            updatedAt = Instant.parse("2026-05-03T12:00:00Z"),
        )
        val homeRepository = FakeHomeLocationRepository(initialHomeLocation = original)
        val viewModel = HomeViewModel(
            homeLocationRepository = homeRepository,
            workLocationRepository = FakeWorkLocationRepository(),
            currentGeofenceLocationProvider = StaticCurrentGeofenceLocationProvider(),
            addressGeocoder = StaticAddressGeocoder(
                GeocodedAddress(
                    latitude = 39.7292,
                    longitude = -104.9803,
                    displayAddress = "123 Home Way, Denver, CO",
                ),
                reverseResult = "456 Map Way, Denver, CO",
            ),
            homeGeofenceRegistrar = RecordingHomeGeofenceRegistrar(),
            workGeofenceRegistrar = RecordingWorkGeofenceRegistrar(),
            clock = clock,
            logger = NoopAppLogger(),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.updateHomeField(LocationField.ADDRESS, "123 Home Way")
        viewModel.resolveHomeAddress()
        advanceUntilIdle()

        assertEquals(original, homeRepository.homeLocation.value)
        assertEquals("123 Home Way, Denver, CO", viewModel.uiState.value.homeResolvedAddress)
        assertEquals("39.7292", viewModel.uiState.value.homeLatitude)

        viewModel.selectHomeMapPin(39.7192, -104.9603, 402.336f)
        advanceUntilIdle()

        assertEquals(original, homeRepository.homeLocation.value)
        assertEquals("456 Map Way, Denver, CO", viewModel.uiState.value.homeResolvedAddress)
        assertEquals("39.7192", viewModel.uiState.value.homeLatitude)
        assertEquals("402.336", viewModel.uiState.value.homeRadiusMeters)

        viewModel.cancelHomeEdit()
        advanceUntilIdle()

        assertEquals(original, homeRepository.homeLocation.value)
        assertEquals("", viewModel.uiState.value.homeAddress)
        assertEquals("", viewModel.uiState.value.homeResolvedAddress)
        assertEquals("39.7392", viewModel.uiState.value.homeLatitude)
        assertEquals("-104.9903", viewModel.uiState.value.homeLongitude)
        assertEquals("15.24", viewModel.uiState.value.homeRadiusMeters)
    }

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
            addressGeocoder = StaticAddressGeocoder(),
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
            addressGeocoder = StaticAddressGeocoder(),
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
            addressGeocoder = StaticAddressGeocoder(),
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
            addressGeocoder = StaticAddressGeocoder(),
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
            radiusMeters = WorkLocation.MINIMUM_RADIUS_METERS,
            updatedAt = Instant.parse("2026-05-04T12:00:00Z"),
        )
        val workRepository = FakeWorkLocationRepository()
        val workGeofenceRegistrar = RecordingWorkGeofenceRegistrar()
        val viewModel = HomeViewModel(
            homeLocationRepository = FakeHomeLocationRepository(),
            workLocationRepository = workRepository,
            currentGeofenceLocationProvider = StaticCurrentGeofenceLocationProvider(workLocation = workLocation),
            addressGeocoder = StaticAddressGeocoder(),
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
            addressGeocoder = StaticAddressGeocoder(),
            homeGeofenceRegistrar = RecordingHomeGeofenceRegistrar(),
            workGeofenceRegistrar = RecordingWorkGeofenceRegistrar(),
            clock = clock,
            logger = NoopAppLogger(),
        )

        viewModel.updateWorkField(LocationField.LABEL, "North Yard")
        viewModel.updateWorkField(LocationField.LATITUDE, "39.7292")
        viewModel.updateWorkField(LocationField.LONGITUDE, "-104.9803")
        viewModel.updateWorkField(LocationField.RADIUS_METERS, WorkLocation.MINIMUM_RADIUS_METERS.toString())
        viewModel.saveWorkPin()
        advanceUntilIdle()

        assertEquals("North Yard", workRepository.workLocation.value?.label)
    }

    @Test
    fun `adding a worksite appends a distinct saved place instead of replacing the existing one`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val existing = WorkLocation(
                id = "office",
                label = "Office",
                latitude = 39.7292,
                longitude = -104.9803,
                radiusMeters = WorkLocation.MINIMUM_RADIUS_METERS,
                updatedAt = Instant.parse("2026-05-03T12:00:00Z"),
            )
            val workRepository = FakeWorkLocationRepository(existing)
            val viewModel = HomeViewModel(
                homeLocationRepository = FakeHomeLocationRepository(),
                workLocationRepository = workRepository,
                currentGeofenceLocationProvider = StaticCurrentGeofenceLocationProvider(),
                addressGeocoder = StaticAddressGeocoder(),
                homeGeofenceRegistrar = RecordingHomeGeofenceRegistrar(),
                workGeofenceRegistrar = RecordingWorkGeofenceRegistrar(),
                clock = clock,
                logger = NoopAppLogger(),
            )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }
            advanceUntilIdle()

            viewModel.updateWorkField(LocationField.LABEL, "North Yard")
            viewModel.updateWorkField(LocationField.LATITUDE, "39.8")
            viewModel.updateWorkField(LocationField.LONGITUDE, "-104.8")
            viewModel.saveWorkPin(replaceLatest = false)
            advanceUntilIdle()

            assertEquals(2, workRepository.workLocations.value.size)
            assertEquals(existing, workRepository.workLocations.value.first { it.id == existing.id })
            assertEquals(setOf("Office", "North Yard"), viewModel.uiState.value.workLocations.map { it.label }.toSet())
        }

    @Test
    fun `starting add place never reuses the existing place draft`() = runTest(mainDispatcherRule.testDispatcher) {
        val existing = WorkLocation(
            id = "office",
            label = "Office",
            latitude = 39.7292,
            longitude = -104.9803,
            address = "100 Existing Way",
            radiusMeters = 5 * WorkLocation.MINIMUM_RADIUS_METERS,
            updatedAt = Instant.parse("2026-05-03T12:00:00Z"),
        )
        val viewModel = HomeViewModel(
            homeLocationRepository = FakeHomeLocationRepository(),
            workLocationRepository = FakeWorkLocationRepository(existing),
            currentGeofenceLocationProvider = StaticCurrentGeofenceLocationProvider(),
            addressGeocoder = StaticAddressGeocoder(),
            homeGeofenceRegistrar = RecordingHomeGeofenceRegistrar(),
            workGeofenceRegistrar = RecordingWorkGeofenceRegistrar(),
            clock = clock,
            logger = NoopAppLogger(),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        assertEquals("Office", viewModel.uiState.value.workLabel)
        viewModel.beginAddPlace()
        advanceUntilIdle()

        assertEquals(WorkLocation.DEFAULT_LABEL, viewModel.uiState.value.workLabel)
        assertEquals("", viewModel.uiState.value.workLatitude)
        assertEquals("", viewModel.uiState.value.workLongitude)
        assertEquals("", viewModel.uiState.value.workAddress)
        assertEquals(WorkLocation.MINIMUM_RADIUS_METERS.toString(), viewModel.uiState.value.workRadiusMeters)
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
            addressGeocoder = StaticAddressGeocoder(),
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
            addressGeocoder = StaticAddressGeocoder(),
            homeGeofenceRegistrar = RecordingHomeGeofenceRegistrar(),
            workGeofenceRegistrar = workGeofenceRegistrar,
            clock = clock,
            logger = NoopAppLogger(),
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()
        viewModel.updateWorkRadius(8_046.72f)
        viewModel.saveWorkPin(replaceLatest = true)
        advanceUntilIdle()

        val savedWork = workRepository.workLocations.value.single()
        assertEquals("existing-work", savedWork.id)
        assertEquals("Existing work", savedWork.label)
        assertEquals(8_046.72f, savedWork.radiusMeters, 0.001f)
        assertEquals(listOf(savedWork), workGeofenceRegistrar.registeredWorkLocationBatches.last())
    }

    @Test
    fun `home is saved while surfacing geofence permission warnings`() = runTest(mainDispatcherRule.testDispatcher) {
        val homeRepository = FakeHomeLocationRepository()
        val viewModel = HomeViewModel(
            homeLocationRepository = homeRepository,
            workLocationRepository = FakeWorkLocationRepository(),
            currentGeofenceLocationProvider = StaticCurrentGeofenceLocationProvider(),
            addressGeocoder = StaticAddressGeocoder(),
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

    @Test
    fun `address lookup fills work coordinates and saves resolved address`() = runTest(mainDispatcherRule.testDispatcher) {
        val workRepository = FakeWorkLocationRepository()
        val viewModel = HomeViewModel(
            homeLocationRepository = FakeHomeLocationRepository(),
            workLocationRepository = workRepository,
            currentGeofenceLocationProvider = StaticCurrentGeofenceLocationProvider(),
            addressGeocoder = StaticAddressGeocoder(
                GeocodedAddress(
                    latitude = 39.7292,
                    longitude = -104.9803,
                    displayAddress = "123 Office Way, Denver, CO",
                ),
            ),
            homeGeofenceRegistrar = RecordingHomeGeofenceRegistrar(),
            workGeofenceRegistrar = RecordingWorkGeofenceRegistrar(),
            clock = clock,
            logger = NoopAppLogger(),
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        viewModel.updateWorkField(LocationField.LABEL, "Office")
        viewModel.updateWorkField(LocationField.ADDRESS, "123 Office Way")
        viewModel.resolveWorkAddress()
        advanceUntilIdle()
        viewModel.saveWorkPin()
        advanceUntilIdle()

        val saved = workRepository.workLocation.value
        assertEquals("Office", saved?.label)
        assertEquals("123 Office Way, Denver, CO", saved?.address)
        assertEquals(39.7292, saved?.latitude ?: 0.0, 0.0001)
        assertEquals(-104.9803, saved?.longitude ?: 0.0, 0.0001)
    }

    @Test
    fun `address lookup keeps typed address when no result is found`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = HomeViewModel(
            homeLocationRepository = FakeHomeLocationRepository(),
            workLocationRepository = FakeWorkLocationRepository(),
            currentGeofenceLocationProvider = StaticCurrentGeofenceLocationProvider(),
            addressGeocoder = StaticAddressGeocoder(),
            homeGeofenceRegistrar = RecordingHomeGeofenceRegistrar(),
            workGeofenceRegistrar = RecordingWorkGeofenceRegistrar(),
            clock = clock,
            logger = NoopAppLogger(),
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        viewModel.updateWorkField(LocationField.ADDRESS, "unknown jobsite")
        viewModel.resolveWorkAddress()
        advanceUntilIdle()

        assertEquals("No address match found.", viewModel.uiState.value.statusMessage)
        assertEquals("unknown jobsite", viewModel.uiState.value.workAddress)
        assertEquals("", viewModel.uiState.value.workLatitude)
        assertEquals("", viewModel.uiState.value.workLongitude)
    }

    @Test
    fun `address lookup surfaces geocoder failure`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = HomeViewModel(
            homeLocationRepository = FakeHomeLocationRepository(),
            workLocationRepository = FakeWorkLocationRepository(),
            currentGeofenceLocationProvider = StaticCurrentGeofenceLocationProvider(),
            addressGeocoder = StaticAddressGeocoder(failure = IllegalStateException("Geocoder unavailable")),
            homeGeofenceRegistrar = RecordingHomeGeofenceRegistrar(),
            workGeofenceRegistrar = RecordingWorkGeofenceRegistrar(),
            clock = clock,
            logger = NoopAppLogger(),
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        viewModel.updateWorkField(LocationField.ADDRESS, "123 Office Way")
        viewModel.resolveWorkAddress()
        advanceUntilIdle()

        assertEquals("Geocoder unavailable", viewModel.uiState.value.statusMessage)
        assertEquals("123 Office Way", viewModel.uiState.value.workAddress)
        assertEquals("", viewModel.uiState.value.workResolvedAddress)
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

private class StaticAddressGeocoder(
    private val result: GeocodedAddress? = null,
    private val failure: Throwable? = null,
    private val reverseResult: String? = null,
) : AddressGeocoder {
    val queries = mutableListOf<String>()

    override suspend fun findAddress(query: String): GeocodedAddress? {
        queries += query
        failure?.let { throw it }
        return result
    }

    override suspend fun reverseGeocode(latitude: Double, longitude: Double): String? {
        failure?.let { throw it }
        return reverseResult
    }
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
