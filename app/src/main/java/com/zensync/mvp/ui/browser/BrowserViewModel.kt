package com.zensync.mvp.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zensync.mvp.browser.BrowserSessionManager
import com.zensync.mvp.data.local.Prefs
import com.zensync.mvp.domain.model.BrowserTab
import com.zensync.mvp.domain.model.Workspace
import com.zensync.mvp.domain.repo.TabRepository
import com.zensync.mvp.domain.repo.WorkspaceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BrowserViewModel(
    private val workspaceRepository: WorkspaceRepository,
    private val tabRepository: TabRepository,
    val sessionManager: BrowserSessionManager,
    val prefs: Prefs,
    private val externalScope: CoroutineScope? = null,
) : ViewModel() {

    val workspaces: StateFlow<List<Workspace>> =
        workspaceRepository.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _activeWorkspaceId = MutableStateFlow<String?>(prefs.activeWorkspaceId)
    val activeWorkspaceId: StateFlow<String?> = _activeWorkspaceId.asStateFlow()

    val tabs: StateFlow<List<BrowserTab>> =
        _activeWorkspaceId.flatMapLatest { wsId ->
            if (wsId == null) flowOf(emptyList()) else tabRepository.observeForWorkspace(wsId)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _activeTabId = MutableStateFlow<String?>(prefs.activeTabId)
    val activeTabId: StateFlow<String?> = _activeTabId.asStateFlow()

    private val _splitTabId = MutableStateFlow<String?>(null)
    val splitTabId: StateFlow<String?> = _splitTabId.asStateFlow()

    private val _railCollapsed = MutableStateFlow(false)
    val railCollapsed: StateFlow<Boolean> = _railCollapsed.asStateFlow()

    val sessionStates = sessionManager.states

    init {
        viewModelScope.launch {
            val defaultWs = workspaceRepository.ensureDefault()
            if (_activeWorkspaceId.value == null) {
                _activeWorkspaceId.value = defaultWs.id
                prefs.activeWorkspaceId = defaultWs.id
            }
            // Open a home tab if the active workspace has none.
            // (Tabs are loaded from Room via the tabs flow; the UI creates the
            // first tab on demand — see ensureHomeTab().)
        }
    }

    fun ensureHomeTab() {
        viewModelScope.launch {
            val wsId = _activeWorkspaceId.value ?: return@launch
            val existing = tabs.value
            if (existing.isEmpty()) {
                val tab = tabRepository.create(wsId, HOME_URL)
                selectTab(tab.id)
            } else if (_activeTabId.value == null) {
                selectTab(existing.first().id)
            }
        }
    }

    fun newTab(url: String = HOME_URL) {
        viewModelScope.launch {
            val wsId = _activeWorkspaceId.value ?: return@launch
            val tab = tabRepository.create(wsId, normalizeUrl(url))
            selectTab(tab.id)
        }
    }

    fun selectTab(tabId: String) {
        _activeTabId.value = tabId
        prefs.activeTabId = tabId
        viewModelScope.launch {
            val tab = tabs.value.firstOrNull { it.id == tabId } ?: return@launch
            tabRepository.update(tab.copy(lastActiveAt = System.currentTimeMillis()))
        }
    }

    fun closeTab(tabId: String) {
        viewModelScope.launch {
            val tab = tabs.value.firstOrNull { it.id == tabId } ?: return@launch
            sessionManager.close(tabId)
            tabRepository.delete(tab)
            if (_splitTabId.value == tabId) _splitTabId.value = null
            if (_activeTabId.value == tabId) {
                _activeTabId.value = tabs.value.firstOrNull { it.id != tabId }?.id
                prefs.activeTabId = _activeTabId.value
            }
        }
    }

    fun togglePin(tab: BrowserTab) {
        viewModelScope.launch { tabRepository.update(tab.copy(pinned = !tab.pinned)) }
    }

    fun loadUrl(rawInput: String) {
        val tabId = _activeTabId.value ?: return
        val url = normalizeUrl(rawInput)
        sessionManager.loadUrl(tabId, url)
        viewModelScope.launch {
            val tab = tabs.value.firstOrNull { it.id == tabId } ?: return@launch
            tabRepository.update(tab.copy(url = url))
        }
    }

    fun goBack() = _activeTabId.value?.let { sessionManager.goBack(it) }
    fun goForward() = _activeTabId.value?.let { sessionManager.goForward(it) }
    fun reload() = _activeTabId.value?.let { sessionManager.reload(it) }
    fun goHome() = _activeTabId.value?.let {
        sessionManager.loadUrl(it, HOME_URL)
    }

    fun switchWorkspace(workspaceId: String) {
        _activeWorkspaceId.value = workspaceId
        prefs.activeWorkspaceId = workspaceId
        _activeTabId.value = null
        prefs.activeTabId = null
        _splitTabId.value = null
    }

    fun createWorkspace(name: String) {
        viewModelScope.launch {
            val ws = workspaceRepository.create(name)
            switchWorkspace(ws.id)
        }
    }

    fun renameWorkspace(workspace: Workspace, newName: String) {
        viewModelScope.launch { workspaceRepository.rename(workspace, newName) }
    }

    fun deleteWorkspace(workspace: Workspace) {
        viewModelScope.launch {
            val all = workspaces.value
            if (all.size <= 1) return@launch // Keep at least one workspace.
            workspaceRepository.delete(workspace)
            if (_activeWorkspaceId.value == workspace.id) {
                switchWorkspace(all.first { it.id != workspace.id }.id)
            }
        }
    }

    fun toggleRailCollapsed() { _railCollapsed.update { !it } }

    /** Split view: second tab shown side-by-side on large screens. */
    fun toggleSplit(tabId: String) {
        _splitTabId.value = if (_splitTabId.value == tabId) null else tabId
        prefs.splitViewEnabled = _splitTabId.value != null
    }

    /** Normalises address-bar input: URL, or a search query. */
    fun normalizeUrl(input: String): String {
        val text = input.trim()
        if (text.isEmpty()) return HOME_URL
        if (text.contains(" ") || (!text.contains(".") && !text.startsWith("http"))) {
            return "https://duckduckgo.com/?q=" + java.net.URLEncoder.encode(text, "UTF-8")
        }
        return if (text.startsWith("http://") || text.startsWith("https://")) text else "https://$text"
    }

    companion object {
        const val HOME_URL = "https://www.mozilla.org/"
    }
}
