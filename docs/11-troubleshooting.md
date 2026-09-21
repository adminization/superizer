# Troubleshooting

Every failure the framework can report, and what it actually means. Most of them are visible in the
Service Menu — the event log and the manifest list are there for exactly this.

---

## The app does not appear at all

| Symptom | Cause | Fix |
|---|---|---|
| Not on Home, but in All Apps | Home is a **chosen** set. A fresh install shows `home(...)`; after that it is the user's. | `home("your-id")` in the builder, or add it from the catalog. An existing install will not change — the store already has a list. |
| Not in All Apps either | `metadata.hidden = true` and nothing has unlocked it | activate it (promo code / QR), or Service Menu → unlock |
| Nowhere, and no event | `register(...)` was never called | the builder is the inventory; there is no classpath scanning |
| `IllegalArgumentException: duplicate app id` | two apps answer to one name | a build mistake, thrown on purpose |
| `IllegalArgumentException: AppId must be kebab-case` | `AppId("MyApp")` | `[a-z0-9][a-z0-9-]*` |

## `RegistrationRejected`

The manifest does not fit this host. The Service Menu lists the manifest with the reason.

| Reason | Meaning |
|---|---|
| `needs host contract N, this host is M` | `minHostContract` is newer than `HostInfo.contractVersion`. Upgrade the host, or lower the requirement if the app really does run on the older contract. |
| `host provides no <key>` | `manifest.requires` names a `ServiceKey` the host did not `service(key, impl)`. Either provide it, or drop it from `requires` and handle `service(key) == null`. |
| `malformed deep-link path: '…'` | must be `[a-z0-9][a-z0-9-]*(/[a-z0-9][a-z0-9-]*)*` — no query, no scheme. `rate?pair=X` is wrong; the query is data. |
| `malformed push topic: '…'` | must be `[A-Za-z0-9][A-Za-z0-9._-]*` |

## `SetupMismatch` — the app stays disabled

`setup()` registered a deep link the manifest does not declare. The manifest is the declaration;
`setup` is only the wiring, and a manifest that can be wrong is a manifest nobody trusts.

Add the path to `manifest.deepLinks`, or stop registering it. (Declared-but-not-registered is only
a warning — a path may be wired conditionally.)

## `LaunchFailed`

| Reason text | Cause |
|---|---|
| `setup failed: …` | `setup()` threw. It runs before any screen exists; keep it cheap and total. |
| `unknown app <id>` | nothing registered under that id |
| `hidden app <id> is not unlocked` | a launch without `force` for a locked hidden app — including a restored session, deliberately |
| `app <id> could not be enabled` | a previous `SetupMismatch` or a `setup` throw |
| `config rejected: …` | the payload did not decode **and** `fallbackToDefault = false` |
| `launch() threw: …` | your `launch(runtime, config)` threw. It should only build a state holder. |
| `onLaunch() threw: …` | the instance is disposed and the host shows its error screen |

The user sees `HostErrorScreen` with Retry and Back. Retry relaunches with `force`.

## `ConfigRejected`

The payload did not decode. Reporting is not optional; recovering is — `fallbackToDefault` decides
whether the launch continues on defaults or fails.

Common causes: a field's type changed without a `migrate`; a QR code from a newer build carrying a
shape this version does not know; an object where a string belongs. Unknown *keys* are never a
problem — the decoder ignores them on purpose.

## The push never arrives — `PushDropped`

| Reason | Meaning |
|---|---|
| `UnsupportedSchema` | `data["schemaVersion"]` is not the string `"1"` |
| `UnknownApp` | `appId` missing, malformed, or not registered |
| `Locked` | the target is hidden and not unlocked. **A push never unlocks.** |
| `Disabled` | the app is registered but not enabled (a `SetupMismatch`, usually) |
| `LinkMismatch` | `link` does not start with `<scheme>://app/<appId>` — an app may not be sent to another app's screen |

And when nothing is dropped but nothing happens either:

- `subscribe("x")` was refused because `x` is not in `manifest.pushTopics` — look for the warning in
  the log.
- You collected `push.messages` on the **instance** scope, and the app was closed. Collect on
  `ctx.runtime.scope` in `setup` for messages that must arrive with no screen open.
- `PushTransport` is a no-op on every target today. Use `diagnostics.simulatePush(...)` to drive the
  chain end to end.

## The deep link opens the app but does nothing

- `DeepLinkUnmatched` in the log: the app serves no such path, so it opened on an empty config.
  Deliberate — a link from a newer build should not be a dead end.
- The path was registered but the params were not what you expected: repeated query keys keep the
  **first**, and values are percent-decoded.
- Nothing at all happened: the link never reached `route.deliver(...)`. On Android check
  `launchMode="singleTask"`, the `VIEW` filter, and that `onNewIntent` also delivers.
- `RouteDiscarded`: the route arrived, the open app vetoed its close, and the user chose to stay.

## The screen does not come back after a restart

- Snapshots expire after **24 hours**.
- `saveState()` returned `null`.
- The app was locked or reset in between — a restore never uses `force`.
- A snapshot written by an older build no longer parses, which means the same as no snapshot.
- On Android, the snapshot is written on `ON_STOP`. A crash with no stop writes nothing.

## Storage reads nothing back

- Android: `initPrefs(context)` was not called before the first access.
- Web: the browser is blocking site data. `SafePrefs` swallows the failure by design — a store
  nobody can write to is a preference nobody set, not a crash.
- Desktop: someone called `PrefsStorage.useDirectory(...)` (a test does) and the process is reading
  a different file.
- `DiagnosticsPort.reset(id)` erased the namespace.

## The build fails on a rule, not a compile error

| Message | Meaning |
|---|---|
| `Dependency rules (02) violated: … must not depend on libs.superizer.host` | an app reached for the host module. Whatever you wanted is either on `AppRuntime` already, or belongs behind a `ServiceKey`. |
| `… must not depend on :apps:other` | apps do not know about each other. Use `navigation.openApp` and a config. |
| `ABI dumps are stale` | a public API changed. `./gradlew updateLegacyAbi` and commit the diff — **that diff is the review of the contract.** |
| The TCK fails `launchingIsCheapAndTouchesNoNetwork…` | `onLaunch` fetched, or blocked. Move it to `Content()`'s `LaunchedEffect` or `runtime.scope`. |
| The TCK fails `aSnapshotSurvivesARoundTrip` | `restore(saveState())` does not reproduce the same snapshot. Usually a field saved but not restored. |
| The TCK fails `setupRegistersOnlyWhatTheManifestDeclares` | see `SetupMismatch` above |

## Gradle and toolchain

| Symptom | Fix |
|---|---|
| `Could not find cx.m42.superizer:core:0.1.0` | `./gradlew publishToMavenLocal` in the library, and `mavenLocal()` in the consumer's `dependencyResolutionManagement` — or `includeBuild("../superizer")` |
| GitHub Packages returns 401 on **read** | it authenticates downloads even for a public package; set `gpr.user` / `gpr.key` in `~/.gradle/gradle.properties` (see [Getting started §2, Option C](01-getting-started.md#option-c--github-packages)) |
| GitHub Packages returns 401 on **publish** | Gradle looks the credentials up by repository name: `ORG_GRADLE_PROJECT_GitHubPackagesUsername` / `…Password`, or the same two as Gradle properties |
| The publish job failed with "tag claims X but gradle.properties says Y" | a release is the version in `gradle.properties`; the tag only names it. Fix whichever is wrong and tag again. |
| `wasmJsTest` does nothing | no `CHROME_BIN`. The task is disabled rather than failing with a download error; `verify.sh` names the skip. |
| OutOfMemory / Metaspace during the build | the Compose compiler plugin and AGP together need more than the 512 m default: `org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g` |
| Compose in an app module does not compile | the module needs both `org.jetbrains.compose` and `org.jetbrains.kotlin.plugin.compose` |

## Things that are not bugs

- **One open app at a time.** Opening another closes the first and keeps its snapshot.
- **A blank frame while an app launches.** `onLaunch` is budgeted at 200 ms; a spinner that flashes
  for one frame is worse than none.
- **An app cannot open a locked hidden app.** If it could, any app would be a key to every locked
  door in the build.
- **A throw inside `Content()` takes the host down.** Compose gives no way to intercept a
  composition that throws. A throw in the instance's *coroutine* scope is contained — the session
  fails, the host does not.
- **`navigation` is missing in `setup`.** There is no screen yet; that is the app-level runtime.
- **No dark theme.** `Tokens` is a data class so that one is a second value, not a second component.
