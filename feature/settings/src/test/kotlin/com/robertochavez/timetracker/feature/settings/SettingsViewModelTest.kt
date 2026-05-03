package com.robertochavez.timetracker.feature.settings

import com.robertochavez.timetracker.core.location.activity.ActivityTransitionRegistrar
import com.robertochavez.timetracker.core.testing.FakeAppSettingsRepository
import com.robertochavez.timetracker.core.testing.FakePayPeriodSettingsRepository
import com.robertochavez.timetracker.core.testing.FakeWorkScheduleRepository
import com.robertochavez.timetracker.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `updates work schedule through repository contract`() = runTest(mainDispatcherRule.testDispatcher) {
        val workScheduleRepository = FakeWorkScheduleRepository()
        val viewModel = SettingsViewModel(
            workScheduleRepository = workScheduleRepository,
            payPeriodSettingsRepository = FakePayPeriodSettingsRepository(),
            appSettingsRepository = FakeAppSettingsRepository(),
            activityTransitionRegistrar = RecordingActivityTransitionRegistrar(),
        )

        viewModel.setDayTrackable(DayOfWeek.SATURDAY.name, true)
        advanceUntilIdle()

        assertTrue(DayOfWeek.SATURDAY in workScheduleRepository.schedule.value.trackableDays)
    }

    @Test
    fun `registers activity transitions through location contract`() = runTest(mainDispatcherRule.testDispatcher) {
        val activityRegistrar = RecordingActivityTransitionRegistrar()
        val viewModel = SettingsViewModel(
            workScheduleRepository = FakeWorkScheduleRepository(),
            payPeriodSettingsRepository = FakePayPeriodSettingsRepository(),
            appSettingsRepository = FakeAppSettingsRepository(),
            activityTransitionRegistrar = activityRegistrar,
        )

        viewModel.registerActivityTransitions()
        advanceUntilIdle()

        assertEquals(1, activityRegistrar.registerCount)
    }
}

private class RecordingActivityTransitionRegistrar : ActivityTransitionRegistrar {
    var registerCount = 0

    override suspend fun registerDriveAndIdleTransitions() {
        registerCount += 1
    }

    override suspend fun unregisterDriveAndIdleTransitions() = Unit
}
