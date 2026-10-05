# Changelog

Versions are the library's; `contractVersion` is separate and moves only when the app contract
changes incompatibly. A release that bumps one does not automatically bump the other.

## 0.5.0 — contract 4

Secret apps (Unitool notes idea/09, D248–D262): an app only a promo code opens, for one visit, and
that no screen of the host lists. Contract 4 because an older host would let a QR code unlock one
onto Home.

### The contract (`core`)

- `AppMetadata.secret`. Must come with `hidden = true` and `minHostContract = 4`, and with no deep
  links and no push topics — the registry turns any other combination away.
- `AppRegistry.isSecret(id)`, and `SuperizerApp.listed(unlocked)`: whether a person may see an app
  anywhere. `AppRegistry.visible` never returns a secret app.
- `AppHandler` writes no session snapshot for a secret app, takes no Settings block from it, and
  a `ctx.listener` hears no other app's secret events.
- `ActivationPort.canScan` / `scan()`, with defaults: the camera behind the Activate screen.

### The host

- The only door is `fromPromo`. It hands out the activation once, and `apply` opens the app only
  for that very object. The app is never unlocked and never lands on Home. `fromQr`, `activate`
  links, `app/<id>` links, pushes and `openApp` answer `UnknownApp`, as for an id that does not
  exist.
- On start, a secret app's old unlock and Home tile are forgotten (a build that turned a hidden
  app secret).
- Back after `secretBackgroundLimit` (default 5 minutes) in the background with a secret app open:
  it is closed and the shell goes Home.
- Kept out of `runtime.apps`, other apps' `runtime.events`, the backup preview, plan and report,
  and the Storage & security report. Its data still goes into the backup and comes back with any
  restore. In a release build it is also kept out of the Service Menu's events and log.
- `SuperizerBuilder.qrScanner { … }` and `secretBackgroundLimit(millis)`.
- `ActivationResult.OpenHostScreen(id)`, `SuperizerBuilder.hostScreen(screen)` and
  `Superizer.codeScreens`: a host screen nothing lists — not Settings, not the menu — that only a
  promo code opens. The Activate screen shows it with back to Home. A code for an id the host never
  registered is answered as an unknown code. (Unitool moves SSH keys out of Settings this way, behind
  the exact code `[ssh-keys]`.)
- `HashedPromoCodes(salt, table)`: the promo table with SHA-256 hashes of the codes as keys
  (`HashedPromoCodes.hash(salt, code)`), so a build carries none that `strings` would print. A host
  command goes in the same table, which takes the Service Menu's code out of `serviceCode(…)`'s
  plain string. SHA-256 is by hand in `commonMain`, checked against the standard vectors.
- `PromoCode`: one comparison rule for both tables. A code in brackets — `{ssh-keys}`,
  `[ssh-keys]` — matches only as written, with the surrounding spaces ignored. Any other code is
  normalised as before. A bracketed input that matches no exact row is also tried normalised, so
  `[SCI]` still finds `SCI`.

### The shell (`ui`)

- The Activate screen gets a "Scan a QR code" button when the host has a camera. The text goes
  through `fromQr` straight away. The promo field no longer teaches the keyboard.
- `RecentsPreview(hidden)`: keeps the screen out of the recents thumbnail without refusing
  screenshots — `setRecentsScreenshotEnabled(false)` on Android 13+, the privacy cover on iOS.
  `AppContainer` uses it for a secret app.
- Settings show neither the block nor the lock row of an app the person cannot see — this also
  covers a hidden app that is not yet unlocked. A release Service Menu does not list secret apps.

### Apps

- `SecretTestApp` (`secret-test`, code `SECRET-2026`) next to the bench app: a visit counter and
  a list of where you will not find it.

## 0.4.1 — contract 3

The person chooses where an SSH key lives (Unitool notes ssh-new 09, D237–D244). Contract 3 is
unchanged: the additions have defaults and only a host's own screens use them.

- `ImportOptions.level`: the level for an imported or generated key — 1 in the chip, 2 sealed by a
  key from it, 3 under the key's passphrase. Null takes the best the device offers, as before; a
  level the device or the key cannot have is `ImportOutcome.Failed("level.unavailable")`.
- `ImportOptions.biometricOnly`, `SshKeyInfo.biometricOnly`, `KeyringDevice.strongBiometric`: a key
  in the chip that only a strong biometric opens — a fingerprint on Android, Face ID or Touch ID on
  iOS; not the screen lock's PIN or passcode — and that a new enrolment makes unusable (D245, D246).
  Without such a biometric, `Failed("biometric.unavailable")`.
- `SshKeyInfo.passphraseKept`: a level-2 key whose own passphrase is asked for too. A host can now
  tell a key in the chip with a lock of its own from one only the screen lock guards (D247).
- Three catalogue codes: `key.level3.chip-available` (Info) — a level-3 key on a device with a chip,
  told apart from one on a device without; `key.no-second-lock` (Attention) — a key in the chip
  with neither a biometric nor its passphrase, which whoever unlocks the device can use;
  `key.biometric-only` (Ok).

## 0.4.0 — contract 3

Contracts for a host that keeps secrets, SSH keys and backups — and says honestly how (Unitool
notes ssh-new 05–07, D171–D184). The library gets the interfaces, the data and the mechanics that
work through them; it gets no cryptography, no platform code and no words for a person. Every
implementation is the host's.

### The contract (`core`)

- `AppRuntime.secrets`: a `StorageService` whose values the host seals before writing them, under
  `secret.<id>.`. `get` throws `SecretsUnavailableException` when a value is there and cannot be
  opened now — never null, which would read as "nothing stored". `set` throws when the host cannot
  seal, and has then written nothing.
- `AppRuntime.diagnostics.findings`: this app's secrets and the host-wide findings, as codes.
- `AppManifest.backup: BackupPolicy` — `All` (default), `None`, `Except(keys)`. Anything but `All`
  needs `minHostContract = 3`; the registry turns a contract-2 manifest with one away.
- `secrets`: `SecretVault` (moved from Unitool unchanged, plus `opensSilently`, default true),
  `SecretsPort` (`Superizer.secrets`: the vault, `reseal`, `counts`).
- `backup`: `HostBackupCipher`, `BackupPort` (`Superizer.backup`), `BackupBundle` and friends.
- `ssh`: `SshKeyring` (for apps: SSHSIG under a namespace, age decryption), `SshKeyAdmin` (the
  host's screens only), `SshAgent` (the ssh-agent protocol), and their data — `KeyId`,
  `SshKeyInfo`, `KeyStorage` (three levels), `KeyringDevice`, `Inspection`, `ImportOutcome`,
  `SshOutcome`, `Grant`, `UseRecord`, `MIN_PASSPHRASE = 12`. No type can hold a private key.
- `diagnostics`: `StorageDiagnostics` (`Superizer.storage`), `DiagnosticsSource`,
  `SelfTestRunner`, `DiagnosticsStrings`, the report types, and the catalogue — `DiagnosticsCode`
  (finding codes with their status and actions) and `SelfTestCode`, enums so that a host's words
  table is checked for completeness by the compiler.
- `FilePicker` (optional service `file-picker`) and `PickedFile`.
- `HostSection`, `HostScreen`, `HomeBanner`: slots for the host's own screens; and
  `Superizer.hostSections`, `Superizer.homeBanners`.

### The host (`host`)

- `HostSecrets`: seals with the vault the builder was given, or writes `plain:` when there is none;
  `reseal` moves every secret to another vault all or nothing. Reset (D35) erases secrets too.
- `DiagnosticsHub`: the host's own findings (`vault.missing`, the heartbeat, each app's secrets
  sealed or plain, the last backup, the last self-test) plus every registered source; the
  self-test once per install and per new host version; `exportText()` in English. The heartbeat
  never opens a vault that would have to ask.
- `HostBackup`: walks `app.<id>.` and `secret.<id>.` without reading a format, honours each
  manifest's policy, opens secrets with the vault and seals them again with the next device's,
  and carries key names but never keys. A bundle already opened on a computer is read as is.
- `SuperizerBuilder`: `service(key) { caller -> }` (a service bound to the app that asks for it),
  `secretVault`, `backupCipher`, `backupKeys`, `diagnosticsSource`, `selfTest`, `hostSection`,
  `homeBanner`.

### The shell (`ui`)

- `ShellDestination.Host(screen, from)`: the shell's bar and back button around a host screen,
  `FLAG_SECURE` while `screen.secure`.
- Settings draws the host's sections after Protection and before the apps'
  (`settings:host:<id>`); Home draws the host's banners above the tiles.
- `LocalDiagnosticsStrings`, raw codes by default.

### Testing (`testing`)

- `FakeSecretVault`, `FakeSecrets` (`FakeAppRuntime.secrets`), `FakeAppDiagnostics`,
  `FakeStorageDiagnostics`, `FakeSshKeyring`, `FakeFilePicker`.
- `Canary` (masked, so a heap dump finds only the code under test's copies) and `LeakScanner`:
  raw, Base64 at every alignment in both alphabets, hex, UTF-16LE and every 16-byte window.

### The bench app

- Contract 3: a Secrets card and an SSH keyring card that signs under `test-app@superizer`.

## 0.3.0 — contract 2

Two things: iOS, and protected apps.

### iOS

Every published module now has `iosArm64` and `iosSimulatorArm64` targets. The klibs compile and
publish from any host, since Kotlin 2.4 cross-compiles Apple targets. Linking an app framework and
running the iOS tests need a Mac. `Platform.Ios` is new in contract 2.

- `host`:
  - `NSUserDefaults` in a suite of the host's own (`cx.m42.superizer.store`);
  - Ktor's Darwin engine;
  - `didEnterBackground`/`willEnterForeground` as the lifecycle, deliberately not resign-active;
  - `NWPathMonitor` for connectivity;
  - the Taptic Engine for haptics;
  - `NSLocale.preferredLanguages` for the language.
- The lock:
  - `LocalAuthentication` with `deviceOwnerAuthentication`, which is Face ID or Touch ID with the
    passcode as fallback;
  - "the phone locked" is `protectedDataWillBecomeUnavailable`;
  - a missing `NSFaceIDUsageDescription` crashes a debug build and disables the lock in a release.
- Push is FCM, as on Android. The Firebase iOS SDK lives in the app's Swift target. The app hands
  `PushTransport.messaging` (`IosMessaging`, for topics) in at launch, then calls
  `PushTransport.deliverToken` from its `MessagingDelegate`, plus `deliverTap` and `deliverMessage`.
  Without Firebase configured, push is inert.
- `ui`: `SecureWindow` puts a plain view over the window while the app is resigning active, so the
  app-switcher snapshot shows nothing, and keeps it there during screen recording, mirroring and
  AirPlay (`UIScreen.isCaptured`). iOS gives an app no way to refuse a screenshot, and this does not
  pretend to. `SystemBackHandler` is a no-op, since iOS has no system back.

### Protected apps

Protected apps: an app declares that its screen is its owner's business, and the host locks it
behind the device's own biometrics or screen lock (Unitool note 06, D127–D142). Contract 2, because
a contract-1 host has never heard of the declaration and would show such an app unlocked.

### The contract (`core`)

- `AppManifest.protection: AppProtection(lock, secureWindow)`, with `LockPolicy` `Off`,
  `OptionalOff`, `OptionalOn`, `Required`. The registry rejects a manifest that declares protection
  but asks for contract 1, so the mistake fails in the app's first test.
- `Superizer.lock: AppLockPort` and `LockState` — what the shell draws the curtain, the banner and
  the Settings section from. One unlock for the whole host, in memory only.
- `DeviceAuthenticator`, `AuthStrength`, `AuthAvailability`, `AuthOutcome` — the platform's "is this
  the owner?", implemented by the host. `AuthStrength.Strong` is in the contract already so that
  binding a vault key to authentication later needs no contract 3.
- `UserPresence` (optional service `user-presence`) — an app asks the person to confirm one action.
  True with no screen lock, because there is nothing to ask with and the banner already says so.
- A protected app's config never reaches an event (`AppConfig.Redacted` instead) or the session
  snapshot (empty instead); a rejected config's reason is not quoted. Its links lose their query in
  `RouteDiscarded`.

### The host (`host`)

- `AppLockController`: locks at once when the screen goes off, after the grace (a minute by
  default) away in another app, and on every process start. Lifecycle transitions while its own
  sheet is up are recorded, not acted on — the API 24–29 PIN screen is another activity — and
  judged when the sheet closes. Grace and the person's switches are stored under `host.lock`.
- `SuperizerBuilder.deviceAuthenticator(…)` and `.lockStrength(…)`. The default on Android is
  `BiometricPrompt` with `BIOMETRIC_WEAK or DEVICE_CREDENTIAL`; the host's activity must be a
  `FragmentActivity`, and a debug build crashes on the first frame if it is not. Desktop and the web
  have none and say so.
- `PlatformScreen` — `ACTION_SCREEN_OFF` and `PowerManager.isInteractive` on Android.
- New Android dependencies: `androidx.biometric:biometric:1.1.0`, `androidx.fragment:fragment` (api).

### The shell (`ui`)

- The curtain in the app container — the one place `Content()` is drawn, so every road onto the
  screen is covered by one branch. It asks once by itself, then offers a button; the bar shows the
  app's name, not its current title.
- The "not protected" banner over an app that wants a lock the device cannot give.
- `FLAG_SECURE` while a `secureWindow` app is on screen, curtain included (`SecureWindow`).
- Settings → Protection: grace, a switch per optional app, a line per required one, "Lock now". A
  change that weakens the lock asks first. A covered app's own Settings block is not drawn.
- `AppTextField(keyboardOptions)`, `ToggleRow(modifier, enabled)`.
- The link route filters nulls before `collectLatest`, so consuming a link no longer cancels the
  launch it was consumed for.

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
