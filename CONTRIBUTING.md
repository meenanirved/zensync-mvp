# Contributing to ZenSync MVP

Thanks for wanting to help. This is an early MVP, so small, focused
contributions are the most useful right now.

## How to contribute

1. Fork the repo and create a branch from `main`.
2. Make your change. Keep it small and focused on one thing.
3. Make sure the project still builds: `./gradlew assembleDebug`
4. Open a pull request describing what you changed and why.

## What to work on

See `TODO.md` — the P0 items (verifying FxA sign-in and first sync on
a real device) matter most. P1/P2 items are good first issues.

## Rules

- Follow the official Kotlin code style (already set in
  `gradle.properties`).
- Don't commit `local.properties` or any credentials. FxA OAuth
  credentials go in your own uncommitted `local.properties` — see
  `local.properties.example`. Never hard-code a client id, secret,
  token, or password anywhere in the code.
- Keep the MPL-2.0 notices for Mozilla's GeckoView / Android
  Components intact. This project's own code is Apache-2.0 (see
  `LICENSE`).
- If you're fixing a bug, describe how to reproduce it in the PR.
- Be kind in reviews and issues.

## Building

See `README.md` — you need JDK 17 and the Android SDK
(`platforms;android-35`, `build-tools;35.0.0`). First build downloads
GeckoView (~230 MB) from Mozilla's Maven repo.
