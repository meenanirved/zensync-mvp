package com.zensync.mvp.sync

import com.zensync.mvp.domain.model.AccountState
import com.zensync.mvp.domain.model.AccountStatus
import com.zensync.mvp.domain.model.SyncedTabItem
import kotlinx.coroutines.flow.StateFlow

/**
 * The app-facing sync contract. The real implementation ([FxaSyncManager])
 * is backed by Mozilla Android Components (service-firefox-accounts,
 * browser-storage-sync, service-sync-logins, feature-syncedtabs).
 * Nothing here hard-codes a client secret or token.
 */
interface SyncRepository {
    val accountState: StateFlow<AccountState>

    /** True when an FxA OAuth client id + redirect URI were supplied via
     *  local.properties (BuildConfig). Without them, sign-in is unavailable. */
    val isConfigured: Boolean

    suspend fun start()
    suspend fun beginSignIn(): Result<String> // Returns the FxA auth URL to open.
    suspend fun finishSignIn(code: String, state: String): Result<Boolean>
    suspend fun signOut()
    suspend fun syncNow(): Result<Unit>
    suspend fun refreshSyncedTabs(): Result<List<SyncedTabItem>>
    suspend fun setEngineEnabled(engineName: String, enabled: Boolean): Result<Unit>
    fun engineStates(): List<com.zensync.mvp.domain.model.SyncEngineState>
}
