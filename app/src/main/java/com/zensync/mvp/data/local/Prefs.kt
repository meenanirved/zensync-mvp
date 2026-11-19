package com.zensync.mvp.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Plain prefs for UI settings; ENCRYPTED prefs for anything token-like.
 * FxA tokens themselves live inside the Mozilla component's own secure
 * storage (AccountStorage); this encrypted store is for our own small
 * secrets (e.g. the last sync timestamp is NOT secret — it lives in plain
 * prefs) and must never hold a hard-coded client secret (there is none:
 * FxA OAuth for a public client uses no client secret).
 */
class Prefs(context: Context) {
    private val plain: SharedPreferences =
        context.getSharedPreferences("zensync_prefs", Context.MODE_PRIVATE)

    private val encrypted: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "zensync_encrypted_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var verticalTabsEnabled: Boolean
        get() = plain.getBoolean("vertical_tabs_enabled", true)
        set(v) = plain.edit().putBoolean("vertical_tabs_enabled", v).apply()

    var activeWorkspaceId: String?
        get() = plain.getString("active_workspace_id", null)
        set(v) = plain.edit().putString("active_workspace_id", v).apply()

    var activeTabId: String?
        get() = plain.getString("active_tab_id", null)
        set(v) = plain.edit().putString("active_tab_id", v).apply()

    var splitViewEnabled: Boolean
        get() = plain.getBoolean("split_view_enabled", false)
        set(v) = plain.edit().putBoolean("split_view_enabled", v).apply()

    var lastSyncAtMillis: Long
        get() = plain.getLong("last_sync_at", 0L)
        set(v) = plain.edit().putLong("last_sync_at", v).apply()

    fun clearLocalData() {
        plain.edit().clear().apply()
        encrypted.edit().clear().apply()
    }
}
