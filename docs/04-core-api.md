# `core` API reference

`cx.m42.superizer:core:0.1.0` — the contract. Both apps and hosts depend on it; nothing depends on
it in the other direction.

Contents: [contract version](#1-superizercontract) · [the app](#2-the-app) ·
[identity & metadata](#3-identity-and-metadata) · [manifest](#4-appmanifest) ·
[config](#5-config) · [runtimes](#6-the-runtimes) · [services](#7-the-services) ·
[registry](#8-appregistry) · [handler](#9-apphandler) · [events](#10-superizerevent) ·
[activation model](#11-activation-model) · [host ports](#12-the-superizer-ports)

---

## 1. `SuperizerContract`

```kotlin
public object SuperizerContract { public const val VERSION: Int = 1 }
```

The revision of the app contract this build implements. A host puts it in
`HostInfo.contractVersion`; an app declares the oldest it tolerates in `manifest.minHostContract`.
Separate from the library's semver on purpose — see [Architecture §7](02-architecture.md#7-contract-versioning).

---

## 2. The app

### `SuperizerApp<C : Any>`

One per app id, created by the host at startup, alive for the process. Holds **no** screen state.

| Member | Signature | Notes |
|---|---|---|
| `manifest` | `abstract val manifest: AppManifest` | validated before `setup`, shown before `launch` |
| `configSpec` | `abstract val configSpec: AppConfigSpec<C>` | |
| `id` | `val id: AppId` | `= manifest.id` |
| `version` | `val version: String` | `= manifest.version` |
| `metadata` | `val metadata: AppMetadata` | `= manifest.metadata` |
| `setup` | `open fun setup(ctx: AppSetupContext)` | once per enable; default no-op |
| `launch` | `abstract fun launch(runtime: InstanceRuntime, config: C): AppInstance` | once per open; must be cheap |

### `AppInstance`

One open app: its state, its screen, its ending. At most one per app id at a time.

| Member | Signature | When |
|---|---|---|
| `onLaunch` | `open suspend fun onLaunch()` | once, before the first frame. Budget: 200 ms, no network. |
| `saveState` | `open fun saveState(): JsonObject?` | on close and on background. `null` = nothing worth keeping. |
| `restore` | `open fun restore(state: JsonObject)` | after `onLaunch`, when a snapshot exists. Must tolerate old shapes. |
| `onBackground` | `open fun onBackground()` | the host process went to background |
| `onForeground` | `open fun onForeground()` | …and came back |
| `Content` | `@Composable abstract fun Content()` | the whole of the app's UI. No top bar, no drawer. |
| `chrome` | `open val chrome: StateFlow<AppChrome>` | what the host's top bar should read |
| `onCloseRequested` | `open suspend fun onCloseRequested(): Boolean` | `false` vetoes; the host asks the user |
| `onClose` | `open fun onClose()` | save what must survive; never composed again |
| `dispose` | `open fun dispose()` | release; `runtime.scope` is cancelled right after |

### `AppChrome`

```kotlin
public data class AppChrome(
    val title: String? = null,      // null → metadata.title
    val subtitle: String? = null,
    val canGoBack: Boolean = false, // true while the app has somewhere to go back to inside itself
)
```

### `AppSetupContext`

Given to `setup`. Everything registered through it is owned by the handler and torn down in reverse
order on disable.

```kotlin
public interface AppSetupContext {
    public val runtime: AppRuntime                       // app-level: no navigation, survives a close
    public fun settingsSection(section: @Composable (runtime: AppRuntime) -> Unit)
    public fun deepLink(path: String, toConfig: (params: Map<String, String>) -> AppConfig)
    public fun listener(handler: suspend (event: SuperizerEvent) -> Unit)
}
```

### `Disposable`

```kotlin
public fun interface Disposable { public fun dispose() }
```

Undoes one thing `setup` registered. Collected by the handler, run in reverse. Apps rarely build
one themselves.

---

## 3. Identity and metadata

### `AppId`

```kotlin
@Serializable @JvmInline
public value class AppId(public val value: String) {
    // init: require(value.matches(Regex("[a-z0-9][a-z0-9-]*")))
    public companion object { public fun parseOrNull(value: String?): AppId? }
}
```

Kebab-case, checked in the constructor because every source of one — QR, promo code, deep link,
push — is text from outside the process. `parseOrNull` is the version for untrusted input.

### `Localized` and `localized(...)`

```kotlin
@Serializable public class Localized(entries: Map<String, String>, fallback: String) {
    public fun resolve(langTag: String): String
}
public fun localized(vararg entries: Pair<String, String>): Localized   // first entry = fallback
```

Resolution is on the primary subtag, so `ru-RU` finds the `ru` entry. A serializable table rather
than a lambda, because a manifest has to survive a round trip through JSON.

### `AppIcon`

```kotlin
@Serializable public sealed interface AppIcon {
    @Serializable @SerialName("letter") public data class Letter(val char: Char) : AppIcon
    @Serializable @SerialName("named")  public data class Named(val name: String) : AppIcon
    @Serializable @SerialName("path")   public data class Path(val svgPath: String) : AppIcon
}
```

Named glyphs the host's pack knows: `calculator`, `currency`, `apps`, `qr`, `settings`; anything
else falls back to a letter. `Path` is an SVG `d` attribute over a 24×24 viewBox, filled.

### `AppMetadata`

```kotlin
@Serializable public data class AppMetadata(
    val title: Localized,
    val description: Localized? = null,
    val icon: AppIcon = AppIcon.Letter('?'),
    val hidden: Boolean = false,
    val category: String? = null,
)
```

---

## 4. `AppManifest`

```kotlin
@Serializable public data class AppManifest(
    val id: AppId,
    val version: String,
    val minHostContract: Int = 1,
    val metadata: AppMetadata,
    val requires: Set<ServiceKey<*>> = emptySet(),
    val deepLinks: Set<String> = emptySet(),
    val pushTopics: Set<String> = emptySet(),
    val networkHosts: Set<String> = emptySet(),
)
```

Validation at registration (any failure → `RegistrationRejected` with a reason the Service Menu
shows):

| Field | Rule |
|---|---|
| `minHostContract` | `<= HostInfo.contractVersion` |
| `requires` | every key present in `HostInfo.services` |
| `deepLinks` | each matches `[a-z0-9][a-z0-9-]*(/[a-z0-9][a-z0-9-]*)*` — no query, no scheme |
| `pushTopics` | each matches `[A-Za-z0-9][A-Za-z0-9._-]*` |

A duplicate `id` **throws** instead: two apps answering to one name is a build mistake, not a
deployment fact.

---

## 5. Config

### `AppConfig`

```kotlin
@Serializable @JvmInline
public value class AppConfig(public val json: JsonObject) {
    public companion object { public val Empty: AppConfig }
}
public fun JsonObject.asConfig(): AppConfig
```

Raw config as it travels. JSON, so the host never learns an app's shape and the app never learns its
config's source.

### `AppConfigSpec<C : Any>`

```kotlin
public class AppConfigSpec<C : Any>(
    public val serializer: KSerializer<C>,
    public val default: C,
    public val schemaVersion: Int = 1,
    public val fallbackToDefault: Boolean = true,
    private val migrate: (fromVersion: Int, raw: JsonObject) -> JsonObject = { _, raw -> raw },
    private val json: Json = DefaultJson,
) {
    public fun decode(config: AppConfig): Result<C>
    public fun encode(value: C): AppConfig

    public companion object {
        public const val SCHEMA_VERSION: String = "schemaVersion"
        public val DefaultJson: Json     // ignoreUnknownKeys, isLenient, encodeDefaults
        public val None: AppConfigSpec<Unit>
    }
}
```

`decode`: reads `schemaVersion` from the payload (absent → assumed current), runs `migrate` when it
differs, shallow-merges the payload over the default's JSON, then deserialises. Missing keys keep
the default; a broken payload is a `Result.failure` carrying the reason.

The handler turns that failure into a `ConfigRejected` event **and only then** applies
`fallbackToDefault`. Reporting is not optional; recovering is.

`encode`: the inverse, stamped with `schemaVersion`.

Helpers:

```kotlin
public fun JsonObject.schemaVersionOrNull(): Int?
public fun JsonObject.schemaVersion(ifAbsent: Int): Int   // what restore() needs to migrate
```

---

## 6. The runtimes

### `AppRuntime`

```kotlin
public interface AppRuntime {
    public val appId: AppId
    public val host: HostInfo
    public val storage: StorageService
    public val network: NetworkService
    public val logger: Logger
    public val analytics: AnalyticsService
    public val locale: LocaleService
    public val haptics: HapticsService
    public val push: PushService
    public val auth: AuthService
    public val apps: AppsService
    public val events: SharedFlow<SuperizerEvent>
    public val lifecycle: StateFlow<HostLifecycle>
    public val clock: Clock
    public fun <T : Any> service(key: ServiceKey<T>): T?
    public val scope: CoroutineScope          // cancelled on disable
}

public fun <T : Any> AppRuntime.require(key: ServiceKey<T>): T   // throws if not in manifest.requires
```

### `InstanceRuntime`

```kotlin
public interface InstanceRuntime : AppRuntime {
    public val navigation: NavigationService
    override val scope: CoroutineScope        // cancelled on dispose — shadows the app-level scope
}

public val LocalAppRuntime: ProvidableCompositionLocal<InstanceRuntime>
```

`LocalAppRuntime` is provided by the host's app container (and by `runAppTest`). Reading it outside
one throws with a message saying so.

### `HostInfo`

```kotlin
public data class HostInfo(
    val name: String,
    val version: String,
    val build: String,                  // commit, with a * when the tree was dirty
    val platform: Platform,             // Android | Desktop | Web
    val contractVersion: Int,
    val debug: Boolean = false,         // unlocks the 7-tap Service Menu gesture and verbose logs
    val services: Set<ServiceKey<*>> = emptySet(),
)

public enum class Platform { Android, Desktop, Web }
public enum class HostLifecycle { Foreground, Background }
public fun interface Clock { public fun now(): Long }   // epoch millis
```

### `HostRuntimeFactory`

```kotlin
public interface HostRuntimeFactory {
    public fun createApp(appId: AppId, scope: CoroutineScope): AppRuntime
    public fun createInstance(app: AppRuntime, scope: CoroutineScope): InstanceRuntime
}
```

### `ServiceKey<T>`

```kotlin
public class ServiceKey<T : Any>(public val name: String)   // equality is by name
public object ServiceKeySerializer : KSerializer<ServiceKey<*>>   // travels as its name
```

The extension seam of the runtime. A host that gains a camera registers it under a key instead of
growing a field on `AppRuntime` — which would be an incompatible contract change. Declare the key
next to the service interface:

```kotlin
public interface CameraService {
    public suspend fun scan(): String?
    public companion object { public val Key: ServiceKey<CameraService> = ServiceKey("camera") }
}
```

---

## 7. The services

Two rules hold for every one of them: nothing takes a type that cannot be JSON, and every suspending
call is main-safe.

### `StorageService`

```kotlin
public interface StorageService {
    public suspend fun get(key: String): String?
    public suspend fun set(key: String, value: String)
    public suspend fun remove(key: String)
    public suspend fun keys(): Set<String>
}

public suspend inline fun <reified T> StorageService.getJson(key: String, json: Json = Json): T?
public suspend inline fun <reified T> StorageService.setJson(key: String, value: T, json: Json = Json)
```

Namespaced by app id inside the host (`app.<id>.`), so an app sees plain keys and two apps cannot
collide.

### `NetworkService`

```kotlin
public interface NetworkService {
    public val online: StateFlow<Boolean>
    public suspend fun request(request: NetworkRequest): NetworkResponse
    public suspend fun get(url: String, headers: Map<String, String> = emptyMap()): NetworkResponse
    public suspend fun post(
        url: String, body: String,
        contentType: String = "application/json",
        headers: Map<String, String> = emptyMap(),
    ): NetworkResponse
}

public data class NetworkRequest(
    val method: String, val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null, val contentType: String? = null,
)
public data class NetworkResponse(
    val status: Int, val body: String,
    val headers: Map<String, List<String>> = emptyMap(),
) { public val isSuccess: Boolean get() = status in 200..299 }

public class NetworkException(public val status: Int?, message: String, cause: Throwable? = null) : Exception

public inline fun <reified T> NetworkResponse.decode(json: Json = LenientJson): T
public val LenientJson: Json
```

No credentials are injected — read `auth.session` and set the header yourself. 15-second timeout,
no retries, non-2xx is a value and not a throw. Only a transport failure throws, as
`NetworkException`.

### `Logger`, `AnalyticsService`

```kotlin
public interface Logger {
    public fun debug(message: String, throwable: Throwable? = null)
    public fun info(message: String, throwable: Throwable? = null)
    public fun warn(message: String, throwable: Throwable? = null)
    public fun error(message: String, throwable: Throwable? = null)
}

public interface AnalyticsService {
    public fun event(name: String, params: Map<String, String> = emptyMap())
    public fun screen(name: String)
}
```

Tagged `app:<id>` and prefixed with the app id respectively, by the host. In a release build the
console sees `warn` and `error` only; the Service Menu's 500-line ring buffer keeps every level.

### `NavigationService`

```kotlin
public interface NavigationService {
    public suspend fun openApp(id: AppId, config: AppConfig = AppConfig.Empty): Result<AppResult>
    public suspend fun close()
    public fun openSettings()
    public fun openUrl(url: String): Boolean
}

public data class AppResult(val payload: JsonObject? = null)   // payload is always null for now

public sealed class NavigationError(message: String) : Exception(message) {
    public class UnknownApp(public val id: AppId) : NavigationError
    public class Locked(public val id: AppId) : NavigationError      // hidden and not unlocked
    public class Vetoed(public val id: AppId) : NavigationError      // the open app refused to close
}
```

Deliberately not "navigate to this screen": an app may open another app, go to Settings, or close
itself, and nothing else. Only on `InstanceRuntime` — navigating from a background listener is not
a thing an app gets to do.

### `LocaleService`, `HapticsService`

```kotlin
public interface LocaleService {
    public val langTag: StateFlow<String>
    public fun <S> pick(tables: Map<String, S>, fallback: S): S   // by primary subtag
}

public fun interface HapticsService { public fun tick() }
```

`tick()` is a no-op when the host's haptics setting is off. The app never checks the setting.

### `PushService`

```kotlin
public interface PushService {
    public val enabled: StateFlow<Boolean>          // transport present and permission granted
    public suspend fun requestPermission(): Boolean // the system dialog where one exists
    public suspend fun subscribe(topic: String)     // refused + logged if not in manifest.pushTopics
    public suspend fun unsubscribe(topic: String)
    public val subscriptions: StateFlow<Set<String>>
    public val messages: SharedFlow<PushMessage>    // replay 0
}

public data class PushMessage(
    val topic: String?, val data: JsonObject, val link: String?, val receivedAt: Long,
)
```

Deliberately missing: the device token, drawing a notification, and navigating from a message
handler — that is what the payload's `link` is for, and it goes through the same deep-link door as
a QR code.

### `AuthService`

```kotlin
public interface AuthService {
    public val session: StateFlow<AuthSession?>
    public suspend fun signIn(): AuthSession?
    public suspend fun signOut()
}
public data class AuthSession(val userId: String, val displayName: String?, val token: String?)
```

Anonymous today; the shape is fixed so apps can be written against it.

### `AppsService`

```kotlin
public interface AppsService {
    public fun list(): List<AppSummary>
    public fun get(id: AppId): AppSummary?
}
public data class AppSummary(
    val id: AppId, val version: String, val metadata: AppMetadata,
    val state: AppState, val unlocked: Boolean,
)
```

Read-only. No register, no unregister, no handler.

---

## 8. `AppRegistry`

```kotlin
public class AppRegistry(host: HostInfo, events: MutableSharedFlow<SuperizerEvent>? = null) {
    public val apps: StateFlow<List<SuperizerApp<*>>>
    public val manifests: StateFlow<List<Pair<AppManifest, RegistrationOutcome>>>
    public fun register(app: SuperizerApp<*>): Boolean     // throws on a duplicate id
    public fun get(id: AppId): SuperizerApp<*>?
    public fun all(): List<SuperizerApp<*>>
    public fun visible(unlocked: Set<AppId>): List<SuperizerApp<*>>
    public fun outcome(id: AppId): RegistrationOutcome?
}

public sealed interface RegistrationOutcome {
    public data object Accepted : RegistrationOutcome
    public data class Rejected(val reason: String) : RegistrationOutcome
}
```

`manifests` holds everything ever *offered*, accepted or not — which is what the Service Menu lists.

---

## 9. `AppHandler`

The state machine with one current session. Apps never see it; hosts rarely touch it beyond the
shell.

```kotlin
public class AppHandler(
    registry: AppRegistry,
    runtimeFactory: HostRuntimeFactory,
    events: MutableSharedFlow<SuperizerEvent>,
    hostLifecycle: StateFlow<HostLifecycle>,
    snapshots: SnapshotStore,
    clock: Clock,
    scope: CoroutineScope,
    unlocked: () -> Set<AppId> = { emptySet() },
)
```

| Member | Signature | Notes |
|---|---|---|
| `current` | `StateFlow<AppSession?>` | the one open app |
| `states` | `StateFlow<Map<AppId, AppState>>` | |
| `settingsSections` | `StateFlow<List<Pair<AppId, @Composable (AppRuntime) -> Unit>>>` | in registration order |
| `appRuntime` | `fun appRuntime(id: AppId): AppRuntime?` | the app-level runtime, for the push router |
| `isEnabled` | `fun isEnabled(id: AppId): Boolean` | |
| `deepLinkHandler` | `fun deepLinkHandler(id: AppId, path: String): ((Map<String, String>) -> AppConfig)?` | |
| `enableAll` | `suspend fun enableAll()` | `setup(ctx)` for everything registered |
| `enable` / `disable` | `suspend fun enable(id): Boolean` / `disable(id): Boolean` | disable runs disposers in reverse and cancels the scope; it does **not** erase data |
| `launch` | `suspend fun launch(id, config = Empty, restoreFrom: JsonObject? = null, force: Boolean = false): Result<AppSession>` | `force` opens a hidden app that is not unlocked — only activation and the Service Menu pass it |
| `close` | `suspend fun close(force: Boolean = false): Boolean` | `false` = the app vetoed |
| `restoreLastSession` | `suspend fun restoreLastSession(): Result<AppSession>?` | drops snapshots older than 24 h; never uses `force` |
| `observeLifecycle` | `fun observeLifecycle()` | wired by the host once |
| `forgetSnapshot` | `suspend fun forgetSnapshot(id: AppId)` | part of reset |

Supporting types:

```kotlin
public enum class AppState { Registered, Enabled, Creating, Launching, Active, Closing, Disposed, Failed }

public class AppSession internal constructor(
    public val app: SuperizerApp<*>,
    public val config: AppConfig,
    public val instance: AppInstance,
    public val runtime: InstanceRuntime,
    public val state: StateFlow<AppState>,
)

@Serializable public data class SessionSnapshot(
    val appId: AppId, val config: AppConfig, val state: JsonObject?, val savedAt: Long,
)

public interface SnapshotStore {
    public suspend fun save(snapshot: SessionSnapshot)
    public suspend fun load(): SessionSnapshot?
    public suspend fun clear()
}
```

Concurrency: `enable`, `disable`, `launch` and `close` share one mutex. A second tap while the first
launch is still in `onLaunch` queues rather than interleaves.

Crash containment: an exception escaping an instance's own coroutines fails **the session**
(`AppState.Failed` + `Crashed`), not the host. An exception thrown inside `Content()` cannot be
intercepted — Compose offers no hook — which is why the TCK composes `Content()` on the default
config.

---

## 10. `SuperizerEvent`

```kotlin
public sealed interface SuperizerEvent { public val appId: AppId? }
```

| Event | Carries | Emitted when |
|---|---|---|
| `Registered` | `version` | a manifest was accepted |
| `RegistrationRejected` | `reason` | contract too new, a required service missing, a bad path or topic |
| `SetupMismatch` | `what` | `setup` registered a deep link the manifest does not declare — the app stays disabled |
| `Enabled` / `Disabled` | | `setup` ran / its disposers ran |
| `RuntimeCreated` | | an instance runtime exists |
| `Configured` | `config` | the payload decoded |
| `ConfigRejected` | `config`, `reason` | it did not; `fallbackToDefault` decides what happens next |
| `Created` | | `launch()` returned an instance |
| `Launched` | | `onLaunch()` returned |
| `Restored` | | a snapshot was handed back |
| `Active` | | the session is current |
| `Background` / `Foreground` | `appId?` | the host process moved |
| `Closed` / `Disposed` | | the instance is going / gone |
| `LaunchFailed` | `reason`, `cause?` | any step of the launch failed |
| `Activated` | `source` | `"qr"`, `"promo"`, `"deeplink"`, `"push"`, `"service-menu"` |
| `Unlocked` / `Locked` | | a hidden app was revealed / put back |
| `AddedToHome` / `RemovedFromHome` | | the user's Home set changed |
| `PushReceived` | `topic?` | a data message reached an app |
| `PushDropped` | `reason` | `UnsupportedSchema`, `UnknownApp`, `Locked`, `Disabled`, `LinkMismatch` |
| `DeepLinkUnmatched` | `path` | the app serves no such path; it opens on an empty config anyway |
| `RouteDiscarded` | `link` | a route arrived, the open app vetoed, the user chose to stay |
| `Crashed` | `cause` | an exception escaped the instance's scope |
| `Reset` | | storage, topics, snapshot and unlock all erased |

Replay is 0: listeners registered in `setup` subscribe before the first launch, and the current
truth is `handler.states` / `handler.current`. A log is not a source of truth.

---

## 11. Activation model

```kotlin
public data class Activation(
    val appId: AppId,
    val config: AppConfig = AppConfig.Empty,
    val unlock: Boolean = true,      // whether applying this also reveals a hidden app
)

public sealed interface ActivationResult {
    public data class Success(val activation: Activation) : ActivationResult
    public data class HostCommand(val command: Command) : ActivationResult
    public data class Rejected(val reason: Reason) : ActivationResult
    public enum class Reason { Malformed, UnsupportedSchema, UnknownApp, UnknownCode, Expired }
    public enum class Command { OpenServiceMenu }
}
```

The *model* is in `core` so an app's tests can name one; the parsers and the service that applies
them live in `host`, and no app imports them.

---

## 12. The `Superizer` ports

What the host's own screens need from the host, declared in `core` so `ui` never depends on `host`.
**Apps do not see this type.**

```kotlin
public interface Superizer {
    public val hostInfo: HostInfo
    public val registry: AppRegistry
    public val handler: AppHandler
    public val events: SharedFlow<SuperizerEvent>
    public val unlocked: StateFlow<Set<AppId>>
    public val home: StateFlow<List<AppId>>
    public suspend fun addToHome(id: AppId)        // ignored for an unknown or locked id
    public suspend fun removeFromHome(id: AppId)   // the app stays unlocked and in the catalog
    public val haptics: HapticsService
    public val settings: HostSettingsPort
    public val activation: ActivationPort
    public val route: RoutePort
    public val diagnostics: DiagnosticsPort
    public val commands: SharedFlow<ShellCommand>
    public companion object                        // host hangs `build { … }` off this
}

public sealed interface ShellCommand {
    public data class OpenApp(val id: AppId) : ShellCommand
    public data object OpenSettings : ShellCommand
    public data object Close : ShellCommand
}

public data class LanguageOption(val tag: String, val nativeName: String)

public interface HostSettingsPort {
    public val langTag: StateFlow<String>
    public val languages: List<LanguageOption>
    public fun chooseLanguage(tag: String)
    public val haptics: StateFlow<Boolean>
    public fun setHaptics(enabled: Boolean)
}

public interface ActivationPort {
    public suspend fun fromQr(text: String): ActivationResult
    public suspend fun fromPromo(code: String): ActivationResult
    public suspend fun fromDeepLink(url: String): ActivationResult
    public suspend fun apply(activation: Activation, source: String): Result<AppSession>
}

public interface RoutePort {                       // consume-once
    public val route: StateFlow<String?>
    public fun deliver(link: String)
    public fun consume(): String?
    public fun discard()
}

public interface DiagnosticsPort {
    public val log: StateFlow<List<String>>              // the ring buffer, newest last
    public val recentEvents: StateFlow<List<SuperizerEvent>>   // the last hundred
    public suspend fun reset(id: AppId)                  // storage + topics + snapshot + unlock
    public suspend fun unlock(id: AppId)                 // reveal with no activation; also adds to Home
    public suspend fun lock(id: AppId)                   // put back behind the activation
    public suspend fun simulatePush(payload: Map<String, String>)
}
```

`Home` is a **chosen** set, not "everything visible": a fresh install shows what the host named in
`home(...)`, the catalog adds, an activation of a hidden app adds, and a long press removes. Ids
that are no longer registered or unlocked stay in the store and are skipped by the shell, so a build
that drops an app and a later build that brings it back do not lose the user's arrangement.
