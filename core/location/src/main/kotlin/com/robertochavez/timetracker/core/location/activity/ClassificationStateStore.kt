package com.robertochavez.timetracker.core.location.activity

import android.content.Context
import com.robertochavez.timetracker.core.common.domain.ClassificationState
import com.robertochavez.timetracker.core.common.domain.MovementDriveInferenceState
import com.robertochavez.timetracker.core.common.model.AwaySession
import com.robertochavez.timetracker.core.common.model.ClassificationStateResetter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClassificationStateStore @Inject constructor(@ApplicationContext context: Context) : ClassificationStateResetter {
    private val preferences = context.getSharedPreferences("activity_classification", Context.MODE_PRIVATE)

    fun read(session: AwaySession): ClassificationState? {
        if (preferences.getString("session", null) != session.id ||
            preferences.getLong("start", Long.MIN_VALUE) != session.start.toEpochMilli()
        ) {
            return null
        }
        return ClassificationState(
            atHome = preferences.getBoolean("home", false),
            atSite = preferences.getBoolean("site", false),
            vehicleActive = preferences.getBoolean("vehicle", false),
            movement = MovementDriveInferenceState(instant("candidate"), preferences.getBoolean("inferred", false)),
            lastEventAt = instant("event"),
            nonDrivingAt = instant("non_driving"),
        )
    }

    fun write(session: AwaySession, state: ClassificationState) {
        check(
            preferences.edit().clear()
                .putString("session", session.id)
                .putLong("start", session.start.toEpochMilli())
                .putBoolean("home", state.atHome)
                .putBoolean("site", state.atSite)
                .putBoolean("vehicle", state.vehicleActive)
                .putBoolean("inferred", state.movement.driveActive)
                .putLong("candidate", state.movement.candidateStart?.toEpochMilli() ?: Long.MIN_VALUE)
                .putLong("event", state.lastEventAt?.toEpochMilli() ?: Long.MIN_VALUE)
                .putLong("non_driving", state.nonDrivingAt?.toEpochMilli() ?: Long.MIN_VALUE)
                .commit(),
        ) { "Could not persist classification state." }
    }

    override fun clear() {
        check(preferences.edit().clear().commit()) { "Could not clear classification state." }
    }

    private fun instant(key: String): Instant? = preferences.getLong(key, Long.MIN_VALUE)
        .takeUnless { it == Long.MIN_VALUE }?.let(Instant::ofEpochMilli)
}
