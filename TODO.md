# TODO — prioritised

## P0 — verify before calling this "working"
1. **FxA end-to-end sign-in.** Register a real FxA OAuth app, fill in
   `local.properties`, install, sign in, confirm the `zensync://fxa`
   redirect returns `code`+`state` and `finishAuthentication()` succeeds.
   If FxA returns the code in the URL fragment instead of the query,
   update `MainActivity.handleDeepLink()` accordingly.
2. **First sync + synced tabs.** After sign-in, check Settings shows
   your email and a last-sync time; open the synced-tabs screen from a
   second device and confirm tabs appear.
3. **Run on a real device.** `./gradlew assembleDebug`, install, open a
   URL, create/close tabs, hide/show the rail, create/switch workspaces,
   kill and relaunch the app — workspace state must survive.

## P1 — product gaps
4. Favicons in the vertical tab rail (GeckoSession icon delegate).
5. GeckoSession state save/restore across process death.
6. Engine enabled/disabled state: read it back from
   `SyncEnginesStorage` instead of the MVP's static defaults in
   Settings (see SettingsScreen.kt).
7. Decide on the double periodic sync: keep either `SyncWorker` or the
   component's `SyncConfig.periodicSyncConfig` scheduler, not both.
8. Login autofill UI (GeckoView login storage delegate) so the synced
   logins engine is user-visible.

## P2 — nice to have
9. Tab search, tab groups, workspace rename/delete UI in the rail
   (rename/delete exist in the ViewModel; wire long-press menus).
10. Private browsing mode, downloads, find-in-page.
11. Workspace → bookmark-folder mapping (opt-in), so workspace
    *contents* can travel via the bookmarks engine. Document that this
    is a mapping, not native workspace sync.

## API verification notes (done 2026-11-17)
- Pinned Android Components **160.0** (stable; 161.0b4 was the latest
  on Maven at the time but is a beta) and GeckoView
  **160.0.20261104181552**, verified via `maven-metadata.xml` and by
  downloading the AARs from https://maven.mozilla.org/maven2/.
- `FxaAccountManager`, `FxaConfig`/`FxaServer.Release`,
  `beginAuthentication(entrypoint, service)`,
  `finishAuthentication(FxaAuthData)`, `syncNow(SyncReason.User)`,
  `GlobalSyncableStoreProvider.configureStore`,
  `SyncedTabsStorage.getSyncedDeviceTabs()`,
  `PlacesBookmarksStorage` / `PlacesHistoryStorage` /
  `RemoteTabsStorage` / `SyncableLoginsStorage` signatures were all
  checked with `javap` against the 160.0 AARs and, for
  `FxaAccountManager`, against the published 160.0 sources jar.
- Official references: https://github.com/mozilla-mobile/android-components
  (samples: `samples/sync`), https://firefox-source-docs.mozilla.org/
  (GeckoView), https://mozilla.github.io/application-services/
  (FxA / Sync).
