package com.robertochavez.timetracker.core.database.repository

import com.robertochavez.timetracker.core.common.model.PayPeriodSettings
import com.robertochavez.timetracker.core.common.model.WorkSchedule
import com.robertochavez.timetracker.core.common.repository.PayPeriodSettingsRepository
import com.robertochavez.timetracker.core.common.repository.WorkScheduleRepository
import com.robertochavez.timetracker.core.database.dao.PayPeriodSettingsDao
import com.robertochavez.timetracker.core.database.dao.WorkScheduleDao
import com.robertochavez.timetracker.core.database.entity.PayPeriodSettingsEntity
import com.robertochavez.timetracker.core.database.entity.WorkScheduleEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomWorkScheduleRepository @Inject constructor(
    private val workScheduleDao: WorkScheduleDao,
) : WorkScheduleRepository {
    override fun observeWorkSchedule(): Flow<WorkSchedule> = workScheduleDao.observeWorkScheduleDays()
        .map(WorkScheduleEntity::toSchedule)

    override suspend fun getWorkSchedule(): WorkSchedule = WorkScheduleEntity.toSchedule(workScheduleDao.getWorkScheduleDays())

    override suspend fun setWorkSchedule(schedule: WorkSchedule) {
        workScheduleDao.upsertAll(WorkScheduleEntity.fromSchedule(schedule))
    }
}

@Singleton
class RoomPayPeriodSettingsRepository @Inject constructor(
    private val payPeriodSettingsDao: PayPeriodSettingsDao,
) : PayPeriodSettingsRepository {
    override fun observeSettings(): Flow<PayPeriodSettings> = payPeriodSettingsDao.observeSettings()
        .map { it?.toModel() ?: defaultSettings() }

    override suspend fun getSettings(): PayPeriodSettings = payPeriodSettingsDao.getSettings()?.toModel() ?: defaultSettings()

    override suspend fun setSettings(settings: PayPeriodSettings) {
        payPeriodSettingsDao.upsert(PayPeriodSettingsEntity.fromModel(settings))
    }

    private fun defaultSettings(): PayPeriodSettings = PayPeriodSettings(
        biweeklyAnchorStartDate = LocalDate.now().with(java.time.DayOfWeek.MONDAY),
    )
}
