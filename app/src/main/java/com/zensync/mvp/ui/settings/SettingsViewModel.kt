package com.zensync.mvp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zensync.mvp.data.local.AppDatabase
import com.zensync.mvp.data.local.Prefs
import com.zensync.mvp.domain.model.AccountState
import com.zensync.mvp.sync.SyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val syncRepository: SyncRepository,
    val prefs: Prefs,
    private val db: AppDatabase,
    private val externalScope: CoroutineScope? = null,
) : ViewModel() {

    val accountState: StateFlow<AccountState> = syncRepository.accountState
    val isConfigured: Boolean = syncRepository.isConfigured

    private val _authUrl = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val authUrl: StateFlow<String?> = _authUrl

    fun signIn() {
        viewModelScope.launch {
            syncRepository.beginSignIn()
                .onSuccess { url -> _authUrl.value = url }
                .onFailure { /* surfaced via accountState.lastSyncError */ }
        }
    }

    fun authUrlConsumed() { _authUrl.value = null }

    fun signOut() {
        viewModelScope.launch { syncRepository.signOut() }
    }

    fun syncNow() {
        viewModelScope.launch { syncRepository.syncNow() }
    }

    fun setEngineEnabled(name: String, enabled: Boolean) {
        viewModelScope.launch { syncRepository.setEngineEnabled(name, enabled) }
    }

    fun clearLocalData() {
        viewModelScope.launch {
            db.tabDao().deleteAll()
            val workspaces = db.workspaceDao().getAll()
            workspaces.forEach { db.workspaceDao().delete(it) }
            prefs.clearLocalData()
        }
    }
}
