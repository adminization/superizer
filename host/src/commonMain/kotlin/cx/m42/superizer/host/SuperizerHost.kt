package cx.m42.superizer.host

import cx.m42.superizer.ActivationPort
import cx.m42.superizer.DiagnosticsPort
import cx.m42.superizer.HostSettingsPort
import cx.m42.superizer.LanguageOption
import cx.m42.superizer.RoutePort
import cx.m42.superizer.ShellCommand
import cx.m42.superizer.Superizer
import cx.m42.superizer.activation.ActivationResult
import cx.m42.superizer.app.AppConfig
import cx.m42.superizer.app.AppId
import cx.m42.superizer.app.SuperizerApp
import cx.m42.superizer.event.SuperizerEvent
import cx.m42.superizer.host.activation.ActivationService
import cx.m42.superizer.host.activation.LocalPromoCodes
import cx.m42.superizer.host.activation.PromoCodeResolver
import cx.m42.superizer.host.net.KtorNetworkService
import cx.m42.superizer.host.platform.PlatformLifecycle
import cx.m42.superizer.host.platform.openExternalUrl
import cx.m42.superizer.host.push.DeviceRegistrar
import cx.m42.superizer.host.push.IncomingPush
import cx.m42.superizer.host.push.Notifier
import cx.m42.superizer.host.push.PendingRoute
import cx.m42.superizer.host.push.PushRouter
import cx.m42.superizer.host.push.RoutedPush
import cx.m42.superizer.host.push.TopicStore
import cx.m42.superizer.host.storage.PrefsSnapshotStore
import cx.m42.superizer.host.storage.PrefsStorageService
import cx.m42.superizer.host.storage.UnlockStore
import cx.m42.superizer.registry.AppHandler
import cx.m42.superizer.registry.AppRegistry
import cx.m42.superizer.registry.AppSession
import cx.m42.superizer.runtime.AppResult
import cx.m42.superizer.runtime.AuthService
import cx.m42.superizer.runtime.Clock
import cx.m42.superizer.runtime.HapticsService
import cx.m42.superizer.runtime.HostInfo
import cx.m42.superizer.runtime.Logger
import cx.m42.superizer.runtime.NavigationError
import cx.m42.superizer.runtime.NavigationService
import cx.m42.superizer.runtime.NetworkService
import cx.m42.superizer.runtime.ServiceKey
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Assembles a host.
 *
 * Registration is explicit and by hand (D1) — no classpath scanning, no `ServiceLoader`, which does
 * not exist on wasm and which R8 would need told about. This block is Adminizer's `fixture/index.ts`
 * in Kotlin, and reading it tells you exactly what is in a build.
 *
 * ```kotlin
 * val superizer = Superizer.build {
 *     host(HostInfo("Unitool", BuildInfo.VERSION, BuildInfo.label, currentPlatform(), SuperizerContract.VERSION))
 *     register(CalculatorApp())
 *     register(CurrencyConverterApp())
 *     promoCodes(mapOf("SCI" to Activation(AppId("calculator"), scientific)))
 *     serviceCode("SERVICE")
 * }
 * ```
 */
public class SuperizerBuilder internal constructor() {
    internal var hostInfo: HostInfo? = null
    internal val apps: MutableList<SuperizerApp<*>> = mutableListOf()
    internal val services: MutableMap<ServiceKey<*>, Any> = mutableMapOf()
    internal var promo: PromoCodeResolver = PromoCodeResolver {
        ActivationResult.Rejected(ActivationResult.Reason.UnknownCode)
    }
    internal var serviceCode: String? = null
    internal var scheme: String = "superizer"
    internal var languages: List<LanguageOption> = listOf(
        LanguageOption("ru", "Русский"),
        LanguageOption("en", "English"),
    )
    internal var fallbackLanguage: String = "en"
    internal var clock: Clock = SystemClock
    internal var httpClient: HttpClient? = null
    internal var network: NetworkService? = null
    internal var auth: AuthService = AnonymousAuth()
    internal var notifier: Notifier? = null
    internal var lifecycle: StateFlow<cx.m42.superizer.runtime.HostLifecycle>? = null

    public fun host(info: HostInfo) {
        hostInfo = info
    }

    /** Registration order is the order All Apps and the drawer list them in. */
    public fun register(app: SuperizerApp<*>) {
        apps += app
    }

    /** How a host adds an optional service (D19). None in the MVP — the mechanism is the point. */
    public fun <T : Any> service(key: ServiceKey<T>, implementation: T) {
        services[key] = implementation
    }

    public fun promoCodes(resolver: PromoCodeResolver) {
        promo = resolver
    }

    public fun promoCodes(table: Map<String, ActivationResult>) {
        promo = LocalPromoCodes(table)
    }

    /** The one door into the Service Menu in a release build (D34). Null leaves it debug-only. */
    public fun serviceCode(code: String?) {
        serviceCode = code
    }

    /** The URL scheme this host answers to — `unitool` for Unitool. */
    public fun scheme(value: String) {
        scheme = value
    }

    public fun languages(vararg options: LanguageOption, fallback: String = "en") {
        languages = options.toList()
        fallbackLanguage = fallback
    }

    public fun clock(value: Clock) {
        clock = value
    }

    /** Substituted whole in a test, so no test ever reaches the network. */
    public fun network(value: NetworkService) {
        network = value
    }

    public fun httpClient(value: HttpClient) {
        httpClient = value
    }

    public fun auth(value: AuthService) {
        auth = value
    }

    /** Draws a notification for a non-silent data message (13). None of the three targets has one. */
    public fun notifier(value: Notifier) {
        notifier = value
    }

    public fun lifecycle(value: StateFlow<cx.m42.superizer.runtime.HostLifecycle>) {
        lifecycle = value
    }
}

/**
 * Builds the host and enables every app in it.
 *
 * @param scope the host's own scope; everything an app starts is a child of it, so shutting the
 *   host down takes all of it with it. A test passes its own, which is also how the handler avoids
 *   reaching for a main dispatcher that a unit test does not have.
 */
public fun Superizer.Companion.build(
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    block: SuperizerBuilder.() -> Unit,
): Superizer = SuperizerHost(SuperizerBuilder().apply(block), scope)

/**
 * The host itself: registry, handler, activation, push routing, settings and the log — wired once.
 *
 * Everything an app can reach is behind `AppRuntime`; everything the *shell* can reach is behind
 * the ports in `core`. Nothing in this class is visible to either, which is what keeps every app
 * module from ever depending on this one (02).
 */
internal class SuperizerHost(
    builder: SuperizerBuilder,
    private val scope: CoroutineScope,
) : Superizer {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override val hostInfo: HostInfo = requireNotNull(builder.hostInfo) {
        "Superizer.build { host(HostInfo(…)) } is required"
    }.copy(services = builder.services.keys)

    private val _events = MutableSharedFlow<SuperizerEvent>(replay = 0, extraBufferCapacity = 64)
    override val events: SharedFlow<SuperizerEvent> = _events.asSharedFlow()

    override val registry: AppRegistry = AppRegistry(hostInfo, _events)

    private val unlockStore = UnlockStore(_events, json)
    override val unlocked: StateFlow<Set<AppId>> = unlockStore.unlocked

    /** D48. One call behind both doors: Home's long press and the Service Menu's "Hide". */
    override suspend fun hide(id: AppId) {
        unlockStore.lock(id)
    }

    private val logBuffer = LogBuffer()
    private val hostLogger: Logger = ConsoleLogger("host", logBuffer, verbose = hostInfo.debug)

    private val localeService = AppLocaleService(builder.languages, builder.fallbackLanguage)
    override val settings: HostSettingsPort = localeService
    override val haptics: HapticsService = PlatformHaptics(localeService.haptics)

    private val client: HttpClient? = if (builder.network == null) {
        builder.httpClient ?: KtorNetworkService.defaultClient()
    } else {
        null
    }
    private val network: NetworkService = builder.network ?: KtorNetworkService(client!!)

    private val topics = TopicStore(json)
    private val pushes = mutableMapOf<AppId, RoutedPush>()

    private val _commands = MutableSharedFlow<ShellCommand>(replay = 0, extraBufferCapacity = 8)
    override val commands: SharedFlow<ShellCommand> = _commands.asSharedFlow()

    private val pendingRoute = PendingRoute(_events)
    override val route: RoutePort = pendingRoute

    private val snapshots = PrefsSnapshotStore(json)

    private val appsView = RegistryView(registry, { handler }, unlockStore.unlocked)

    private val runtimeFactory = DefaultHostRuntimeFactory(
        hostInfo = hostInfo,
        network = network,
        logBuffer = logBuffer,
        locale = localeService,
        haptics = haptics,
        auth = builder.auth,
        apps = appsView,
        events = events,
        services = ServiceRegistry(builder.services),
        clock = builder.clock,
        pushFor = { appId, logger ->
            pushes.getOrPut(appId) {
                RoutedPush(appId, registry.get(appId)?.manifest?.pushTopics.orEmpty(), topics, logger)
            }
        },
        navigationFor = { appId -> ShellNavigation(appId) },
        lifecycle = builder.lifecycle ?: PlatformLifecycle.state,
    )

    override val handler: AppHandler = AppHandler(
        registry = registry,
        runtimeFactory = runtimeFactory,
        events = _events,
        hostLifecycle = builder.lifecycle ?: PlatformLifecycle.state,
        snapshots = snapshots,
        clock = builder.clock,
        scope = scope,
        unlocked = { unlockStore.unlocked.value },
    )

    override val activation: ActivationPort = ActivationService(
        registry = registry,
        promo = builder.promo,
        unlocks = unlockStore,
        handler = handler,
        events = _events,
        scheme = builder.scheme,
        serviceCode = builder.serviceCode,
        json = json,
    )

    private val router = PushRouter(
        registry = registry,
        unlocked = { unlockStore.unlocked.value },
        isEnabled = { handler.isEnabled(it) },
        pushOf = { pushes[it] },
        pending = pendingRoute,
        notifier = builder.notifier,
        events = _events,
        clock = builder.clock,
        scheme = builder.scheme,
        json = json,
    )

    private val registrar = DeviceRegistrar(builder.auth, hostLogger, { registry.all().map { it.id } })

    private val recent = MutableStateFlow<List<SuperizerEvent>>(emptyList())

    override val diagnostics: DiagnosticsPort = object : DiagnosticsPort {
        override val log: StateFlow<List<String>> = logBuffer.lines
        override val recentEvents: StateFlow<List<SuperizerEvent>> = recent.asStateFlow()

        /**
         * D35. Four stores, because an app that has been "reset" but still shows yesterday's
         * screen, or still receives yesterday's topic, has not been reset.
         */
        override suspend fun reset(id: AppId) {
            PrefsStorageService.erase(id)
            topics.clear(id)
            handler.forgetSnapshot(id)
            unlockStore.lock(id)
            _events.tryEmit(SuperizerEvent.Reset(id))
        }

        override suspend fun unlock(id: AppId) {
            unlockStore.unlock(id)
        }

        override suspend fun simulatePush(payload: Map<String, String>) {
            router.route(IncomingPush(payload, tapped = payload["tapped"] == "true"))
        }
    }

    init {
        builder.apps.forEach { registry.register(it) }
        handler.observeLifecycle()
        router.attach(scope)
        registrar.attach(scope)
        // The last hundred, because the Service Menu is read after something has gone wrong and
        // the interesting part is always just before the end.
        scope.launch {
            _events.collect { event ->
                recent.value = (recent.value + event).takeLast(RECENT_EVENTS)
            }
        }
        scope.launch { handler.enableAll() }
    }

    /**
     * The four verbs of [NavigationService], and no fifth.
     *
     * `openApp` closes the current app *politely* first, so a form with unsaved edits can still say
     * no and the caller gets `Vetoed` rather than a silent loss (D22). Every other path into the
     * handler closes with `force`, because by then the user has already answered the question.
     */
    private inner class ShellNavigation(private val from: AppId) : NavigationService {

        override suspend fun openApp(id: AppId, config: AppConfig): Result<AppResult> {
            val target = registry.get(id) ?: return Result.failure(NavigationError.UnknownApp(id))
            if (target.metadata.hidden && id !in unlockStore.unlocked.value) {
                // An app cannot reveal a hidden app by asking for it (D8): if it could, the bench
                // app would be a key to every locked door in the build.
                hostLogger.warn("${from.value} tried to open locked app ${id.value}")
                return Result.failure(NavigationError.Locked(id))
            }
            if (!handler.close()) return Result.failure(NavigationError.Vetoed(from))
            return handler.launch(id, config)
                .map { _commands.tryEmit(ShellCommand.OpenApp(id)); AppResult() }
        }

        override suspend fun close() {
            if (handler.close()) _commands.tryEmit(ShellCommand.Close)
        }

        override fun openSettings() {
            _commands.tryEmit(ShellCommand.OpenSettings)
        }

        override fun openUrl(url: String): Boolean = openExternalUrl(url)
    }

    private companion object {
        const val RECENT_EVENTS = 100
    }
}

/** The session type the ports hand back, re-exported so a host file needs one import fewer. */
public typealias HostSession = AppSession
