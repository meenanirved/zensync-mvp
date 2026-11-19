package com.zensync.mvp.domain.model

import java.util.UUID

/** A workspace owns a set of tabs. Workspaces are LOCAL-ONLY in this MVP:
 *  Zen workspaces are not a native Firefox Sync engine. What can sync is
 *  bookmarks / history / tabs / logins via the FxA Sync engines (see
 *  sync/FxaSyncManager.kt); workspace membership itself does not sync.
 *  A future mapping could persist workspace->bookmark-folder links, but
 *  that is explicitly out of scope for the MVP (see README.md). */
data class Workspace(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val isPinnedSection: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val workspaceId: String,
    val url: String,
    val title: String = "",
    val pinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long = System.currentTimeMillis(),
)

/** A tab synced from another device (via feature-syncedtabs / RemoteTabs). */
data class SyncedTabItem(
    val deviceName: String,
    val deviceType: String,
    val title: String,
    val url: String,
    val lastUsedMillis: Long,
)

enum class AccountStatus { SIGNED_OUT, SIGNED_IN, NEEDS_REAUTH, NOT_CONFIGURED }

data class AccountState(
    val status: AccountStatus = AccountStatus.SIGNED_OUT,
    val email: String? = null,
    val displayName: String? = null,
    val lastSyncAtMillis: Long? = null,
    val lastSyncError: String? = null,
    val syncInProgress: Boolean = false,
)

data class SyncEngineState(val name: String, val enabled: Boolean)
