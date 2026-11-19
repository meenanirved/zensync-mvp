package com.zensync.mvp

import android.app.Application
import com.zensync.mvp.di.AppContainer
import com.zensync.mvp.work.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ZenSyncApp : Application() {
    val container: AppContainer by lazy { AppContainer.get(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Start the FxA account manager (restores a signed-in session, if any).
        scope.launch { container.syncRepository.start() }
        SyncWorker.schedule(this)
    }
}
