package com.robertochavez.timetracker.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class HubSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = runCatching {
        coordinator().syncNow()
        Result.success()
    }.getOrElse { if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure() }

    private fun coordinator() = EntryPointAccessors.fromApplication(applicationContext, HubSyncEntryPoint::class.java).coordinator()
}

class HubReceiptWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = runCatching {
        coordinator().ingestReceipts()
        Result.success()
    }.getOrElse { if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure() }

    private fun coordinator() = EntryPointAccessors.fromApplication(applicationContext, HubSyncEntryPoint::class.java).coordinator()
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface HubSyncEntryPoint {
    fun coordinator(): HubSyncCoordinator
}

private const val MAX_RETRIES = 5
