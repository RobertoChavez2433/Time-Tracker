package com.robertochavez.timetracker.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.robertochavez.timetracker.core.common.model.AwaySession
import java.time.Instant

@Entity(tableName = "away_sessions")
data class AwaySessionEntity(
    @PrimaryKey val id: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long?,
    val countsTowardTotals: Boolean,
    val manuallyAdjusted: Boolean,
    val drivenMiles: Double,
) {
    fun toModel(): AwaySession = AwaySession(
        id = id,
        start = Instant.ofEpochMilli(startEpochMillis),
        end = endEpochMillis?.let(Instant::ofEpochMilli),
        countsTowardTotals = countsTowardTotals,
        manuallyAdjusted = manuallyAdjusted,
        drivenMiles = drivenMiles,
    )

    companion object {
        fun fromModel(session: AwaySession): AwaySessionEntity = AwaySessionEntity(
            id = session.id,
            startEpochMillis = session.start.toEpochMilli(),
            endEpochMillis = session.end?.toEpochMilli(),
            countsTowardTotals = session.countsTowardTotals,
            manuallyAdjusted = session.manuallyAdjusted,
            drivenMiles = session.drivenMiles,
        )
    }
}
