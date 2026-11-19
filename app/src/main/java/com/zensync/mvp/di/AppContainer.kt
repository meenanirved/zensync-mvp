package com.zensync.mvp.di

import android.content.Context
import com.zensync.mvp.browser.BrowserSessionManager
import com.zensync.mvp.data.local.AppDatabase
import com.zensync.mvp.data.local.Prefs
import com.zensync.mvp.domain.repo.TabRepository
import com.zensync.mvp.domain.repo.WorkspaceRepository
import com.zensync.mvp.sync.FxaSyncManager
import com.zensync.mvp.sync.SyncRepository
import com.zensync.mvp.ui.browser.BrowserViewModel
import com.zensync.mvp.ui.settings.SettingsViewModel
import com.zensync.mvp.ui.syncedtabs.SyncedTabsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual DI (chosen over Hilt for this MVP: fewer annotation-processing
 * moving parts, and the whole graph is small enough to wire by hand).
 * One instance per Application; see ZenSyncApp.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val prefs = Prefs(appContext)
    val db: AppDatabase = AppDatabase.get(appContext)
    val workspaceRepository = WorkspaceRepository(db)
    val tabRepository = TabRepository(db)
    val browserSessionManager = BrowserSessionManager(appContext)
    val syncRepository: SyncRepository = FxaSyncManager(appContext, prefs)

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun browserViewModel(): BrowserViewModel =
        BrowserViewModel(workspaceRepository, tabRepository, browserSessionManager, prefs, appScope)

    fun settingsViewModel(): SettingsViewModel =
        SettingsViewModel(syncRepository, prefs, db, appScope)

    fun syncedTabsViewModel(): SyncedTabsViewModel =
        SyncedTabsViewModel(syncRepository)

    companion object {
        @Volatile
        private var instance: AppContainer? = null

        fun get(context: Context): AppContainer =
            instance ?: synchronized(this) {
                instance ?: AppContainer(context).also { instance = it }
            }
    }
}
