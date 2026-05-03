package com.robertochavez.timetracker.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.robertochavez.timetracker.core.common.model.PayPeriodSettings
import java.time.LocalDate

@Entity(tableName = "pay_period_settings")
data class PayPeriodSettingsEntity(
    @PrimaryKey val id: String = DEFAULT_ID,
    val biweeklyAnchorEpochDay: Long,
) {
    fun toModel(): PayPeriodSettings = PayPeriodSettings(
        biweeklyAnchorStartDate = LocalDate.ofEpochDay(biweeklyAnchorEpochDay),
    )

    companion object {
        const val DEFAULT_ID = "default"

        fun fromModel(settings: PayPeriodSettings): PayPeriodSettingsEntity = PayPeriodSettingsEntity(
            biweeklyAnchorEpochDay = settings.biweeklyAnchorStartDate.toEpochDay(),
        )
    }
}
