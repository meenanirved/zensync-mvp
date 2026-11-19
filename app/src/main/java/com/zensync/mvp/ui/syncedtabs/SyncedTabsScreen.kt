package com.zensync.mvp.ui.syncedtabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SyncedTabsScreen(
    viewModel: SyncedTabsViewModel,
    onOpenTab: (String) -> Unit,
    onBack: () -> Unit,
) {
    val tabs by viewModel.tabs.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Tabs from other devices", style = MaterialTheme.typography.headlineSmall)
        Text(
            "From Firefox Sync (feature-syncedtabs). Shown only when signed in " +
                "and another device has synced tabs.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(onClick = { viewModel.refresh() }, modifier = Modifier.padding(top = 8.dp)) {
            Text(if (loading) "Refreshing…" else "Refresh")
        }
        error?.let { Text("Error: $it", modifier = Modifier.padding(top = 8.dp)) }
        if (!loading && tabs.isEmpty() && error == null) {
            Text("No synced tabs found.", modifier = Modifier.padding(top = 16.dp))
        }
        LazyColumn {
            items(tabs, key = { it.deviceName + it.url }) { tab ->
                Column(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenTab(tab.url) }.padding(vertical = 8.dp),
                ) {
                    Text(tab.title.ifEmpty { tab.url }, style = MaterialTheme.typography.bodyLarge)
                    Text(tab.url, style = MaterialTheme.typography.bodySmall)
                    Text("${tab.deviceName} • ${tab.deviceType}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) { Text("Back to browser") }
    }
}
