package com.robertochavez.timetracker.feature.settings

import com.robertochavez.timetracker.core.common.model.WorkSchedule
import com.robertochavez.timetracker.core.common.repository.WorkScheduleRepository
import com.robertochavez.timetracker.core.location.activity.ActivityTransitionRegistrar
import com.robertochavez.timetracker.core.logging.NoopAppLogger
import com.robertochavez.timetracker.core.sync.HubProviderGateState
import com.robertochavez.timetracker.core.sync.HubTransportKind
import com.robertochavez.timetracker.core.testing.FakeAppSettingsRepository
import com.robertochavez.timetracker.core.testing.FakeLocalDataResetter
import com.robertochavez.timetracker.core.testing.FakePayPeriodSettingsRepository
import com.robertochavez.timetracker.core.testing.FakeWorkScheduleRepository
import com.robertochavez.timetracker.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRedesignTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    @Test fun `sync remains unavailable until all connection prerequisites are met`() {
        val pending = SettingsUiState(hubActivationDate = "2026-08-01", hubOutboxConfigured = true)
        assertFalse(pending.hubReady)
        assertFalse(pending.copy(hubProviderGateState = HubProviderGateState.BASIC_PASSED).hubReady)
        val verified = pending.copy(hubProviderGateState = HubProviderGateState.FULL_PASSED)
        assertTrue(verified.hubReady)
        assertFalse(verified.copy(hubActivationDate = "").hubReady)
        assertFalse(verified.copy(hubOutboxConfigured = false).hubReady)
        val fallback = pending.copy(hubTransport = HubTransportKind.DIRECT_NOTION, hubFallbackConfigured = true)
        assertFalse(fallback.hubReady)
        assertTrue(fallback.copy(hubProviderGateState = HubProviderGateState.BASIC_FAILED).hubReady)
        assertFalse(fallback.copy(hubProviderGateState = HubProviderGateState.BASIC_FAILED, hubFallbackConfigured = false).hubReady)
    }

    @Test fun `permission summary never reports ready with a missing prerequisite`() {
        val state = SettingsUiState(privacyDisclosureAccepted = true, activityDetectionEnabled = true)
        val granted = SettingsPermissions(true, true, true, true, true)
        assertEquals("Tracking permissions ready", granted.summary(state))
        assertEquals("Background location needed", granted.copy(background = false).summary(state))
        assertEquals("Device location is off", granted.copy(locationEnabled = false).summary(state))
        assertEquals("Local automation consent needed", granted.summary(state.copy(privacyDisclosureAccepted = false)))
    }

    @Test fun `rapid day changes survive suspending repository writes`() = runTest(dispatcher.testDispatcher) {
        val repository = object : WorkScheduleRepository {
            val flow = MutableStateFlow(WorkSchedule())
            override fun observeWorkSchedule() = flow
            override suspend fun getWorkSchedule() = flow.value
            override suspend fun setWorkSchedule(schedule: WorkSchedule) {
                delay(10)
                flow.value = schedule
            }
        }
        val viewModel = model(repository = repository)
        DayOfWeek.entries.forEach { viewModel.setDayTrackable(it.name, false) }
        advanceUntilIdle()
        assertTrue(repository.flow.value.trackableDays.isEmpty())
    }

    @Test fun `failed activity change retains saved switch state and releases busy state`() = runTest(dispatcher.testDispatcher) {
        val settings = FakeAppSettingsRepository()
        settings.setActivityDetectionEnabled(true)
        val registrar = object : ActivityTransitionRegistrar {
            override suspend fun registerDriveAndIdleTransitions() = Unit
            override suspend fun unregisterDriveAndIdleTransitions() {
                error("registration failed")
            }
        }
        val viewModel = model(settings = settings, registrar = registrar)
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.activityDetectionEnabled)
        viewModel.unregisterActivityTransitions()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.activityDetectionEnabled)
        assertEquals("Activity detection could not be disabled.", viewModel.uiState.value.statusMessage)
        assertNull(viewModel.uiState.value.runningAction)
    }

    @Test fun `discarding a pay period draft does not persist changes`() = runTest(dispatcher.testDispatcher) {
        val payPeriod = FakePayPeriodSettingsRepository()
        val original = payPeriod.settings.value
        val viewModel = model(payPeriod = payPeriod)
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()
        viewModel.updateAnchorDate("2026-07-06")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.payPeriodEdited)
        viewModel.discardPayPeriodEdits()
        advanceUntilIdle()
        assertEquals(original, payPeriod.settings.value)
        assertEquals(original.anchorStartDate.toString(), viewModel.uiState.value.anchorDate)
        assertFalse(viewModel.uiState.value.payPeriodEdited)
    }

    private fun model(
        repository: WorkScheduleRepository = FakeWorkScheduleRepository(),
        settings: FakeAppSettingsRepository = FakeAppSettingsRepository(),
        payPeriod: FakePayPeriodSettingsRepository = FakePayPeriodSettingsRepository(),
        registrar: ActivityTransitionRegistrar = object : ActivityTransitionRegistrar {
            override suspend fun registerDriveAndIdleTransitions() = Unit
            override suspend fun unregisterDriveAndIdleTransitions() = Unit
        },
    ) = SettingsViewModel(repository, payPeriod, settings, FakeLocalDataResetter(), registrar, NoopAppLogger())
}
