package com.zensync.mvp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zensync.mvp.di.AppContainer
import com.zensync.mvp.ui.browser.BrowserScreen
import com.zensync.mvp.ui.settings.SettingsScreen
import com.zensync.mvp.ui.syncedtabs.SyncedTabsScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleDeepLink(intent)
        setContent {
            MaterialTheme {
                val container = remember { (application as ZenSyncApp).container }
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "browser") {
                    composable("browser") {
                        val vm = remember { container.browserViewModel() }
                        BrowserScreen(
                            viewModel = vm,
                            onOpenSettings = { navController.navigate("settings") },
                            onOpenSyncedTabs = { navController.navigate("syncedtabs") },
                        )
                    }
                    composable("settings") {
                        val vm = remember { container.settingsViewModel() }
                        SettingsScreen(viewModel = vm, onBack = { navController.popBackStack() })
                    }
                    composable("syncedtabs") {
                        val vm = remember { container.syncedTabsViewModel() }
                        SyncedTabsScreen(
                            viewModel = vm,
                            onOpenTab = { url ->
                                // Open the synced tab in the current workspace.
                                val browserVm = container.browserViewModel()
                                browserVm.newTab(url)
                                navController.popBackStack()
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    /**
     * FxA OAuth redirect: zensync://fxa?code=…&state=…
     * The code is exchanged by FxaAccountManager.finishAuthentication().
     * TODO: verify the exact redirect handling against a real FxA OAuth app;
     * some FxA flows return the code in the fragment or via a webchannel
     * instead — see README.md § FxA setup.
     */
    private fun handleDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "zensync") return
        val code = uri.getQueryParameter("code") ?: return
        val state = uri.getQueryParameter("state") ?: return
        val container = AppContainer.get(this)
        scope.launch {
            container.syncRepository.finishSignIn(code, state)
        }
    }
}
