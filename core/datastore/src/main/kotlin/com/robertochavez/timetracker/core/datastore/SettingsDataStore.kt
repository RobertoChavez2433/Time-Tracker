package com.robertochavez.timetracker.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.robertochavez.timetracker.core.common.model.AppSettings
import com.robertochavez.timetracker.core.common.repository.AppSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.timeTrackerSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "time_tracker_settings",
)

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppSettingsRepository {
    override val settings: Flow<AppSettings> = context.timeTrackerSettingsDataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences ->
            AppSettings(
                minimalActiveNotificationEnabled = preferences[MINIMAL_ACTIVE_NOTIFICATION] ?: false,
                liveTimerNotificationEnabled = preferences[LIVE_TIMER_NOTIFICATION] ?: false,
                privacyDisclosureAccepted = preferences[PRIVACY_DISCLOSURE_ACCEPTED] ?: false,
            )
        }

    override suspend fun setMinimalActiveNotificationEnabled(enabled: Boolean) {
        context.timeTrackerSettingsDataStore.edit { it[MINIMAL_ACTIVE_NOTIFICATION] = enabled }
    }

    override suspend fun setLiveTimerNotificationEnabled(enabled: Boolean) {
        context.timeTrackerSettingsDataStore.edit { it[LIVE_TIMER_NOTIFICATION] = enabled }
    }

    override suspend fun setPrivacyDisclosureAccepted(accepted: Boolean) {
        context.timeTrackerSettingsDataStore.edit { it[PRIVACY_DISCLOSURE_ACCEPTED] = accepted }
    }

    private companion object {
        val MINIMAL_ACTIVE_NOTIFICATION = booleanPreferencesKey("minimal_active_notification")
        val LIVE_TIMER_NOTIFICATION = booleanPreferencesKey("live_timer_notification")
        val PRIVACY_DISCLOSURE_ACCEPTED = booleanPreferencesKey("privacy_disclosure_accepted")
    }
}
