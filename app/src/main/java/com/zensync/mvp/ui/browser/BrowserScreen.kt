package com.zensync.mvp.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.mozilla.geckoview.GeckoView

@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onOpenSettings: () -> Unit,
    onOpenSyncedTabs: () -> Unit,
) {
    val workspaces by viewModel.workspaces.collectAsState()
    val activeWorkspaceId by viewModel.activeWorkspaceId.collectAsState()
    val tabs by viewModel.tabs.collectAsState()
    val activeTabId by viewModel.activeTabId.collectAsState()
    val splitTabId by viewModel.splitTabId.collectAsState()
    val railCollapsed by viewModel.railCollapsed.collectAsState()
    val sessionStates by viewModel.sessionStates.collectAsState()

    val activeTab = tabs.firstOrNull { it.id == activeTabId }
    val activeState = activeTabId?.let { sessionStates[it] }

    LaunchedEffect(activeWorkspaceId) { viewModel.ensureHomeTab() }

    var addressText by remember { mutableStateOf("") }
    var addressFocused by remember { mutableStateOf(false) }
    LaunchedEffect(activeState?.url, addressFocused) {
        if (!addressFocused && activeState != null) addressText = activeState.url
    }

    var showNewWorkspaceDialog by remember { mutableStateOf(false) }
    var newWorkspaceName by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        // ---- Address bar / toolbar ----
        Row(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
            IconButton(onClick = { viewModel.goBack() }, enabled = activeState?.canGoBack == true) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            IconButton(onClick = { viewModel.goForward() }, enabled = activeState?.canGoForward == true) {
                Icon(Icons.Filled.ArrowForward, contentDescription = "Forward")
            }
            IconButton(onClick = { viewModel.reload() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Reload")
            }
            IconButton(onClick = { viewModel.goHome() }) {
                Icon(Icons.Filled.Home, contentDescription = "Home")
            }
            OutlinedTextField(
                value = addressText,
                onValueChange = { addressText = it },
                modifier = Modifier.weight(1f).onFocusChanged { addressFocused = it.isFocused },
                singleLine = true,
                placeholder = { Text("Search or enter address") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { viewModel.loadUrl(addressText) }),
            )
            IconButton(onClick = { viewModel.newTab() }) {
                Icon(Icons.Filled.Add, contentDescription = "New tab")
            }
            IconButton(onClick = { viewModel.toggleRailCollapsed() }) {
                Icon(Icons.Filled.Menu, contentDescription = "Toggle tab rail")
            }
            IconButton(onClick = onOpenSyncedTabs) {
                Icon(Icons.Filled.Sync, contentDescription = "Synced tabs")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings")
            }
        }
        if (activeState?.loading == true) {
            Text(
                "Loading… ${activeState.progress}%",
                modifier = Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        // ---- Body: vertical tab rail + web content ----
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val wide = maxWidth > 840.dp
            Row(modifier = Modifier.fillMaxSize()) {
                if (viewModel.prefs.verticalTabsEnabled && !railCollapsed) {
                    VerticalTabRail(
                        workspaces = workspaces,
                        activeWorkspaceId = activeWorkspaceId,
                        tabs = tabs,
                        activeTabId = activeTabId,
                        splitTabId = splitTabId,
                        onSelectWorkspace = { viewModel.switchWorkspace(it) },
                        onNewWorkspace = { showNewWorkspaceDialog = true },
                        onSelectTab = { viewModel.selectTab(it) },
                        onCloseTab = { viewModel.closeTab(it) },
                        onTogglePin = { tab -> viewModel.togglePin(tab) },
                        onToggleSplit = { viewModel.toggleSplit(it) },
                        modifier = Modifier.width(240.dp).fillMaxHeight(),
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    if (activeTab != null) {
                        if (splitTabId != null && wide) {
                            val second = tabs.firstOrNull { it.id == splitTabId }
                            Row(modifier = Modifier.fillMaxSize()) {
                                GeckoPane(viewModel, activeTab.id, activeTab.url, Modifier.weight(1f))
                                if (second != null) {
                                    GeckoPane(viewModel, second.id, second.url, Modifier.weight(1f))
                                }
                            }
                        } else {
                            GeckoPane(viewModel, activeTab.id, activeTab.url, Modifier.fillMaxSize())
                        }
                    } else {
                        Text("No tab open — tap + to open one.", modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
    }

    if (showNewWorkspaceDialog) {
        AlertDialog(
            onDismissRequest = { showNewWorkspaceDialog = false },
            title = { Text("New workspace") },
            text = {
                OutlinedTextField(
                    value = newWorkspaceName,
                    onValueChange = { newWorkspaceName = it },
                    singleLine = true,
                    placeholder = { Text("Workspace name") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.createWorkspace(newWorkspaceName)
                    newWorkspaceName = ""
                    showNewWorkspaceDialog = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewWorkspaceDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun GeckoPane(
    viewModel: BrowserViewModel,
    tabId: String,
    url: String,
    modifier: Modifier,
) {
    AndroidView(
        factory = { context -> GeckoView(context) },
        update = { view ->
            val session = viewModel.sessionManager.sessionFor(tabId, url)
            if (view.session != session) view.setSession(session)
        },
        onRelease = { view -> view.releaseSession() },
        modifier = modifier,
    )
}

@Composable
private fun VerticalTabRail(
    workspaces: List<com.zensync.mvp.domain.model.Workspace>,
    activeWorkspaceId: String?,
    tabs: List<com.zensync.mvp.domain.model.BrowserTab>,
    activeTabId: String?,
    splitTabId: String?,
    onSelectWorkspace: (String) -> Unit,
    onNewWorkspace: () -> Unit,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onTogglePin: (com.zensync.mvp.domain.model.BrowserTab) -> Unit,
    onToggleSplit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.background(Color(0xFFF2F2F2)).padding(8.dp),
    ) {
        Text("Workspaces", style = MaterialTheme.typography.labelLarge)
        workspaces.forEach { ws ->
            Text(
                text = (if (ws.id == activeWorkspaceId) "● " else "○ ") + ws.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectWorkspace(ws.id) }
                    .padding(vertical = 6.dp),
            )
        }
        TextButton(onClick = onNewWorkspace) { Text("+ New workspace") }

        val pinned = tabs.filter { it.pinned }
        if (pinned.isNotEmpty()) {
            Text("Pinned / essential", style = MaterialTheme.typography.labelLarge)
            pinned.forEach { tab ->
                TabRow(tab, tab.id == activeTabId, tab.id == splitTabId, onSelectTab, onCloseTab, onTogglePin, onToggleSplit)
            }
        }
        Text("Tabs", style = MaterialTheme.typography.labelLarge)
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(tabs.filter { !it.pinned }, key = { it.id }) { tab ->
                TabRow(tab, tab.id == activeTabId, tab.id == splitTabId, onSelectTab, onCloseTab, onTogglePin, onToggleSplit)
            }
        }
        Text(
            "Split view: tap the split icon on a tab to show it beside the active tab (large screens).",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun TabRow(
    tab: com.zensync.mvp.domain.model.BrowserTab,
    active: Boolean,
    inSplit: Boolean,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onTogglePin: (com.zensync.mvp.domain.model.BrowserTab) -> Unit,
    onToggleSplit: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (active) Color(0xFFDDE8FF) else Color.Transparent)
            .clickable { onSelectTab(tab.id) }
            .padding(6.dp),
    ) {
        Text(
            text = (tab.title.ifEmpty { tab.url }).take(28),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        IconButton(onClick = { onToggleSplit(tab.id) }) {
            Text(if (inSplit) "▣" else "▢")
        }
        IconButton(onClick = { onTogglePin(tab) }) {
            Icon(Icons.Filled.PushPin, contentDescription = if (tab.pinned) "Unpin" else "Pin")
        }
        IconButton(onClick = { onCloseTab(tab.id) }) {
            Icon(Icons.Filled.Close, contentDescription = "Close tab")
        }
    }
}
