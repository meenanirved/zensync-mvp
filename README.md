# ZenSync MVP

An independent Android MVP that replicates the core UX of Zen Browser on
Android and integrates Mozilla Firefox Account (FxA) Sync.

**Not affiliated with, endorsed by, or connected to Mozilla or the Zen
Browser project.** The name "ZenSync MVP" and its icon use no Mozilla or
Zen trademarks. Rendering is GeckoView; sync uses Mozilla Android
Components. See "Legal / licensing" below.

## What it does

- **GeckoView browser** (no Android WebView anywhere): address bar
  (URL or search), back / forward / reload / home, per-tab GeckoSessions.
- **Vertical tabs**: collapsible rail with favicon-less title rows (MVP),
  active state, close, pin, and a pinned/essential section.
- **Workspaces**: create / rename / delete / switch; each workspace owns
  its tabs; persisted locally in Room and restored on restart.
- **Split view**: on large screens (width > 840dp) two GeckoView panes
  side by side; on phones, pick a second tab and it is shown stacked —
  tap the ▢ icon on a tab to toggle it into split.
- **Firefox Account + Sync**: sign in / sign out via the FxA component,
  account + sync status in Settings, manual **Sync now**, periodic sync
  via WorkManager, synced tabs from other devices (feature-syncedtabs).
  Engines: bookmarks, history, tabs, logins (passwords).
- **Settings**: account/sync status, vertical-tabs toggle, clear local
  data, about/legal notes.

## Prerequisites

- Android Studio Hedgehog or newer (or command-line build, below)
- JDK 17
- Android SDK with:
  - `platforms;android-35`
  - `build-tools;35.0.0`
  - `platform-tools`
- Internet access (Gradle downloads Android Components + GeckoView from
  Mozilla's Maven repo on first build — GeckoView alone is ~230 MB)

## Build & install

```bash
# 1. (Optional, only for FxA sign-in) copy local.properties.example to
#    local.properties and fill in fxa.client.id / fxa.redirect.url.
cp local.properties.example local.properties

# 2. Build the debug APK
./gradlew assembleDebug

# 3. Install on a connected device / emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

On a machine without Android Studio, install the SDK command-line tools
and set `sdk.dir` in `local.properties`, e.g.:

```bash
# download https://dl.google.com/android/repository/commandlinetools-linux-*.zip
# unzip to $HOME/Android/cmdline-tools/latest, then:
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

## Sign in to Mozilla account & trigger Sync

1. **Register an FxA OAuth app first** (required — there is no shared
   client id/secret in this repo, by design):
   - Go to https://accounts.firefox.com/ and create an OAuth client
     (a "public" / native-app client; FxA OAuth for native apps uses no
     client secret).
   - Redirect URI must match the one in your `local.properties`, e.g.
     `zensync://fxa` — this is the deep link declared in
     `app/src/main/AndroidManifest.xml`.
   - Copy the client id into `local.properties`:
     ```
     fxa.client.id=<your client id>
     fxa.redirect.url=zensync://fxa
     ```
   - Rebuild (`./gradlew assembleDebug`) and reinstall.
2. In the app: **Settings → Sign in with Mozilla account**. The FxA web
   flow opens in your browser; after you approve, the redirect returns
   to the app and `FxaAccountManager.finishAuthentication()` exchanges
   the code. A first sync runs automatically after sign-in.
3. **Sync now**: Settings → Sync now. Success/failure is shown in
   Settings (last sync time / last sync error); it never crashes the app
   on failure — errors surface as text.
4. **Synced tabs**: tap the sync icon in the browser toolbar. Tabs from
   other signed-in devices appear after a sync; tap one to open it.
5. Periodic sync runs every 12h via WorkManager (see
   `work/SyncWorker.kt`); the component's own scheduler is also enabled
   via `SyncConfig.periodicSyncConfig`.

**If sign-in is not configured** (no client id in `local.properties`),
Settings shows "not configured" and sign-in is disabled — the browser,
tabs, workspaces and split view still work fully.

## Workspaces and Sync — read this

Zen workspaces are **not** a native Firefox Sync engine. There is no
Sync engine for workspaces, so this MVP does **not** claim workspace
sync. What syncs is what Firefox Sync syncs: bookmarks, history, tabs,
logins. Workspace membership (which tab is in which workspace, pinned
tabs, workspace names) is **local-only**, stored in Room. A future
mapping (e.g. one bookmark folder per workspace) is possible but is
explicitly out of scope — see `TODO.md`.

## Known limitations (honest list)

- **FxA end-to-end sign-in is NOT yet verified against a real OAuth
  app.** The code paths use the published Android Components 160.0
  APIs (verified against the 160.0 AARs/sources — see TODO.md), but no
  real FxA client id existed while building this MVP, so the full
  redirect → code exchange → first sync flow has not been run live.
  This is the #1 thing to test first.
- Synced-tabs device names come from `SyncedTabsStorage`; if a device
  has no display name, its client id is shown instead.
- No favicons in the tab rail yet (title + URL only); no tab search.
- No session restore across process death — tab URLs/titles persist in
  Room, but GeckoSessions are recreated and reloaded on launch.
- No private browsing, no extensions, no downloads UI, no find-in-page.
- Split view on phones is stacked/alternate only; true side-by-side
  needs a large screen.
- Logins sync is wired (`SyncableLoginsStorage`) but there is no login
  autofill UI in this MVP — logins sync in the background only.
- The periodic-sync worker and the component's own scheduler may both
  trigger syncs; see `work/SyncWorker.kt` TODO.

## Architecture

MVVM + Repository, manual DI (`di/AppContainer.kt` — chosen over Hilt
to keep the MVP's annotation processing minimal), Jetpack Compose +
Navigation Compose, Room, Coroutines/Flow, WorkManager.

```
app/src/main/java/com/zensync/mvp/
  MainActivity.kt  ZenSyncApp.kt
  browser/   BrowserSessionManager.kt   (GeckoRuntime + GeckoSession per tab)
  data/local/  AppDatabase.kt  Entities.kt  Prefs.kt
  di/        AppContainer.kt
  domain/    model/Models.kt  repo/Repositories.kt
  sync/      SyncRepository.kt  FxaSyncManager.kt
  ui/browser/  BrowserViewModel.kt  BrowserScreen.kt
  ui/settings/ SettingsViewModel.kt  SettingsScreen.kt
  ui/syncedtabs/ SyncedTabsViewModel.kt  SyncedTabsScreen.kt
  work/      SyncWorker.kt
```

## Legal / licensing

- This app is an independent demo. "Firefox", "Mozilla", "GeckoView"
  and "Zen Browser" are trademarks of their respective owners; none
  are used in the app name, package branding, or icon.
- GeckoView and Mozilla Android Components are licensed under the
  Mozilla Public License 2.0 (MPL-2.0). The app's own code is licensed
  under the Apache License 2.0 — see `LICENSE`. If you redistribute it,
  keep the MPL notices for the Mozilla components.
- Signing in uses Mozilla's Firefox Accounts / Sync services, subject
  to Mozilla's terms and privacy notice. No credentials, tokens, or
  client secrets are stored in this repository — FxA tokens live in
  the component's secure AccountStorage, and the OAuth client id is
  supplied only via your own (uncommitted) `local.properties`.
