package com.zensync.mvp.sync

import android.content.Context
import com.zensync.mvp.BuildConfig
import com.zensync.mvp.data.local.Prefs
import com.zensync.mvp.domain.model.AccountState
import com.zensync.mvp.domain.model.AccountStatus
import com.zensync.mvp.domain.model.SyncEngineState
import com.zensync.mvp.domain.model.SyncedTabItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mozilla.appservices.fxaclient.FxaConfig
import mozilla.appservices.fxaclient.FxaServer
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.browser.storage.sync.PlacesBookmarksStorage
import mozilla.components.browser.storage.sync.PlacesHistoryStorage
import mozilla.components.browser.storage.sync.RemoteTabsStorage
import mozilla.components.concept.base.crash.Breadcrumb
import mozilla.components.concept.base.crash.CrashReporting
import mozilla.components.concept.sync.AuthType
import mozilla.components.concept.sync.DeviceConfig
import mozilla.components.concept.sync.DeviceType
import mozilla.components.concept.sync.FxAEntryPoint
import mozilla.components.concept.sync.PeriodicSyncConfig
import mozilla.components.concept.sync.Profile
import mozilla.components.concept.sync.SyncConfig
import mozilla.components.concept.sync.SyncEngine
import mozilla.components.feature.syncedtabs.storage.SyncedTabsStorage
import mozilla.components.lib.dataprotect.SecureAbove22Preferences
import mozilla.components.service.fxa.FxaAuthData
import mozilla.components.service.fxa.manager.FxaAccountManager
import mozilla.components.service.fxa.sync.GlobalSyncableStoreProvider
import mozilla.components.service.fxa.sync.SyncReason
import mozilla.components.service.sync.logins.SyncableLoginsStorage
import kotlinx.coroutines.Job

/**
 * Firefox Account + Sync, backed by Mozilla Android Components 160.0.
 *
 * Verified against the published 160.0 sources/AARs (2026-11-17):
 *  - FxaAccountManager(context, serverConfig, deviceConfig, syncConfig, …)
 *  - beginAuthentication(pairingUrl, entrypoint, authScopes, service): String?
 *  - finishAuthentication(FxaAuthData(AuthType, code, state, declinedEngines)): Boolean
 *  - syncNow(reason, debounce, customEngineSubset)
 *  - GlobalSyncableStoreProvider.configureStore(engine to lazy { store })
 *  - SyncedTabsStorage(accountManager, browserStore, remoteTabsStorage)
 *    .getSyncedDeviceTabs(): List<SyncedDeviceTabs>
 *
 * FxA OAuth credentials come ONLY from BuildConfig fields populated from
 * local.properties (see app/build.gradle.kts). There is NO client secret:
 * FxA OAuth for a public (native-app) client uses PKCE / no secret.
 * Tokens are held by the component's own secure AccountStorage, not by us.
 *
 * IMPORTANT — what sync does and does not do here:
 *  - Engines enabled: History, Bookmarks, Tabs, Passwords (logins).
 *  - Workspaces are NOT a Sync engine. Workspace membership is local-only
 *    (Room). See README.md "Workspaces and Sync".
 *  - TODO (blocking, needs a registered FxA OAuth app): fill in
 *    fxa.client.id / fxa.redirect.url in local.properties, then test the
 *    full sign-in flow end-to-end. Until then `isConfigured == false` and
 *    sign-in is disabled with an explanation in Settings.
 */
class FxaSyncManager(
    private val context: Context,
    private val prefs: Prefs,
) : SyncRepository {

    override val isConfigured: Boolean =
        BuildConfig.FXA_CLIENT_ID.isNotBlank() && BuildConfig.FXA_REDIRECT_URL.isNotBlank()

    private val _accountState = MutableStateFlow(AccountState())
    override val accountState: StateFlow<AccountState> = _accountState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val crashReporting = object : CrashReporting {
        override fun submitCaughtException(throwable: Throwable): Job = Job()
        override fun recordCrashBreadcrumb(breadcrumb: Breadcrumb) {}
    }

    // Syncable stores, registered with the global provider so the
    // WorkManagerSyncManager inside FxaAccountManager can find them.
    private val historyStorage by lazy { PlacesHistoryStorage(context) }
    private val bookmarksStorage by lazy { PlacesBookmarksStorage(context) }
    private val remoteTabsStorage by lazy { RemoteTabsStorage(context, crashReporting) }
    private val loginsStorage by lazy {
        SyncableLoginsStorage(
            context,
            lazy { SecureAbove22Preferences(context, "zensync_logins", false, crashReporting) },
        )
    }

    private val accountManager: FxaAccountManager? by lazy {
        if (!isConfigured) return@lazy null
        GlobalSyncableStoreProvider.configureStore(SyncEngine.History to lazy { historyStorage })
        GlobalSyncableStoreProvider.configureStore(SyncEngine.Bookmarks to lazy { bookmarksStorage })
        GlobalSyncableStoreProvider.configureStore(SyncEngine.Tabs to lazy { remoteTabsStorage })
        GlobalSyncableStoreProvider.configureStore(SyncEngine.Passwords to lazy { loginsStorage })

        val serverConfig = FxaConfig(
            server = FxaServer.Release,
            clientId = BuildConfig.FXA_CLIENT_ID,
            redirectUri = BuildConfig.FXA_REDIRECT_URL,
        )
        val deviceConfig = DeviceConfig(
            name = "ZenSync MVP on Android",
            type = DeviceType.MOBILE,
            capabilities = emptySet(),
            secureStateAtRest = true,
        )
        val syncConfig = SyncConfig(
            supportedEngines = setOf(
                SyncEngine.History,
                SyncEngine.Bookmarks,
                SyncEngine.Tabs,
                SyncEngine.Passwords,
            ),
            periodicSyncConfig = PeriodicSyncConfig(periodMinutes = 720, initialDelayMinutes = 15),
            syncDecouplingEnabled = false,
        )
        FxaAccountManager(
            context = context.applicationContext,
            serverConfig = serverConfig,
            deviceConfig = deviceConfig,
            syncConfig = syncConfig,
            applicationScopes = setOf(
                "profile",
                "https://identity.mozilla.com/apps/oldsync",
            ),
        )
    }

    private val syncedTabsStorage: SyncedTabsStorage? by lazy {
        val manager = accountManager ?: return@lazy null
        SyncedTabsStorage(
            accountManager = manager,
            store = BrowserStore(),
            tabsStorage = remoteTabsStorage,
            // Tabs used within the last 24h count as "active" (mirrors the
            // upstream default used by Firefox for Android's synced-tabs UI).
            maxActiveTime = 24L * 60 * 60 * 1000,
        )
    }

    override suspend fun start() {
        val manager = accountManager
        if (manager == null) {
            _accountState.update { it.copy(status = AccountStatus.NOT_CONFIGURED) }
            return
        }
        runCatching { manager.start() }
            .onFailure { e -> setError(e) }
        refreshAccountState()
        syncedTabsStorage?.start()
    }

    override suspend fun beginSignIn(): Result<String> {
        val manager = accountManager
            ?: return Result.failure(IllegalStateException(NOT_CONFIGURED_MSG))
        return runCatching {
            // Opens the FxA web flow. The returned URL must be opened in a
            // Custom Tab / browser; the redirect comes back to this app via
            // the zensync://fxa deep link declared in AndroidManifest.xml.
            val url = manager.beginAuthentication(
                entrypoint = object : FxAEntryPoint {
                    override val entryName: String get() = "signin"
                },
                service = "sync",
            )
            url ?: throw IllegalStateException("FxA did not return an auth URL")
        }
    }

    override suspend fun finishSignIn(code: String, state: String): Result<Boolean> {
        val manager = accountManager
            ?: return Result.failure(IllegalStateException(NOT_CONFIGURED_MSG))
        return runCatching {
            val ok = manager.finishAuthentication(
                FxaAuthData(
                    authType = AuthType.Signin,
                    code = code,
                    state = state,
                    declinedEngines = emptySet(),
                ),
            )
            if (ok) {
                // First sync after sign-in.
                runCatching { manager.syncNow(reason = SyncReason.FirstSync) }
                refreshAccountState()
            }
            ok
        }.onFailure { setError(it) }
    }

    override suspend fun signOut() {
        runCatching { accountManager?.logout() }
        prefs.lastSyncAtMillis = 0L
        refreshAccountState()
    }

    override suspend fun syncNow(): Result<Unit> {
        val manager = accountManager
            ?: return Result.failure(IllegalStateException(NOT_CONFIGURED_MSG))
        _accountState.update { it.copy(syncInProgress = true, lastSyncError = null) }
        return runCatching {
            manager.syncNow(reason = SyncReason.User)
            prefs.lastSyncAtMillis = System.currentTimeMillis()
            refreshAccountState()
        }.onFailure { setError(it) }
            .also { _accountState.update { s -> s.copy(syncInProgress = false) } }
    }

    override suspend fun refreshSyncedTabs(): Result<List<SyncedTabItem>> {
        val storage = syncedTabsStorage
            ?: return Result.failure(IllegalStateException(NOT_CONFIGURED_MSG))
        return runCatching {
            storage.getSyncedDeviceTabs().flatMap { deviceTabs ->
                val device = deviceTabs.device
                deviceTabs.tabs.map { tab ->
                    val entry = tab.active()
                    SyncedTabItem(
                        deviceName = device.displayName,
                        deviceType = device.deviceType.name,
                        title = entry.title,
                        url = entry.url,
                        lastUsedMillis = tab.lastUsed,
                    )
                }
            }
        }
    }

    override suspend fun setEngineEnabled(engineName: String, enabled: Boolean): Result<Unit> {
        val manager = accountManager
            ?: return Result.failure(IllegalStateException(NOT_CONFIGURED_MSG))
        val engine = when (engineName) {
            "history" -> SyncEngine.History
            "bookmarks" -> SyncEngine.Bookmarks
            "tabs" -> SyncEngine.Tabs
            "passwords" -> SyncEngine.Passwords
            else -> return Result.failure(IllegalArgumentException("Unknown engine: $engineName"))
        }
        return runCatching { manager.setEngineEnabled(engine, enabled) }
    }

    override fun engineStates(): List<SyncEngineState> = listOf(
        SyncEngineState("history", true),
        SyncEngineState("bookmarks", true),
        SyncEngineState("tabs", true),
        SyncEngineState("passwords", true),
    )

    private fun refreshAccountState() {
        val manager = accountManager ?: return
        scope.launch {
            val profile: Profile? = runCatching { manager.accountProfile() }.getOrNull()
            val signedIn = manager.authenticatedAccount() != null
            _accountState.update {
                it.copy(
                    status = when {
                        signedIn -> AccountStatus.SIGNED_IN
                        manager.accountNeedsReauth() -> AccountStatus.NEEDS_REAUTH
                        else -> AccountStatus.SIGNED_OUT
                    },
                    email = profile?.email,
                    displayName = profile?.displayName,
                    lastSyncAtMillis = prefs.lastSyncAtMillis.takeIf { v -> v > 0 },
                )
            }
        }
    }

    private fun setError(e: Throwable) {
        _accountState.update { it.copy(lastSyncError = e.message ?: e.toString()) }
    }

    companion object {
        const val NOT_CONFIGURED_MSG =
            "FxA is not configured: add fxa.client.id and fxa.redirect.url to " +
                "local.properties (see README.md § FxA setup)."
    }
}
