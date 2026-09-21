# `host` API reference

`cx.m42.superizer:host:0.1.0` — the service implementations, the platform `actual`s, activation and
push routing. **Only a host depends on this module.** An app that imports anything from
`cx.m42.superizer.host` has broken the boundary, and `checkDependencyRules` says so.

---

## 1. Building a host

```kotlin
public fun Superizer.Companion.build(
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    block: SuperizerBuilder.() -> Unit,
): Superizer
```

`scope` is the host's own: every app scope and instance scope is a child of it, so shutting the host
down takes all of it with it. A test passes its own (`Dispatchers.Unconfined`, or a
`TestScope`), which is also how the handler avoids reaching for a main dispatcher a unit test does
not have.

Building **registers every app and enables them all** (`handler.enableAll()` on the scope), wires
process lifecycle, attaches the push router and the device registrar, and starts collecting the
event ring buffer.

### `SuperizerBuilder`

| Call | Default | What it does |
|---|---|---|
| `host(info: HostInfo)` | **required** | identity, platform, contract version, debug flag. The builder copies the registered service keys into `info.services`. |
| `register(app: SuperizerApp<*>)` | — | explicit registration. The order is the order All Apps and the Service Menu list apps in. |
| `home(ids: List<AppId>)` / `home(vararg ids: String)` | empty | what a **fresh install** shows on Home. Read on the first run only; after that Home is the user's. |
| `scheme(value: String)` | `"superizer"` | the URL scheme: `<scheme>://app/<id>/<path>` and `<scheme>://activate?…` |
| `languages(vararg LanguageOption, fallback: String = "en")` | `ru`, `en` | what the Settings picker offers |
| `promoCodes(table: Map<String, ActivationResult>)` | reject everything | a shipped code table, normalised (case, spaces and punctuation ignored) |
| `promoCodes(resolver: PromoCodeResolver)` | | the same thing against a server: `fun interface { suspend fun resolve(code: String): ActivationResult }` |
| `serviceCode(code: String?)` | `null` | the one door into the Service Menu in a release build. `null` leaves it debug-only (seven taps on the build stamp). |
| `service(key: ServiceKey<T>, implementation: T)` | none | registers an optional service. Apps reach it with `runtime.service(key)` / `require(key)`. |
| `clock(value: Clock)` | `SystemClock` | inject time |
| `network(value: NetworkService)` | Ktor | substitute the whole service, so no test reaches the network |
| `httpClient(value: HttpClient)` | `KtorNetworkService.defaultClient()` | a client of your own, with the same service |
| `auth(value: AuthService)` | `AnonymousAuth` | |
| `notifier(value: Notifier)` | none | draws a notification for a non-silent data message: `fun interface { fun show(title: String?, body: String?, link: String) }` |
| `lifecycle(value: StateFlow<HostLifecycle>)` | `PlatformLifecycle.state` | drive foreground/background by hand in a test |

A complete example is in [Getting started §3.4](01-getting-started.md#34-the-host-itself--commonmainmyhostkt).

```kotlin
public typealias HostSession = AppSession   // re-exported, so a host file needs one import fewer
```

---

## 2. What you get without writing it

| Screen / behaviour | Where it comes from |
|---|---|
| Home — the chosen tiles, "+" to the catalog, long press to remove | `ui`, driven by `Superizer.home` |
| All Apps — the catalog; a tap adds to Home or takes off | `ui`, driven by `registry.visible(unlocked)` |
| The drawer — Home, every app on Home, All Apps, plus the footer | `AppScaffold` |
| Settings — language, haptics, **a block per app**, about, the 7-tap door | `SettingsScreen` + `handler.settingsSections` |
| Activate — promo code and QR text | `ActivateScreen` + `ActivationPort` |
| The Service Menu | `ServiceMenuScreen` + `DiagnosticsPort` |
| The app container, the error screen, the veto dialog | `SuperizerShell` |
| Deep links, push routing, session restore | `host` |

---

## 3. Services (`cx.m42.superizer.host`)

These are the implementations behind `AppRuntime`. A host normally never names them; they are
public so a *different* host can reuse one.

| Type | Role |
|---|---|
| `DefaultHostRuntimeFactory` | builds both runtime levels; scoped storage/logger/analytics/push per app id, shared locale/clock/events |
| `PrefsStorageService(appId)` | `StorageService` over `SafePrefs`, prefixed `app.<id>.`; `erase(appId)` for reset |
| `KtorNetworkService(client, online)` | `NetworkService`; `defaultClient()` sets a 15 s timeout and `expectSuccess = false`; `TIMEOUT_MS = 15_000` |
| `ConsoleLogger(tag, buffer, verbose)` | ring buffer always, console only for `warn`/`error` unless `verbose` |
| `LogBuffer(capacity = 500)` | `lines: StateFlow<List<String>>`, `add`, `clear` |
| `LoggingAnalytics(appId, logger)` | prefixes the app id before anything leaves |
| `AppLocaleService(supported, fallback)` | `LocaleService` **and** `HostSettingsPort`; resolves stored choice → device → fallback; `useForTesting(tag)`, `refreshFromDevice()` |
| `PlatformHaptics(enabled)` | a no-op when the Settings switch is off |
| `AnonymousAuth` | always anonymous, honestly |
| `RegistryView(registry, handler, unlocked)` | the read-only `AppsService` |
| `ServiceRegistry(services)` | the optional-service map behind `runtime.service(key)` |
| `SystemClock` | `expect object` — epoch millis per platform |

### Stores (`cx.m42.superizer.host.storage`)

| Type | Keeps |
|---|---|
| `PrefsStorage` | the `expect object` every platform implements: `get/put/remove/keys`. Desktop: a properties file under `~/.superizer` (`useDirectory(File)` to move it). Android: `MODE_PRIVATE` shared prefs (`initPrefs(context)` first). Web: `localStorage`. |
| `SafePrefs` | the same, with every call guarded — a browser with site data blocked is "nothing was saved", not a crash. **Use this, not `PrefsStorage`.** |
| `HostKeys` | `host.language`, `host.haptics`, `host.unlocked`, `host.home`, `host.session`, `host.push.topics` — a namespace no app prefix can reach |
| `UnlockStore` | which hidden apps this device has unlocked; `unlock`, `lock`, `unlocked: StateFlow` |
| `HomeStore(defaults)` | the user's ordered Home set; absent = first run → defaults, present-but-empty = a choice to keep |
| `PrefsSnapshotStore` | the one `SessionSnapshot`; a snapshot an older build wrote and this one cannot parse means the same as no snapshot |

### Activation (`cx.m42.superizer.host.activation`)

| Type | Role |
|---|---|
| `QrPayloadParser(registry, scheme, json)` | `parse(text)` for both forms: a JSON payload and a `<scheme>://activate?a=…&c=…` URL. Never throws; every branch is a typed rejection. `SCHEMA_VERSION = 1`, `TYPE = "app_activation"` |
| `PromoCodeResolver` | `fun interface { suspend fun resolve(code: String): ActivationResult }` |
| `LocalPromoCodes(table)` | a shipped table; `normalise(code)` strips everything but letters and digits and upper-cases, so `test 2026`, `TEST-2026` and `test2026` are one code |
| `ActivationService` | the `ActivationPort`: the one place an activation turns into a running app |

### Push (`cx.m42.superizer.host.push`)

| Type | Role |
|---|---|
| `PushTransport` | the platform seam: `token`, `incoming`, `available`, `deliverTap`, `deliverMessage`, `requestPermission`, `subscribeTopic`, `unsubscribeTopic`. **A no-op `actual` on all three targets today.** |
| `NoopPushTransport` | the shared no-op body the three actuals delegate to |
| `PushRouter` | payload → either data or a screen; enforces schema, app id, unlock, enabled, and link prefix |
| `PendingRoute` | the consume-once `RoutePort` |
| `TopicStore` | per-app topics, persisted; `qualified(appId, topic)` = `"<id>.<topic>"` |
| `RoutedPush` | the `PushService` an app sees; refuses an undeclared topic |
| `Notifier` | `fun interface { fun show(title: String?, body: String?, link: String) }` |
| `DeviceRegistrar` | token × session × app ids → the server. There is no server, so it logs. |
| `PushRegistration(token, platform)`, `IncomingPush(data, tapped)` | wire types |

Adding Firebase is one `actual` file plus a `google-services.json` — the router, the topic store,
the Service Menu's simulator and the bench app's Push card all work against the seam today.

### Platform (`cx.m42.superizer.host.platform`)

```kotlin
public expect fun currentPlatform(): Platform
public expect fun deviceLanguageTag(): String        // "" means "could not tell"
public expect fun openExternalUrl(url: String): Boolean
public expect object PlatformLifecycle { public val state: StateFlow<HostLifecycle> }
public expect object Connectivity { public val online: StateFlow<Boolean> }
```

Android additionally exposes `AndroidHost.init(context)` (vibrator, process lifecycle,
connectivity). On Android the lifecycle follows `ON_START`/`ON_STOP`, never
`ON_RESUME`/`ON_PAUSE`: a permission dialog pauses the activity, and a snapshot written on every one
of those is a snapshot written on every tap.

---

## 4. What each platform needs at startup

| Target | Before the first composition |
|---|---|
| **Android** | `initPrefs(context)` and `AndroidHost.init(context)`; build the host **once per process**, not per activity; `singleTask` plus a `VIEW` intent filter for your scheme; hand `intent.data` to `superizer.route.deliver(...)` from both `onCreate` and `onNewIntent` |
| **Desktop** | nothing required. Optionally `PrefsStorage.useDirectory(File(...))`; parse `--link=`/`--activate=` into `route.deliver(...)` |
| **Web (wasmJs)** | nothing required. Parse `?link=` and `?activate=` (percent-decoded) into `route.deliver(...)`; `index.html` needs a `#composeTarget` div |

---

## 5. The Service Menu

Two doors, and only two:

1. **debug builds** — seven taps on the build stamp in Settings (`HostInfo.debug = true`);
2. **any build** — typing the `serviceCode(...)` on the Activate screen. It resolves to
   `ActivationResult.HostCommand(OpenServiceMenu)`, which is matched by the shell — not a reserved
   app id, because one magic name always becomes several.

What it does, all through `DiagnosticsPort`: list every manifest with its outcome (including the
rejected ones and why), run an app, run it with a pasted config, unlock/lock a hidden app,
enable/disable, reset an app (storage + topics + snapshot + unlock, confirmed), show the last
hundred events, show the log ring buffer, and simulate a push payload end to end.

---

## 6. Test hooks a host can use

```kotlin
Superizer.build(scope) {
    host(HostInfo(..., debug = true))
    clock(FakeClock())                      // cx.m42.superizer.testing
    network(FakeNetwork().apply { respond("https://api…", """{"rates":{}}""") })
    lifecycle(MutableStateFlow(HostLifecycle.Foreground))
    register(MyApp())
}
```

Plus `PrefsStorage.useDirectory(tmp)` on desktop so a test never touches the developer's own file,
and `AppLocaleService.useForTesting(tag)` to pin a language.
