package io.github.caendeith.awakr.ui.sleep

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Background import so the Sleep screen has fresh data when it is opened.
 *
 * The worker never asks for permissions and never talks to the network: it only copies what
 * Health Connect already exposes into the local database. Missing source or permission is a
 * successful run (nothing to retry), while an actual read failure is retried with backoff.
 */
class SleepSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = when (SleepSync.refresh(applicationContext)) {
        is SleepSyncResult.Imported,
        SleepSyncResult.SourceUnavailable,
        SleepSyncResult.UpdateRequired,
        SleepSyncResult.PermissionMissing -> Result.success()

        is SleepSyncResult.Failed -> Result.retry()
    }

    companion object {

        private const val UNIQUE_NAME = "sleep_sync"
        private const val INTERVAL_HOURS = 3L

        /** Safe to call on every launch: keeps the already enqueued periodic request. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SleepSyncWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
