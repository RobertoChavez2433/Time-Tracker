package com.robertochavez.timetracker.core.sync

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HubBackupService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exporter: BackupExporter,
    private val restoreCoordinator: LocalDataRestoreCoordinator,
) {
    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val backup = exporter.export()
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(backup) }
    }

    suspend fun import(uri: Uri, passphrase: CharArray) = withContext(Dispatchers.IO) {
        val backup = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        restoreCoordinator.restore(backup, passphrase)
    }
}
