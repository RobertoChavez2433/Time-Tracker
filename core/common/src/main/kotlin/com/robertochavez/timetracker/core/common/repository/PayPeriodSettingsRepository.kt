package com.robertochavez.timetracker.core.common.repository

import com.robertochavez.timetracker.core.common.model.PayPeriodSettings
import kotlinx.coroutines.flow.Flow

interface PayPeriodSettingsRepository {
    fun observeSettings(): Flow<PayPeriodSettings>

    suspend fun getSettings(): PayPeriodSettings

    suspend fun setSettings(settings: PayPeriodSettings)
}
