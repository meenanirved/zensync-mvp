package com.zensync.mvp.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.zensync.mvp.di.AppContainer
import java.util.concurrent.TimeUnit

/**
 * Explicit periodic "Sync now" trigger (12h). Note the FxaAccountManager's
 * own WorkManagerSyncManager ALSO schedules periodic sync from
 * SyncConfig.periodicSyncConfig; this worker is the app-level, user-visible
 * trigger and is safe to keep alongside it — both call the same sync path.
 * TODO: verify in testing that the two do not double-sync wastefully; if
 * they do, drop this worker and rely on the component's scheduler.
 */
class SyncWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = AppContainer.get(applicationContext)
        return container.syncRepository.syncNow()
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    companion object {
        const val UNIQUE_NAME = "zensync-periodic-sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(12, TimeUnit.HOURS)
                .addTag("sync")
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
