package com.robertochavez.timetracker.core.sync

import com.robertochavez.timetracker.core.common.repository.WorkLocationRepository
import com.robertochavez.timetracker.core.location.geofence.WorkGeofenceRegistrar
import javax.inject.Inject

class LocalDataRestoreCoordinator @Inject constructor(
    private val importer: BackupImporter,
    private val workLocationRepository: WorkLocationRepository,
    private val workGeofenceRegistrar: WorkGeofenceRegistrar,
) {
    suspend fun restore(encrypted: ByteArray, passphrase: CharArray) {
        importer.import(encrypted, passphrase)
        workGeofenceRegistrar.registerWorkGeofences(workLocationRepository.getWorkLocations())
    }
}
