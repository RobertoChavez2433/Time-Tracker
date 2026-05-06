package com.robertochavez.timetracker.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.robertochavez.timetracker.core.common.model.WorkSiteSession
import java.time.Instant

@Entity(
    tableName = "work_site_sessions",
    indices = [Index("workLocationId")],
)
data class WorkSiteSessionEntity(
    @PrimaryKey val id: String,
    val workLocationId: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long?,
) {
    fun toModel(): WorkSiteSession = WorkSiteSession(
        id = id,
        workLocationId = workLocationId,
        start = Instant.ofEpochMilli(startEpochMillis),
        end = endEpochMillis?.let(Instant::ofEpochMilli),
    )

    companion object {
        fun fromModel(session: WorkSiteSession): WorkSiteSessionEntity = WorkSiteSessionEntity(
            id = session.id,
            workLocationId = session.workLocationId,
            startEpochMillis = session.start.toEpochMilli(),
            endEpochMillis = session.end?.toEpochMilli(),
        )
    }
}
