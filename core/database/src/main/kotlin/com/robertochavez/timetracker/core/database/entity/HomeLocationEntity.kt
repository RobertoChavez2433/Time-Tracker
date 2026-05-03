package com.robertochavez.timetracker.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.robertochavez.timetracker.core.common.model.HomeLocation
import java.time.Instant

@Entity(tableName = "home_locations")
data class HomeLocationEntity(
    @PrimaryKey val id: String = HOME_ID,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val updatedAtEpochMillis: Long,
) {
    fun toModel(): HomeLocation = HomeLocation(
        latitude = latitude,
        longitude = longitude,
        radiusMeters = radiusMeters,
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    )

    companion object {
        const val HOME_ID = "home"

        fun fromModel(homeLocation: HomeLocation): HomeLocationEntity = HomeLocationEntity(
            latitude = homeLocation.latitude,
            longitude = homeLocation.longitude,
            radiusMeters = homeLocation.radiusMeters,
            updatedAtEpochMillis = homeLocation.updatedAt.toEpochMilli(),
        )
    }
}
