package com.robertochavez.timetracker.core.sync

import com.robertochavez.timetracker.core.common.repository.WorkLocationRepository
import com.robertochavez.timetracker.core.location.geofence.WorkGeofenceRegistrar
import com.robertochavez.timetracker.core.location.mileage.DriveMileageTracker
import javax.inject.Inject

class LocalDataRestoreCoordinator @Inject constructor(
    private val importer: BackupImporter,
    private val mileage: DriveMileageTracker,
    private val workLocationRepository: WorkLocationRepository,
    private val workGeofenceRegistrar: WorkGeofenceRegistrar,
) {
    suspend fun restore(backup: ByteArray, passphrase: CharArray) {
        importer.import(backup, passphrase)
        mileage.stopTracking()
        workGeofenceRegistrar.registerWorkGeofences(workLocationRepository.getWorkLocations())
    }
}
