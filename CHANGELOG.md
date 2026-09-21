# Changelog

Versions are the library's; `contractVersion` is separate and moves only when the app contract
changes incompatibly. A release that bumps one does not automatically bump the other.

## 0.1.0 — contract 1

The first version. Everything below is contract 1, which means an app declaring
`minHostContract = 1` runs on any host built against this.

### The contract (`core`)

- `SuperizerApp<C>` — one registered app: a manifest, a config spec, `setup(ctx)`, `launch(...)`.
- `AppInstance` — one open screen: `onLaunch`, `saveState`/`restore`, `onBackground`/`onForeground`,
  `Content()`, `chrome`, `onCloseRequested`, `onClose`, `dispose`.
- `AppManifest` — one serializable object holding everything static a host may want before running a
  line of an app's code: id, version, `minHostContract`, metadata, `requires`, `deepLinks`,
  `pushTopics`, `networkHosts`. The registry validates it whole; the Service Menu shows it.
- `AppConfigSpec<C>` — JSON outside, typed inside. Missing keys keep the default; a *broken* payload
  is a failure with a reason, and only then is `fallbackToDefault` applied.
- `AppRuntime` / `InstanceRuntime` — two levels: app-level from enable to disable (push, listeners,
  background work), instance-level from launch to dispose (navigation, a scope that dies with the
  screen).
- `AppRegistry`, `AppHandler`, `SuperizerEvent` — explicit registration, one open app, and a state
  machine whose every step is an event.
- `Superizer` and its ports (`HostSettingsPort`, `ActivationPort`, `RoutePort`, `DiagnosticsPort`) —
  what the shell needs from a host, declared next to the contract so that `ui` never has to depend
  on `host` and apps therefore cannot reach it.
- `Superizer.home` / `addToHome` / `removeFromHome` — Home is a chosen, ordered set, not "everything
  visible". Events `AddedToHome`, `RemovedFromHome`, and `Locked` as the pair of `Unlocked`.

### The host (`host`)

Preferences, a namespaced storage service, a Ktor network service with a 15-second timeout and no
injected credentials, a tagged logger with a 500-line ring buffer, locale and haptics that respect
the host's own settings, a system clock, process lifecycle, connectivity, an optional-service
registry, QR and promo-code activation, an unlock store, a home store with the host's first-run
defaults (`SuperizerBuilder.home(...)`), a session snapshot store, deep links in four shapes, and
push routing on a transport that is a no-op everywhere. Unlocking a hidden app — by activation or
from the Service Menu — puts it on Home; locking or resetting it takes it off and closes it. A
session snapshot is restored without `force`, so a locked app does not come back through it.

### The shell (`ui`, `ui-theme`)

Design tokens as a value rather than an object, so a dark theme is a second value. Home (chosen
tiles, a long press removes one, a "+" tile leads to the catalog), All Apps (the catalog: a tap
adds to Home or takes off), the drawer, Settings with a section per app, Activate, the Service Menu,
the app container, the host's error screen, and the veto dialog. Content is capped at 480 dp and
centred.

### Distribution

Published to GitHub Packages as `cx.m42.superizer:{core,ui-theme,ui,host,testing}` (and
`cx.m42.superizer.apps:test-app` for the bench app), for Android, desktop and wasm.

The branch decides the channel and the registry decides the number, which is Adminizer's scheme
ported to Maven: `main`/`master` publishes the last release patch bumped, `next` and `alpha`
publish `-next.N` / `-alpha.N` on their own counters, and `commit` publishes
`-commit.<sha>`. `superizer.version` and `superizer.appsVersion` in `gradle.properties` are floors
— raise one by hand to start a new series — and `scripts/resolve-version.sh` does the rest.
Nothing is published that has not passed `verify` first.

### Known limitations

- **One open app at a time.** The snapshot mechanism makes a stack cheap to add later; it is not
  added.
- **No FCM.** `PushTransport` is an `expect object` with a no-op `actual` on every target. The
  router, the topic store, the Service Menu's simulator and the bench app's Push card all work
  against it, so adding Firebase is one `actual` file and a `google-services.json`.
- **No camera for QR.** The activation contract does not depend on one: a payload is a string, and
  where the string came from is the platform's problem.
- **An exception thrown inside `Content()` cannot be caught.** A throw in the instance's coroutine
  scope is handled — the session fails, the host does not — but Compose gives no way to intercept a
  composition that throws. `AppContractTest` composes `Content()` on the default config to catch the
  obvious case.
- **`binary-compatibility-validator` is not used.** It aborts root-script evaluation silently on
  Gradle 9.5; Kotlin 2.4's built-in ABI validation (`checkLegacyAbi` / `updateLegacyAbi`) does the
  same job and understands klibs.
