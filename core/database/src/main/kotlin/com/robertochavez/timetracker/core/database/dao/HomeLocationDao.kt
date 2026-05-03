package com.robertochavez.timetracker.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.robertochavez.timetracker.core.database.entity.HomeLocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeLocationDao {
    @Query("SELECT * FROM home_locations WHERE id = :id LIMIT 1")
    fun observeHomeLocation(id: String = HomeLocationEntity.HOME_ID): Flow<HomeLocationEntity?>

    @Query("SELECT * FROM home_locations WHERE id = :id LIMIT 1")
    suspend fun getHomeLocation(id: String = HomeLocationEntity.HOME_ID): HomeLocationEntity?

    @Upsert
    suspend fun upsert(entity: HomeLocationEntity)
}
