package cx.m42.superizer.testing

import cx.m42.superizer.app.AppConfig
import cx.m42.superizer.app.AppId
import cx.m42.superizer.app.AppMetadata
import cx.m42.superizer.event.SuperizerEvent
import cx.m42.superizer.registry.AppState
import cx.m42.superizer.registry.SessionSnapshot
import cx.m42.superizer.registry.SnapshotStore
import cx.m42.superizer.runtime.AnalyticsService
import cx.m42.superizer.runtime.AppResult
import cx.m42.superizer.runtime.AppRuntime
import cx.m42.superizer.runtime.AppSummary
import cx.m42.superizer.runtime.AppsService
import cx.m42.superizer.runtime.AuthService
import cx.m42.superizer.runtime.AuthSession
import cx.m42.superizer.runtime.Clock
import cx.m42.superizer.runtime.HapticsService
import cx.m42.superizer.runtime.HostInfo
import cx.m42.superizer.runtime.HostLifecycle
import cx.m42.superizer.runtime.HostRuntimeFactory
import cx.m42.superizer.runtime.InstanceRuntime
import cx.m42.superizer.runtime.LocaleService
import cx.m42.superizer.runtime.Logger
import cx.m42.superizer.runtime.NavigationError
import cx.m42.superizer.runtime.NavigationService
import cx.m42.superizer.runtime.NetworkException
import cx.m42.superizer.runtime.NetworkRequest
import cx.m42.superizer.runtime.NetworkResponse
import cx.m42.superizer.runtime.NetworkService
import cx.m42.superizer.runtime.Platform
import cx.m42.superizer.runtime.PushMessage
import cx.m42.superizer.runtime.PushService
import cx.m42.superizer.runtime.ServiceKey
import cx.m42.superizer.runtime.StorageService
import cx.m42.superizer.SuperizerContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject

/**
 * The platform, faked.
 *
 * Every one of these is short — twenty lines, no cleverness — and that is a *property of the
 * contract* rather than of this file: the service interfaces take strings and JSON and nothing
 * else (04), so a fake cannot become an implementation in disguise. If one of these ever needs to
 * be long, the interface it fakes has gone wrong.
 */

public class InMemoryStorage(initial: Map<String, String> = emptyMap()) : StorageService {
    public val entries: MutableMap<String, String> = initial.toMutableMap()

    override suspend fun get(key: String): String? = entries[key]

    override suspend fun set(key: String, value: String) {
        entries[key] = value
    }

    override suspend fun remove(key: String) {
        entries.remove(key)
    }

    override suspend fun keys(): Set<String> = entries.keys.toSet()
}

/**
 * Answers only what a test told it to.
 *
 * A URL with no stub throws, deliberately: a test that hits an endpoint it did not name is a test
 * about to reach the real network, and that should fail loudly the first time rather than pass
 * until the day the network is slow.
 */
public class FakeNetwork : NetworkService {
    override val online: MutableStateFlow<Boolean> = MutableStateFlow(true)

    /** Every request that was made, in order — what "the converter did not call the network" reads. */
    public val calls: MutableList<NetworkRequest> = mutableListOf()

    private val responses = mutableMapOf<String, (NetworkRequest) -> NetworkResponse>()

    public fun respond(url: String, body: String, status: Int = 200) {
        responses[url] = { NetworkResponse(status, body) }
    }

    public fun respond(url: String, handler: (NetworkRequest) -> NetworkResponse) {
        responses[url] = handler
    }

    public fun fail(url: String, message: String = "offline") {
        responses[url] = { throw NetworkException(null, message) }
    }

    override suspend fun request(request: NetworkRequest): NetworkResponse {
        calls += request
        val handler = responses[request.url]
            ?: responses.entries.firstOrNull { request.url.startsWith(it.key) }?.value
            ?: error("FakeNetwork has no stub for ${request.url}")
        return handler(request)
    }
}

public class RecordingLogger : Logger {
    public val lines: MutableList<String> = mutableListOf()

    override fun debug(message: String, throwable: Throwable?) {
        lines += "D $message"
    }

    override fun info(message: String, throwable: Throwable?) {
        lines += "I $message"
    }

    override fun warn(message: String, throwable: Throwable?) {
        lines += "W $message"
    }

    override fun error(message: String, throwable: Throwable?) {
        lines += "E $message"
    }
}

public class RecordingAnalytics : AnalyticsService {
    public val events: MutableList<Pair<String, Map<String, String>>> = mutableListOf()
    public val screens: MutableList<String> = mutableListOf()

    override fun event(name: String, params: Map<String, String>) {
        events += name to params
    }

    override fun screen(name: String) {
        screens += name
    }
}

public class RecordingNavigation : NavigationService {
    public val opened: MutableList<Pair<AppId, AppConfig>> = mutableListOf()
    public var closed: Int = 0
    public var settingsOpened: Int = 0
    public val urls: MutableList<String> = mutableListOf()

    /** Ids a test wants `openApp` to refuse, so the `Result` branches are reachable (D22). */
    public val unknown: MutableSet<AppId> = mutableSetOf()

    override suspend fun openApp(id: AppId, config: AppConfig): Result<AppResult> {
        if (id in unknown) return Result.failure(NavigationError.UnknownApp(id))
        opened += id to config
        return Result.success(AppResult())
    }

    override suspend fun close() {
        closed++
    }

    override fun openSettings() {
        settingsOpened++
    }

    override fun openUrl(url: String): Boolean {
        urls += url
        return true
    }
}

public class FakeLocale(tag: String = "ru") : LocaleService {
    override val langTag: MutableStateFlow<String> = MutableStateFlow(tag)

    override fun <S> pick(tables: Map<String, S>, fallback: S): S =
        tables[langTag.value.substringBefore('-').lowercase()] ?: fallback
}

/** Time a test controls, so "older than a day" is a line rather than a sleep (D30). */
public class FakeClock(private var millis: Long = 1_700_000_000_000) : Clock {
    override fun now(): Long = millis

    public fun advance(millis: Long = 0, seconds: Long = 0, minutes: Long = 0, hours: Long = 0) {
        this.millis += millis + seconds * 1000 + minutes * 60_000 + hours * 3_600_000
    }
}

public class RecordingHaptics : HapticsService {
    public var ticks: Int = 0

    override fun tick() {
        ticks++
    }
}

/** Push without a transport: `deliver` is what the router would have done (13 §7). */
public class FakePush : PushService {
    override val enabled: MutableStateFlow<Boolean> = MutableStateFlow(true)
    override val subscriptions: MutableStateFlow<Set<String>> = MutableStateFlow(emptySet())

    private val _messages = MutableSharedFlow<PushMessage>(replay = 0, extraBufferCapacity = 16)
    override val messages: SharedFlow<PushMessage> get() = _messages.asSharedFlow()

    public var permissionGranted: Boolean = true

    override suspend fun requestPermission(): Boolean = permissionGranted

    override suspend fun subscribe(topic: String) {
        subscriptions.value = subscriptions.value + topic
    }

    override suspend fun unsubscribe(topic: String) {
        subscriptions.value = subscriptions.value - topic
    }

    public fun deliver(
        topic: String? = null,
        data: JsonObject = JsonObject(emptyMap()),
        link: String? = null,
        receivedAt: Long = 0,
    ) {
        _messages.tryEmit(PushMessage(topic, data, link, receivedAt))
    }
}

public class FakeAuth(session: AuthSession? = null) : AuthService {
    override val session: MutableStateFlow<AuthSession?> = MutableStateFlow(session)

    override suspend fun signIn(): AuthSession? = session.value

    override suspend fun signOut() {
        session.value = null
    }
}

public class FakeApps(private val summaries: List<AppSummary> = emptyList()) : AppsService {
    override fun list(): List<AppSummary> = summaries

    override fun get(id: AppId): AppSummary? = summaries.firstOrNull { it.id == id }
}

public class InMemorySnapshotStore : SnapshotStore {
    public var snapshot: SessionSnapshot? = null

    override suspend fun save(snapshot: SessionSnapshot) {
        this.snapshot = snapshot
    }

    override suspend fun load(): SessionSnapshot? = snapshot

    override suspend fun clear() {
        snapshot = null
    }
}

/** A summary that reads the way the registry's would, for a fake `AppsService`. */
public fun appSummary(
    id: String,
    metadata: AppMetadata,
    version: String = "1.0.0",
    state: AppState = AppState.Enabled,
    unlocked: Boolean = true,
): AppSummary = AppSummary(AppId(id), version, metadata, state, unlocked)

/**
 * The whole platform an app can see, in one object a test can reach into.
 *
 * It implements [InstanceRuntime] rather than wrapping one, so a test holds `runtime.storage` as
 * an `InMemoryStorage` — with its map visible — while the app under test holds the same object as
 * a `StorageService` and cannot tell.
 *
 * [services] is what proves D19 and D47 together: a fake host offers *exactly* what the manifest
 * declares, so an app that calls `require(key)` on something it forgot to declare fails in its
 * very first test rather than as a null in production.
 */
public class FakeAppRuntime(
    override val appId: AppId = AppId("test"),
    override val scope: CoroutineScope,
    hostName: String = "Fixture",
    debug: Boolean = true,
    declaredServices: Set<ServiceKey<*>> = emptySet(),
) : InstanceRuntime {

    override val storage: InMemoryStorage = InMemoryStorage()
    override val network: FakeNetwork = FakeNetwork()
    override val logger: RecordingLogger = RecordingLogger()
    override val analytics: RecordingAnalytics = RecordingAnalytics()
    override val locale: FakeLocale = FakeLocale()
    override val haptics: RecordingHaptics = RecordingHaptics()
    override val push: FakePush = FakePush()
    override val auth: FakeAuth = FakeAuth()
    override val apps: FakeApps = FakeApps()
    override val navigation: RecordingNavigation = RecordingNavigation()
    override val clock: FakeClock = FakeClock()

    public val emittedEvents: MutableSharedFlow<SuperizerEvent> =
        MutableSharedFlow(replay = 0, extraBufferCapacity = 64)
    override val events: SharedFlow<SuperizerEvent> get() = emittedEvents.asSharedFlow()

    public val hostLifecycle: MutableStateFlow<HostLifecycle> = MutableStateFlow(HostLifecycle.Foreground)
    override val lifecycle: StateFlow<HostLifecycle> get() = hostLifecycle.asStateFlow()

    /** Whatever the manifest declared, and nothing else. `provide` adds one for a specific test. */
    public val services: MutableMap<ServiceKey<*>, Any> = mutableMapOf()

    override val host: HostInfo = HostInfo(
        name = hostName,
        version = "0.0.0",
        build = "test",
        platform = Platform.Desktop,
        contractVersion = SuperizerContract.VERSION,
        debug = debug,
        services = declaredServices,
    )

    public fun <T : Any> provide(key: ServiceKey<T>, implementation: T) {
        services[key] = implementation
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> service(key: ServiceKey<T>): T? = services[key] as T?
}

/** Builds fake runtimes for a handler under test; both levels are the same object (04). */
public class FakeHostRuntimeFactory(
    private val hostInfo: HostInfo? = null,
) : HostRuntimeFactory {
    public val created: MutableMap<AppId, FakeAppRuntime> = mutableMapOf()

    override fun createApp(appId: AppId, scope: CoroutineScope): AppRuntime =
        created.getOrPut(appId) { FakeAppRuntime(appId, scope) }

    override fun createInstance(app: AppRuntime, scope: CoroutineScope): InstanceRuntime =
        FakeAppRuntime(app.appId, scope).also { created[app.appId] = it }
}
