package com.robertochavez.timetracker.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.robertochavez.timetracker.core.common.model.ActivityBucket
import com.robertochavez.timetracker.core.common.model.ActivityInterval
import java.time.Instant

@Entity(
    tableName = "activity_intervals",
    foreignKeys = [
        ForeignKey(
            entity = AwaySessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class ActivityIntervalEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val bucket: ActivityBucket,
    val startEpochMillis: Long,
    val endEpochMillis: Long?,
) {
    fun toModelOrNull(): ActivityInterval? {
        val end = endEpochMillis ?: return null
        return ActivityInterval(
            id = id,
            sessionId = sessionId,
            bucket = bucket,
            start = Instant.ofEpochMilli(startEpochMillis),
            end = Instant.ofEpochMilli(end),
        )
    }

    companion object {
        fun fromModel(interval: ActivityInterval): ActivityIntervalEntity = ActivityIntervalEntity(
            id = interval.id,
            sessionId = interval.sessionId,
            bucket = interval.bucket,
            startEpochMillis = interval.start.toEpochMilli(),
            endEpochMillis = interval.end.toEpochMilli(),
        )
    }
}
