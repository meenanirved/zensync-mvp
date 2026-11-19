package com.zensync.mvp.ui.syncedtabs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zensync.mvp.domain.model.SyncedTabItem
import com.zensync.mvp.sync.SyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SyncedTabsViewModel(private val syncRepository: SyncRepository) : ViewModel() {
    private val _tabs = MutableStateFlow<List<SyncedTabItem>>(emptyList())
    val tabs: StateFlow<List<SyncedTabItem>> = _tabs.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            syncRepository.refreshSyncedTabs()
                .onSuccess { _tabs.value = it; _error.value = null }
                .onFailure { _error.value = it.message }
            _loading.value = false
        }
    }
}
