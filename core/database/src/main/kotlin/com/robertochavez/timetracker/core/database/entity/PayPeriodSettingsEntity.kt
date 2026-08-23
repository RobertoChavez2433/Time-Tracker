package com.robertochavez.timetracker.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.robertochavez.timetracker.core.common.model.PayPeriodCadence
import com.robertochavez.timetracker.core.common.model.PayPeriodSettings
import java.time.LocalDate

@Entity(tableName = "pay_period_settings")
data class PayPeriodSettingsEntity(
    @PrimaryKey val id: String = DEFAULT_ID,
    val biweeklyAnchorEpochDay: Long,
    val cadence: String = PayPeriodCadence.BIWEEKLY.name,
) {
    fun toModel(): PayPeriodSettings = PayPeriodSettings(
        anchorStartDate = LocalDate.ofEpochDay(biweeklyAnchorEpochDay),
        cadence = runCatching { PayPeriodCadence.valueOf(cadence) }.getOrDefault(PayPeriodCadence.BIWEEKLY),
    )

    companion object {
        const val DEFAULT_ID = "default"

        fun fromModel(settings: PayPeriodSettings): PayPeriodSettingsEntity = PayPeriodSettingsEntity(
            biweeklyAnchorEpochDay = settings.anchorStartDate.toEpochDay(),
            cadence = settings.cadence.name,
        )
    }
}
