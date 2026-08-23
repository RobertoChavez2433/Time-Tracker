package com.robertochavez.timetracker.core.sync

import java.time.Instant

data class BackupManifest(val schemaVersion: Int, val databaseVersion: Int, val exportedAt: Instant)
