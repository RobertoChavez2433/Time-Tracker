package com.robertochavez.timetracker.core.database.repository

import com.robertochavez.timetracker.core.common.model.PayPeriodSettings
import com.robertochavez.timetracker.core.common.model.WorkSchedule
import com.robertochavez.timetracker.core.common.repository.PayPeriodSettingsRepository
import com.robertochavez.timetracker.core.common.repository.WorkScheduleRepository
import com.robertochavez.timetracker.core.database.dao.PayPeriodSettingsDao
import com.robertochavez.timetracker.core.database.dao.WorkScheduleDao
import com.robertochavez.timetracker.core.database.entity.PayPeriodSettingsEntity
import com.robertochavez.timetracker.core.database.entity.WorkScheduleEntity
import com.robertochavez.timetracker.core.logging.AppLogger
import com.robertochavez.timetracker.core.logging.LogCategory
import com.robertochavez.timetracker.core.logging.info
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomWorkScheduleRepository @Inject constructor(private val workScheduleDao: WorkScheduleDao, private val logger: AppLogger) :
    WorkScheduleRepository {
    override fun observeWorkSchedule(): Flow<WorkSchedule> = workScheduleDao.observeWorkScheduleDays()
        .map(WorkScheduleEntity::toSchedule)

    override suspend fun getWorkSchedule(): WorkSchedule = WorkScheduleEntity.toSchedule(workScheduleDao.getWorkScheduleDays())

    override suspend fun setWorkSchedule(schedule: WorkSchedule) {
        workScheduleDao.upsertAll(WorkScheduleEntity.fromSchedule(schedule))
        logger.info(LogCategory.SETTINGS, "Work schedule saved", mapOf("workdayCount" to schedule.trackableDays.size))
    }
}

@Singleton
class RoomPayPeriodSettingsRepository @Inject constructor(
    private val payPeriodSettingsDao: PayPeriodSettingsDao,
    private val logger: AppLogger,
) : PayPeriodSettingsRepository {
    override fun observeSettings(): Flow<PayPeriodSettings> = payPeriodSettingsDao.observeSettings()
        .map { it?.toModel() ?: defaultSettings() }

    override suspend fun getSettings(): PayPeriodSettings = payPeriodSettingsDao.getSettings()?.toModel() ?: defaultSettings()

    override suspend fun setSettings(settings: PayPeriodSettings) {
        payPeriodSettingsDao.upsert(PayPeriodSettingsEntity.fromModel(settings))
        logger.info(LogCategory.SETTINGS, "Pay period settings saved", mapOf("anchorDate" to settings.biweeklyAnchorStartDate.toString()))
    }

    private fun defaultSettings(): PayPeriodSettings = PayPeriodSettings(
        biweeklyAnchorStartDate = LocalDate.now().with(java.time.DayOfWeek.MONDAY),
    )
}
