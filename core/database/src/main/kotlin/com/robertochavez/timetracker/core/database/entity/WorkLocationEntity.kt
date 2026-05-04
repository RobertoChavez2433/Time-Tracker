package com.robertochavez.timetracker.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.robertochavez.timetracker.core.common.model.WorkLocation
import java.time.Instant

@Entity(tableName = "work_locations")
data class WorkLocationEntity(
    @PrimaryKey val id: String = WORK_ID,
    val label: String = WorkLocation.DEFAULT_LABEL,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val updatedAtEpochMillis: Long,
) {
    fun toModel(): WorkLocation = WorkLocation(
        latitude = latitude,
        longitude = longitude,
        radiusMeters = radiusMeters,
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
        id = id,
        label = label,
    )

    companion object {
        const val WORK_ID = WorkLocation.DEFAULT_ID

        fun fromModel(workLocation: WorkLocation): WorkLocationEntity = WorkLocationEntity(
            id = workLocation.id,
            label = workLocation.label,
            latitude = workLocation.latitude,
            longitude = workLocation.longitude,
            radiusMeters = workLocation.radiusMeters,
            updatedAtEpochMillis = workLocation.updatedAt.toEpochMilli(),
        )
    }
}
