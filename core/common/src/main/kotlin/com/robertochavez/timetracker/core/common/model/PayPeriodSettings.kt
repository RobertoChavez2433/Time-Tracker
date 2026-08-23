package com.robertochavez.timetracker.core.common.model

import java.time.LocalDate

enum class PayPeriodCadence(val days: Int, val label: String) {
    WEEKLY(7, "Weekly"),
    BIWEEKLY(14, "Biweekly"),
}

data class PayPeriodSettings(val anchorStartDate: LocalDate, val cadence: PayPeriodCadence = PayPeriodCadence.BIWEEKLY)
