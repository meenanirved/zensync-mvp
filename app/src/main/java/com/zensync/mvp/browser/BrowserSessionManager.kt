package com.zensync.mvp.browser

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings

data class TabSessionState(
    val tabId: String,
    val url: String = "",
    val title: String = "",
    val progress: Int = 0,
    val loading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
)

/**
 * Owns one [GeckoSession] per tab, backed by a single shared [GeckoRuntime].
 * Rendering is GeckoView only — there is no android.webkit.WebView anywhere.
 *
 * NOTE: sessions are created lazily and kept in memory for the process
 * lifetime in this MVP. Session restore across process death is a TODO
 * (see TODO.md); tab URLs/titles persist in Room via TabRepository.
 */
class BrowserSessionManager(private val context: Context) {

    private val runtime: GeckoRuntime by lazy { GeckoRuntime.create(context) }

    private val sessions = mutableMapOf<String, GeckoSession>()
    private val _states = MutableStateFlow<Map<String, TabSessionState>>(emptyMap())
    val states: StateFlow<Map<String, TabSessionState>> = _states.asStateFlow()

    fun sessionFor(tabId: String, initialUrl: String): GeckoSession {
        return sessions.getOrPut(tabId) {
            val settings = GeckoSessionSettings.Builder()
                .usePrivateMode(false)
                .useTrackingProtection(true)
                .allowJavascript(true)
                .build()
            val session = GeckoSession(settings)
            session.navigationDelegate = object : GeckoSession.NavigationDelegate {
                override fun onLocationChange(
                    session: GeckoSession,
                    url: String?,
                    perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                    isSameDocument: Boolean?,
                ) {
                    update(tabId) { it.copy(url = url.orEmpty()) }
                }

                override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                    update(tabId) { it.copy(canGoBack = canGoBack) }
                }

                override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                    update(tabId) { it.copy(canGoForward = canGoForward) }
                }
            }
            // Title changes arrive via ContentDelegate, not NavigationDelegate.
            session.contentDelegate = object : GeckoSession.ContentDelegate {
                override fun onTitleChange(session: GeckoSession, title: String?) {
                    update(tabId) { it.copy(title = title.orEmpty()) }
                }
            }
            session.progressDelegate = object : GeckoSession.ProgressDelegate {
                override fun onProgressChange(session: GeckoSession, progress: Int) {
                    update(tabId) { it.copy(progress = progress, loading = progress < 100) }
                }

                override fun onPageStart(session: GeckoSession, url: String) {
                    update(tabId) { it.copy(loading = true, url = url) }
                }

                override fun onPageStop(session: GeckoSession, success: Boolean) {
                    update(tabId) { it.copy(loading = false, progress = 100) }
                }
            }
            _states.update { it + (tabId to TabSessionState(tabId = tabId, url = initialUrl)) }
            session.open(runtime)
            session.loadUri(initialUrl)
            session
        }
    }

    fun loadUrl(tabId: String, url: String) {
        sessions[tabId]?.loadUri(url)
    }

    fun goBack(tabId: String) { sessions[tabId]?.goBack() }
    fun goForward(tabId: String) { sessions[tabId]?.goForward() }
    fun reload(tabId: String) { sessions[tabId]?.reload() }

    fun close(tabId: String) {
        sessions.remove(tabId)?.close()
        _states.update { it - tabId }
    }

    fun stateFor(tabId: String): TabSessionState? = _states.value[tabId]

    private fun update(tabId: String, f: (TabSessionState) -> TabSessionState) {
        _states.update { map ->
            val cur = map[tabId] ?: TabSessionState(tabId)
            map + (tabId to f(cur))
        }
    }
}
