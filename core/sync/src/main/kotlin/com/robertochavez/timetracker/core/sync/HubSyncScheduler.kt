package com.robertochavez.timetracker.core.sync

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HubSyncScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    fun ensureScheduled() {
        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniquePeriodicWork(
            SYNC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<HubSyncWorker>(15, TimeUnit.MINUTES).build(),
        )
        workManager.enqueueUniquePeriodicWork(
            RECEIPT_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<HubReceiptWorker>(15, TimeUnit.MINUTES).build(),
        )
    }

    private companion object {
        const val SYNC_WORK = "work-hub-summary-sync"
        const val RECEIPT_WORK = "work-hub-receipt-sync"
    }
}
