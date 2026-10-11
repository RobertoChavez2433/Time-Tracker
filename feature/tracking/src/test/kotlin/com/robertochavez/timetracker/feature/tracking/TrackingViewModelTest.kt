package com.robertochavez.timetracker.feature.tracking

import androidx.lifecycle.SavedStateHandle
import com.robertochavez.timetracker.core.common.model.AwaySession
import com.robertochavez.timetracker.core.common.repository.TrackingRepository
import com.robertochavez.timetracker.core.testing.FakeTrackingRepository
import com.robertochavez.timetracker.core.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class TrackingViewModelTest {
    @get:Rule val dispatchers = MainDispatcherRule()
    private val now = Instant.parse("2026-08-30T16:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val original = AwaySession("session", now.minusSeconds(7200), now.minusSeconds(3600), drivenMiles = 12.0)

    @Test fun `compact status uses only active observation and manual controls`() = runTest(dispatchers.testDispatcher) {
        val repository = FakeTrackingRepository(listOf(original))
        val vm = TrackingViewModel(repository, clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        advanceUntilIdle()
        assertFalse(vm.uiState.value.hasActiveSession)
        vm.startManualSession()
        advanceUntilIdle()
        assertEquals(listOf(now), repository.manualStarts)
        assertTrue(vm.uiState.value.hasActiveSession)
        assertTrue(repository.sessionRangeRequests.isEmpty())
    }

    @Test fun `correction saves times miles and inclusion together and retains untouched fields`() = runTest(dispatchers.testDispatcher) {
        val repository = FakeTrackingRepository(listOf(original))
        val vm = editor(repository)
        advanceUntilIdle()
        vm.edit(miles = "23.5", included = false)
        assertEquals(original, repository.getSession(original.id))
        vm.save()
        advanceUntilIdle()
        val saved = repository.getSession(original.id)!!
        assertEquals(original.start, saved.start)
        assertEquals(original.end, saved.end)
        assertEquals(23.5, saved.drivenMiles, 0.0)
        assertFalse(saved.countsTowardTotals)
        assertTrue(vm.state.value.saved)
    }

    @Test fun `invalid corrections remain visible and never partially write`() = runTest(dispatchers.testDispatcher) {
        val repository = FakeTrackingRepository(listOf(original))
        val vm = editor(repository)
        advanceUntilIdle()
        listOf("NaN", "Infinity", "-1", "text").forEach { invalid ->
            vm.edit(miles = invalid, included = false)
            vm.save()
            advanceUntilIdle()
            assertTrue(vm.state.value.error.isNotBlank())
            assertFalse(vm.state.value.saved)
            assertEquals(original, repository.getSession(original.id))
        }
        vm.edit(miles = "1", end = original.start.toString())
        vm.save()
        advanceUntilIdle()
        assertTrue(vm.state.value.error.contains("after start"))
        vm.edit(end = "")
        vm.save()
        advanceUntilIdle()
        assertTrue(vm.state.value.error.contains("end time"))
        assertEquals(original, repository.getSession(original.id))
    }

    @Test fun `correction draft survives recreation and cancel discards changes`() = runTest(dispatchers.testDispatcher) {
        val repository = FakeTrackingRepository(listOf(original))
        val handle = SavedStateHandle(mapOf("sessionId" to original.id))
        val vm = editor(repository, handle)
        advanceUntilIdle()
        vm.edit(miles = "44", included = false)
        val recreated = editor(repository, handle)
        advanceUntilIdle()
        assertEquals("44", recreated.state.value.miles)
        assertTrue(recreated.state.value.dirty)
        val reopened = editor(repository)
        advanceUntilIdle()
        assertEquals("12.0", reopened.state.value.miles)
        assertEquals(original, repository.getSession(original.id))
    }

    @Test fun `failed and duplicate saves keep an editable correction`() = runTest(dispatchers.testDispatcher) {
        val fake = FakeTrackingRepository(listOf(original))
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val failing = object : TrackingRepository by fake {
            override suspend fun saveCorrection(
                sessionId: String,
                start: Instant,
                end: Instant?,
                drivenMiles: Double,
                countsTowardTotals: Boolean,
            ): AwaySession? {
                calls++
                release.await()
                error("Disk full")
            }
        }
        val vm = editor(failing)
        advanceUntilIdle()
        vm.edit(miles = "14")
        vm.save()
        runCurrent()
        vm.save()
        release.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, calls)
        assertEquals("Disk full", vm.state.value.error)
        assertEquals("14", vm.state.value.miles)
        assertFalse(vm.state.value.saving)
        assertEquals(original, fake.getSession(original.id))
    }

    private fun editor(repository: TrackingRepository, saved: SavedStateHandle = SavedStateHandle(mapOf("sessionId" to original.id))) =
        SessionCorrectionViewModel(repository, clock, saved)
}
