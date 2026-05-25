package com.robertochavez.timetracker.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.robertochavez.timetracker.core.common.model.DriveMovementObservation
import java.time.Instant

@Entity(
    tableName = "drive_movement_observations",
    foreignKeys = [
        ForeignKey(
            entity = AwaySessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("startEpochMillis")],
)
data class DriveMovementObservationEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val distanceMeters: Double,
    val countedDistanceMeters: Double,
    val speedMilesPerHour: Double,
    val vehicleLike: Boolean,
    val atWork: Boolean,
) {
    fun toModel(): DriveMovementObservation = DriveMovementObservation(
        id = id,
        sessionId = sessionId,
        start = Instant.ofEpochMilli(startEpochMillis),
        end = Instant.ofEpochMilli(endEpochMillis),
        distanceMeters = distanceMeters,
        countedDistanceMeters = countedDistanceMeters,
        speedMilesPerHour = speedMilesPerHour,
        vehicleLike = vehicleLike,
        atWork = atWork,
    )

    companion object {
        fun fromModel(observation: DriveMovementObservation): DriveMovementObservationEntity = DriveMovementObservationEntity(
            id = observation.id,
            sessionId = observation.sessionId,
            startEpochMillis = observation.start.toEpochMilli(),
            endEpochMillis = observation.end.toEpochMilli(),
            distanceMeters = observation.distanceMeters,
            countedDistanceMeters = observation.countedDistanceMeters,
            speedMilesPerHour = observation.speedMilesPerHour,
            vehicleLike = observation.vehicleLike,
            atWork = observation.atWork,
        )
    }
}
