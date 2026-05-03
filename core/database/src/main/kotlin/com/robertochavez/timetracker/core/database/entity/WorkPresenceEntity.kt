package com.robertochavez.timetracker.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.robertochavez.timetracker.core.common.model.WorkPresence
import java.time.Instant

@Entity(tableName = "work_presence")
data class WorkPresenceEntity(@PrimaryKey val id: String = PRESENCE_ID, val atWork: Boolean, val updatedAtEpochMillis: Long) {
    fun toModel(): WorkPresence = WorkPresence(
        atWork = atWork,
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    )

    companion object {
        const val PRESENCE_ID = "current"

        fun fromModel(workPresence: WorkPresence): WorkPresenceEntity = WorkPresenceEntity(
            atWork = workPresence.atWork,
            updatedAtEpochMillis = workPresence.updatedAt.toEpochMilli(),
        )
    }
}
