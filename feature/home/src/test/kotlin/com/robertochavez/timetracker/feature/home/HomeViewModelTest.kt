package com.robertochavez.timetracker.feature.home

import androidx.lifecycle.SavedStateHandle
import com.robertochavez.timetracker.core.common.model.GeofenceRadiusOptions
import com.robertochavez.timetracker.core.common.model.HomeLocation
import com.robertochavez.timetracker.core.common.model.WorkLocation
import com.robertochavez.timetracker.core.common.repository.WorkLocationRepository
import com.robertochavez.timetracker.core.location.AddressGeocoder
import com.robertochavez.timetracker.core.location.CurrentGeofenceLocationProvider
import com.robertochavez.timetracker.core.location.GeocodedAddress
import com.robertochavez.timetracker.core.location.geofence.HomeGeofenceRegistrar
import com.robertochavez.timetracker.core.location.geofence.WorkGeofenceRegistrar
import com.robertochavez.timetracker.core.testing.FakeHomeLocationRepository
import com.robertochavez.timetracker.core.testing.FakeWorkLocationRepository
import com.robertochavez.timetracker.core.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule val dispatchers = MainDispatcherRule()
    private val now = Instant.parse("2026-08-30T16:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val homes = FakeHomeLocationRepository()
    private val works = FakeWorkLocationRepository()
    private val registrar = RecordingRegistrar()

    @Test fun `editing a non-latest place preserves id and normalizes job number`() = runTest(dispatchers.testDispatcher) {
        val first = site("first", "FIRST")
        works.setWorkLocation(first)
        works.setWorkLocation(site("latest", "LATEST"))
        val vm = editor("first")
        advanceUntilIdle()
        assertFalse(vm.state.value.isNew)
        vm.update(LocationField.LABEL, "Renamed")
        vm.update(LocationField.PROJECT_NUMBER, "  job   42  ")
        vm.update(LocationField.RADIUS_METERS, (GeofenceRadiusOptions.ONE_MILE_METERS * 3).toString())
        vm.save()
        advanceUntilIdle()
        assertEquals(PlaceEditorPhase.SAVED, vm.state.value.phase)
        assertEquals("Renamed", works.getWorkLocation("first")?.label)
        assertEquals("JOB   42", works.getWorkLocation("first")?.projectNumber)
        assertEquals(3 * GeofenceRadiusOptions.ONE_MILE_METERS, works.getWorkLocation("first")!!.radiusMeters)
        assertEquals("latest", works.getWorkLocation("latest")?.label)
        assertEquals(2, works.getWorkLocations().size)
    }

    @Test fun `map address and current location only update a draft`() = runTest(dispatchers.testDispatcher) {
        val vm = editor()
        advanceUntilIdle()
        vm.selectMapPin(40.0, -73.0, GeofenceRadiusOptions.ONE_MILE_METERS)
        advanceUntilIdle()
        assertTrue(works.getWorkLocations().isEmpty())
        vm.update(LocationField.ADDRESS, "Address")
        vm.resolveAddress()
        advanceUntilIdle()
        assertEquals("Resolved address", vm.state.value.draft.address)
        assertTrue(works.getWorkLocations().isEmpty())
        vm.update(LocationField.ADDRESS, "Unresolved address draft")
        assertFalse(vm.state.value.draft.addressResolved)
        vm.useCurrentLocation()
        advanceUntilIdle()
        assertEquals("41.0", vm.state.value.draft.latitude)
        assertTrue(vm.state.value.draft.addressResolved)
        assertTrue(works.getWorkLocations().isEmpty())
        vm.update(LocationField.LABEL, "New site")
        vm.update(LocationField.PROJECT_NUMBER, "N-1")
        vm.save()
        advanceUntilIdle()
        assertEquals(1, works.getWorkLocations().size)
    }

    @Test fun `draft survives recreation and abandoning it never modifies saved place`() = runTest(dispatchers.testDispatcher) {
        val original = site("first", "FIRST")
        works.setWorkLocation(original)
        val saved = SavedStateHandle(mapOf("worksiteId" to "first"))
        val vm = editor(saved = saved)
        advanceUntilIdle()
        vm.update(LocationField.LABEL, "Unsaved")
        val recreated = editor(saved = saved)
        advanceUntilIdle()
        assertEquals("Unsaved", recreated.state.value.draft.name)
        assertTrue(recreated.state.value.dirty)
        val reopened = editor("first")
        advanceUntilIdle()
        assertEquals("first", reopened.state.value.draft.name)
        assertEquals(original, works.getWorkLocation("first"))
    }

    @Test fun `duplicate job invalid name coordinates and radius stay open without saving`() = runTest(dispatchers.testDispatcher) {
        works.setWorkLocation(site("first", "FIRST"))
        val vm = editor()
        advanceUntilIdle()
        vm.save()
        assertEquals(PlaceEditorPhase.VALIDATION_ERROR, vm.state.value.phase)
        vm.update(LocationField.LABEL, "Second")
        vm.update(LocationField.PROJECT_NUMBER, " first ")
        vm.update(LocationField.LATITUDE, "100")
        vm.update(LocationField.LONGITUDE, "0")
        vm.save()
        assertEquals(PlaceEditorPhase.VALIDATION_ERROR, vm.state.value.phase)
        vm.update(LocationField.LATITUDE, "40")
        vm.update(LocationField.RADIUS_METERS, "2000")
        vm.save()
        assertEquals(PlaceEditorPhase.VALIDATION_ERROR, vm.state.value.phase)
        vm.update(LocationField.RADIUS_METERS, GeofenceRadiusOptions.ONE_MILE_METERS.toString())
        vm.save()
        advanceUntilIdle()
        assertEquals(PlaceEditorPhase.FAILURE, vm.state.value.phase)
        assertTrue(vm.state.value.message.contains("already assigned"))
        assertEquals(1, works.getWorkLocations().size)
    }

    @Test fun `failed persistence preserves draft and does not register geofence`() = runTest(dispatchers.testDispatcher) {
        works.setWorkLocation(site("first", "FIRST"))
        val failing = object : WorkLocationRepository by works {
            override suspend fun setWorkLocation(workLocation: WorkLocation) {
                error("Disk unavailable")
            }
        }
        val vm = editor("first", repository = failing)
        advanceUntilIdle()
        vm.update(LocationField.LABEL, "Unsaved")
        vm.save()
        advanceUntilIdle()
        assertEquals(PlaceEditorPhase.FAILURE, vm.state.value.phase)
        assertEquals("Unsaved", vm.state.value.draft.name)
        assertTrue(vm.state.value.dirty)
        assertEquals(0, registrar.calls)
    }

    @Test fun `duplicate save is disabled and geofence warning is a saved result`() = runTest(dispatchers.testDispatcher) {
        works.setWorkLocation(site("first", "FIRST"))
        val release = CompletableDeferred<Unit>()
        var saves = 0
        val waiting = object : WorkLocationRepository by works {
            override suspend fun setWorkLocation(workLocation: WorkLocation) {
                saves++
                release.await()
                works.setWorkLocation(workLocation)
            }
        }
        registrar.fail = true
        val vm = editor("first", repository = waiting)
        advanceUntilIdle()
        vm.save()
        runCurrent()
        vm.save()
        assertEquals(PlaceEditorPhase.SAVING, vm.state.value.phase)
        release.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, saves)
        assertEquals(PlaceEditorPhase.SAVED, vm.state.value.phase)
        assertTrue(vm.state.value.message.contains("Geofence warning"))
    }

    @Test fun `home uses draft and original radius presets without job number`() = runTest(dispatchers.testDispatcher) {
        val vm = editor("home")
        advanceUntilIdle()
        vm.useCurrentLocation()
        advanceUntilIdle()
        assertEquals(null, homes.getHomeLocation())
        vm.update(LocationField.RADIUS_METERS, GeofenceRadiusOptions.all[1].meters.toString())
        vm.save()
        advanceUntilIdle()
        assertEquals(PlaceEditorPhase.SAVED, vm.state.value.phase)
        assertEquals(GeofenceRadiusOptions.all[1].meters, homes.getHomeLocation()!!.radiusMeters)
    }

    private fun site(id: String, job: String) =
        WorkLocation(40.0, -73.0, GeofenceRadiusOptions.ONE_MILE_METERS, now, id, id, projectNumber = job)
    private fun editor(
        id: String = "new",
        saved: SavedStateHandle = SavedStateHandle(
            if (id ==
                "home"
            ) {
                mapOf("kind" to "home")
            } else if (id == "new") {
                emptyMap()
            } else {
                mapOf("worksiteId" to id)
            },
        ),
        repository: WorkLocationRepository = works,
    ) = PlaceEditorViewModel(
        homes,
        repository,
        object : CurrentGeofenceLocationProvider {
            override suspend fun currentPreciseHomeLocation(radiusMeters: Float) = HomeLocation(41.0, -74.0, radiusMeters, now)
            override suspend fun currentPreciseWorkLocation(radiusMeters: Float) = WorkLocation(41.0, -74.0, radiusMeters, now)
        },
        object : AddressGeocoder {
            override suspend fun findAddress(query: String) = GeocodedAddress(42.0, -75.0, "Resolved address")
            override suspend fun reverseGeocode(latitude: Double, longitude: Double) = "Pin address"
        },
        object : HomeGeofenceRegistrar {
            override suspend fun registerHomeGeofence(homeLocation: HomeLocation) = Unit
            override suspend fun unregisterHomeGeofence() = Unit
        },
        registrar,
        clock,
        saved,
    )
}

private class RecordingRegistrar : WorkGeofenceRegistrar {
    var calls = 0
    var fail = false
    override suspend fun registerWorkGeofence(workLocation: WorkLocation) = Unit
    override suspend fun registerWorkGeofences(workLocations: List<WorkLocation>) {
        calls++
        if (fail) error("Permission denied")
    }
    override suspend fun unregisterWorkGeofence() = Unit
    override suspend fun unregisterWorkGeofences(workLocations: List<WorkLocation>) = Unit
}
