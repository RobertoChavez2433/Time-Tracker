package com.robertochavez.timetracker.feature.settings

import com.robertochavez.timetracker.core.common.model.PayPeriodCadence
import com.robertochavez.timetracker.core.common.model.WorkSchedule
import com.robertochavez.timetracker.core.common.repository.WorkScheduleRepository
import com.robertochavez.timetracker.core.location.activity.ActivityTransitionRegistrar
import com.robertochavez.timetracker.core.logging.NoopAppLogger
import com.robertochavez.timetracker.core.testing.FakeAppSettingsRepository
import com.robertochavez.timetracker.core.testing.FakeLocalDataResetter
import com.robertochavez.timetracker.core.testing.FakePayPeriodSettingsRepository
import com.robertochavez.timetracker.core.testing.FakeWorkScheduleRepository
import com.robertochavez.timetracker.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
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
            localDataResetter = FakeLocalDataResetter(),
            activityTransitionRegistrar = RecordingActivityTransitionRegistrar(),
            logger = NoopAppLogger(),
        )

        viewModel.setDayTrackable(DayOfWeek.SATURDAY.name, true)
        advanceUntilIdle()

        assertTrue(DayOfWeek.SATURDAY in workScheduleRepository.schedule.value.trackableDays)
    }

    @Test
    fun `allows zero tracked days without restoring weekdays`() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = FakeWorkScheduleRepository()
        val viewModel = viewModel(repository)

        DayOfWeek.entries.forEach { viewModel.setDayTrackable(it.name, false) }
        advanceUntilIdle()

        assertTrue(repository.schedule.value.trackableDays.isEmpty())
    }

    @Test
    fun `weekly cadence and anchor are saved together`() = runTest(mainDispatcherRule.testDispatcher) {
        val payPeriodRepository = FakePayPeriodSettingsRepository()
        val viewModel = SettingsViewModel(
            workScheduleRepository = FakeWorkScheduleRepository(),
            payPeriodSettingsRepository = payPeriodRepository,
            appSettingsRepository = FakeAppSettingsRepository(),
            localDataResetter = FakeLocalDataResetter(),
            activityTransitionRegistrar = RecordingActivityTransitionRegistrar(),
            logger = NoopAppLogger(),
        )
        backgroundScope.launch { viewModel.uiState.collect() }

        viewModel.selectPayPeriodCadence(PayPeriodCadence.WEEKLY)
        viewModel.updateAnchorDate("2026-07-06")
        viewModel.saveAnchorDate()
        advanceUntilIdle()

        assertEquals(PayPeriodCadence.WEEKLY, payPeriodRepository.settings.value.cadence)
        assertEquals("2026-07-06", payPeriodRepository.settings.value.anchorStartDate.toString())
    }

    @Test
    fun `workday persistence failure is visible`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel(FailingWorkScheduleRepository())
        backgroundScope.launch { viewModel.uiState.collect() }

        viewModel.setDayTrackable(DayOfWeek.SATURDAY.name, true)
        advanceUntilIdle()

        assertEquals("Workday change could not be saved.", viewModel.uiState.value.statusMessage)
    }

    @Test
    fun `registers activity transitions through location contract`() = runTest(mainDispatcherRule.testDispatcher) {
        val activityRegistrar = RecordingActivityTransitionRegistrar()
        val viewModel = SettingsViewModel(
            workScheduleRepository = FakeWorkScheduleRepository(),
            payPeriodSettingsRepository = FakePayPeriodSettingsRepository(),
            appSettingsRepository = FakeAppSettingsRepository(),
            localDataResetter = FakeLocalDataResetter(),
            activityTransitionRegistrar = activityRegistrar,
            logger = NoopAppLogger(),
        )

        viewModel.registerActivityTransitions()
        advanceUntilIdle()

        assertEquals(1, activityRegistrar.registerCount)
    }

    @Test
    fun `deletes local data through reset contract`() = runTest(mainDispatcherRule.testDispatcher) {
        val localDataResetter = FakeLocalDataResetter()
        val viewModel = SettingsViewModel(
            workScheduleRepository = FakeWorkScheduleRepository(),
            payPeriodSettingsRepository = FakePayPeriodSettingsRepository(),
            appSettingsRepository = FakeAppSettingsRepository(),
            localDataResetter = localDataResetter,
            activityTransitionRegistrar = RecordingActivityTransitionRegistrar(),
            logger = NoopAppLogger(),
        )

        viewModel.deleteAllLocalData()
        advanceUntilIdle()

        assertEquals(1, localDataResetter.deleteCount)
    }

    private fun viewModel(repository: WorkScheduleRepository): SettingsViewModel = SettingsViewModel(
        workScheduleRepository = repository,
        payPeriodSettingsRepository = FakePayPeriodSettingsRepository(),
        appSettingsRepository = FakeAppSettingsRepository(),
        localDataResetter = FakeLocalDataResetter(),
        activityTransitionRegistrar = RecordingActivityTransitionRegistrar(),
        logger = NoopAppLogger(),
    )
}

private class RecordingActivityTransitionRegistrar : ActivityTransitionRegistrar {
    var registerCount = 0

    override suspend fun registerDriveAndIdleTransitions() {
        registerCount += 1
    }

    override suspend fun unregisterDriveAndIdleTransitions() = Unit
}

private class FailingWorkScheduleRepository : WorkScheduleRepository {
    private val schedule = MutableStateFlow(WorkSchedule())

    override fun observeWorkSchedule(): Flow<WorkSchedule> = schedule

    override suspend fun getWorkSchedule(): WorkSchedule = schedule.value

    override suspend fun setWorkSchedule(schedule: WorkSchedule) {
        error("disk full")
    }
}
