package com.zensync.mvp.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zensync.mvp.domain.model.AccountStatus
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val state by viewModel.accountState.collectAsState()
    val authUrl by viewModel.authUrl.collectAsState()
    var verticalTabs by remember { mutableStateOf(viewModel.prefs.verticalTabsEnabled) }
    var showClearConfirm by remember { mutableStateOf(false) }

    // Open the FxA auth URL in the user's browser / Custom Tab.
    LaunchedEffect(authUrl) {
        authUrl?.let { url ->
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            viewModel.authUrlConsumed()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Text("Settings", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        Text("Account", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        when (state.status) {
            AccountStatus.NOT_CONFIGURED -> Text(
                "Mozilla account sign-in is not configured. Add fxa.client.id and " +
                    "fxa.redirect.url to local.properties (see README.md § FxA setup) " +
                    "and rebuild. No account data is stored until then.",
            )
            AccountStatus.SIGNED_IN -> {
                Text("Signed in as ${state.displayName ?: state.email ?: "Mozilla account"}")
                state.email?.let { Text(it) }
            }
            AccountStatus.NEEDS_REAUTH -> Text("Signed in, but re-authentication is needed.")
            AccountStatus.SIGNED_OUT -> Text("Not signed in.")
        }
        state.lastSyncAtMillis?.let {
            Text("Last sync: ${DateFormat.getDateTimeInstance().format(Date(it))}")
        }
        state.lastSyncError?.let { Text("Last sync error: $it") }
        if (state.syncInProgress) Text("Syncing…")

        Row(modifier = Modifier.padding(top = 8.dp)) {
            if (state.status == AccountStatus.SIGNED_IN || state.status == AccountStatus.NEEDS_REAUTH) {
                OutlinedButton(onClick = { viewModel.signOut() }) { Text("Sign out") }
            } else if (viewModel.isConfigured) {
                Button(onClick = { viewModel.signIn() }) { Text("Sign in with Mozilla account") }
            }
            Button(onClick = { viewModel.syncNow() }, modifier = Modifier.padding(start = 8.dp)) {
                Text("Sync now")
            }
        }

        Text("Sync engines", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        Text("Bookmarks, history, tabs and logins sync via the Mozilla Sync engines. " +
            "Workspaces do NOT sync — they are local-only (see About).")
        // Engine toggles are wired to FxaAccountManager.setEngineEnabled.
        // (Engine state is read from the manager on demand; the MVP shows the
        // four engines as enabled by default — see TODO.md.)
        listOf("history", "bookmarks", "tabs", "passwords").forEach { engine ->
            var enabled by remember { mutableStateOf(true) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(engine, modifier = Modifier.weight(1f))
                Switch(
                    checked = enabled,
                    onCheckedChange = { enabled = it; viewModel.setEngineEnabled(engine, it) },
                )
            }
        }

        Text("Appearance", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Vertical tabs", modifier = Modifier.weight(1f))
            Switch(
                checked = verticalTabs,
                onCheckedChange = { verticalTabs = it; viewModel.prefs.verticalTabsEnabled = it },
            )
        }

        Text("Data", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        OutlinedButton(onClick = { showClearConfirm = true }) { Text("Clear local data") }

        Text("About / legal", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        Text(
            "ZenSync MVP is an independent demo app. It is not affiliated with, " +
                "endorsed by, or connected to Mozilla or the Zen Browser project, and " +
                "uses no Mozilla or Zen trademarks in its name or icon. Rendering is " +
                "GeckoView (Mozilla Public License 2.0); Android Components are also " +
                "MPL-2.0. Firefox Account / Sync use Mozilla's services under " +
                "Mozilla's terms. Source and licence notes are in README.md.",
        )
        OutlinedButton(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) { Text("Back to browser") }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear local data?") },
            text = { Text("Deletes all local tabs and workspaces. Synced data on Mozilla's servers is not affected.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearLocalData(); showClearConfirm = false }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            },
        )
    }
}
