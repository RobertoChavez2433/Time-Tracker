package com.robertochavez.timetracker.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.robertochavez.timetracker.core.database.entity.PayPeriodSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PayPeriodSettingsDao {
    @Query("SELECT * FROM pay_period_settings WHERE id = :id LIMIT 1")
    fun observeSettings(id: String = PayPeriodSettingsEntity.DEFAULT_ID): Flow<PayPeriodSettingsEntity?>

    @Query("SELECT * FROM pay_period_settings WHERE id = :id LIMIT 1")
    suspend fun getSettings(id: String = PayPeriodSettingsEntity.DEFAULT_ID): PayPeriodSettingsEntity?

    @Upsert
    suspend fun upsert(entity: PayPeriodSettingsEntity)
}
