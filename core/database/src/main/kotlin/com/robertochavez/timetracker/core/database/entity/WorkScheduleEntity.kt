package com.robertochavez.timetracker.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.robertochavez.timetracker.core.common.model.WorkSchedule
import java.time.DayOfWeek

@Entity(tableName = "work_schedule_days")
data class WorkScheduleEntity(@PrimaryKey val dayOfWeek: Int, val trackable: Boolean) {
    companion object {
        fun fromSchedule(schedule: WorkSchedule): List<WorkScheduleEntity> = DayOfWeek.entries.map { day ->
            WorkScheduleEntity(
                dayOfWeek = day.value,
                trackable = day in schedule.trackableDays,
            )
        }

        fun toSchedule(entities: List<WorkScheduleEntity>): WorkSchedule {
            if (entities.isEmpty()) {
                return WorkSchedule()
            }
            return WorkSchedule(
                trackableDays = entities
                    .filter { it.trackable }
                    .map { DayOfWeek.of(it.dayOfWeek) }
                    .toSet(),
            )
        }
    }
}
