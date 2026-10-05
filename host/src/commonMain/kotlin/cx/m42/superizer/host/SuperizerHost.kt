package cx.m42.superizer.host

import cx.m42.superizer.ActivateBanner
import cx.m42.superizer.ActivationPort
import cx.m42.superizer.HomeBanner
import cx.m42.superizer.HostScreen
import cx.m42.superizer.HostSection
import cx.m42.superizer.backup.BackupKeyName
import cx.m42.superizer.backup.BackupPort
import cx.m42.superizer.backup.HostBackupCipher
import cx.m42.superizer.diagnostics.DiagnosticsSource
import cx.m42.superizer.diagnostics.SelfTestRunner
import cx.m42.superizer.diagnostics.StorageDiagnostics
import cx.m42.superizer.host.backup.HostBackup
import cx.m42.superizer.host.diagnostics.DiagnosticsHub
import cx.m42.superizer.host.secrets.HostSecrets
import cx.m42.superizer.secrets.SecretVault
import cx.m42.superizer.secrets.SecretsPort
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
import cx.m42.superizer.host.lock.AppLockController
import cx.m42.superizer.host.lock.LockStore
import cx.m42.superizer.host.net.KtorNetworkService
import cx.m42.superizer.host.platform.PlatformLifecycle
import cx.m42.superizer.host.platform.PlatformScreen
import cx.m42.superizer.host.platform.openExternalUrl
import cx.m42.superizer.host.platform.openSecuritySettings
import cx.m42.superizer.host.platform.platformDeviceAuthenticator
import cx.m42.superizer.host.push.DeviceRegistrar
import cx.m42.superizer.host.push.IncomingPush
import cx.m42.superizer.host.push.Notifier
import cx.m42.superizer.host.push.PendingRoute
import cx.m42.superizer.host.push.PushRouter
import cx.m42.superizer.host.push.RoutedPush
import cx.m42.superizer.host.push.TopicStore
import cx.m42.superizer.host.storage.HomeStore
import cx.m42.superizer.host.storage.PrefsSnapshotStore
import cx.m42.superizer.host.storage.PrefsStorageService
import cx.m42.superizer.host.storage.UnlockStore
import cx.m42.superizer.lock.AppLockPort
import cx.m42.superizer.lock.AuthAvailability
import cx.m42.superizer.lock.AuthStrength
import cx.m42.superizer.lock.DeviceAuthenticator
import cx.m42.superizer.lock.UserPresence
import cx.m42.superizer.registry.AppHandler
import cx.m42.superizer.registry.AppRegistry
import cx.m42.superizer.registry.AppSession
import cx.m42.superizer.registry.listed
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
    internal var home: List<AppId> = emptyList()
    internal var authenticator: DeviceAuthenticator? = null
    internal var lockStrength: AuthStrength = AuthStrength.Any
    internal val boundServices: MutableMap<ServiceKey<*>, (AppId) -> Any> = mutableMapOf()
    internal var secretVault: SecretVault? = null
    internal var backupCipher: HostBackupCipher? = null
    internal var backupKeys: suspend () -> List<BackupKeyName> = { emptyList() }
    internal val diagnosticsSources: MutableList<DiagnosticsSource> = mutableListOf()
    internal var selfTest: SelfTestRunner? = null
    internal val hostSections: MutableList<HostSection> = mutableListOf()
    internal val homeBanners: MutableList<HomeBanner> = mutableListOf()
    internal val activateBanners: MutableList<ActivateBanner> = mutableListOf()
    internal val codeScreens: LinkedHashMap<String, HostScreen> = LinkedHashMap()
    internal var scanner: (suspend () -> String?)? = null
    internal var secretBackgroundLimitMs: Long = DEFAULT_SECRET_BACKGROUND_LIMIT_MS

    public fun host(info: HostInfo) {
        hostInfo = info
    }

    /** Registration order is the order the catalog and the Service Menu list apps in. */
    public fun register(app: SuperizerApp<*>) {
        apps += app
    }

    /**
     * What a fresh install has on Home (D48). Only the first run reads this: from then on Home is
     * the user's, and a build that changes the list changes nothing for anyone who already has one.
     * Registered apps that are not hidden, or the tile is skipped until an activation unlocks it.
     */
    public fun home(ids: List<AppId>) {
        home = ids
    }

    /** The same, by id string — `home("calculator")` — because a value class cannot be a vararg. */
    public fun home(vararg ids: String) {
        home = ids.map(::AppId)
    }

    /** How a host adds an optional service (D19). None in the MVP — the mechanism is the point. */
    public fun <T : Any> service(key: ServiceKey<T>, implementation: T) {
        services[key] = implementation
    }

    /**
     * A service bound to its caller (D146): each app gets what [factory] made for its id, once. The
     * SSH keyring is registered this way, so its sheet can name the app asking and grants stay per app.
     */
    public fun <T : Any> service(key: ServiceKey<T>, factory: (caller: AppId) -> T) {
        boundServices[key] = factory
    }

    /**
     * The vault that seals every app's `runtime.secrets` (05 §3.2). The platform's own — the Keystore,
     * the Secure Enclave — or the person's SSH key where there is no chip; none at all leaves secrets
     * stored as plain text until one arrives through `Superizer.secrets.reseal`, and the diagnostics say so.
     */
    public fun secretVault(vault: SecretVault?) {
        secretVault = vault
    }

    /** Encrypts backups to the person's keys and opens them with the keyring (05 §3.1). None: no backup. */
    public fun backupCipher(cipher: HostBackupCipher) {
        backupCipher = cipher
    }

    /** The names of the person's keys, for the backup's hint of what to import again (D170). */
    public fun backupKeys(provider: suspend () -> List<BackupKeyName>) {
        backupKeys = provider
    }

    /** Findings only the host's implementation can know: which chip, which key (06 §2). */
    public fun diagnosticsSource(source: DiagnosticsSource) {
        diagnosticsSources += source
    }

    /** The self-test (06 §3). Run once per install and per new version, and from Settings. */
    public fun selfTest(runner: SelfTestRunner) {
        selfTest = runner
    }

    /** A block of the host's own in Settings (07 §2.4). */
    public fun hostSection(section: HostSection) {
        hostSections += section
    }

    /**
     * A screen of the host's that nothing lists — not Settings, not the menu — and that a promo code
     * resolving to `ActivationResult.OpenHostScreen(screen.id)` opens (Unitool idea/09).
     */
    public fun hostScreen(screen: HostScreen) {
        require(screen.id !in codeScreens) { "duplicate host screen id: ${screen.id}" }
        codeScreens[screen.id] = screen
    }

    /** A line of the host's own above the tiles on Home. */
    public fun homeBanner(banner: HomeBanner) {
        homeBanners += banner
    }

    /** A block of the host's own above the promo code on Activate. */
    public fun activateBanner(banner: ActivateBanner) {
        activateBanners += banner
    }

    public fun promoCodes(resolver: PromoCodeResolver) {
        promo = resolver
    }

    public fun promoCodes(table: Map<String, ActivationResult>) {
        promo = LocalPromoCodes(table)
    }

    /**
     * The camera behind the Activate screen's scan button: reads one QR code and hands back its
     * text, or null. None — the default, and what desktop and the web have — draws no button.
     */
    public fun qrScanner(scan: suspend () -> String?) {
        scanner = scan
    }

    /**
     * How long a secret app (idea/09) may sit in the background and still be there on return.
     * Longer, and it is closed as the host comes back — the next visit is by code again.
     */
    public fun secretBackgroundLimit(millis: Long) {
        require(millis >= 0) { "secretBackgroundLimit must not be negative" }
        secretBackgroundLimitMs = millis
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

    /**
     * What the lock asks (06). The platform's own by default — the system's biometric sheet on
     * Android, none on desktop and the web — and a fake in a test.
     */
    public fun deviceAuthenticator(value: DeviceAuthenticator) {
        authenticator = value
    }

    /**
     * How strong a proof opening the lock takes, for the whole host (§5.7). [AuthStrength.Any] until
     * the vault's key is bound to authentication (06 §8.5, D142).
     */
    public fun lockStrength(value: AuthStrength) {
        lockStrength = value
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

    private val declared: HostInfo = requireNotNull(builder.hostInfo) {
        "Superizer.build { host(HostInfo(…)) } is required"
    }

    private val logBuffer = LogBuffer()
    private val hostLogger: Logger = ConsoleLogger("host", logBuffer, verbose = declared.debug)

    private val authenticator: DeviceAuthenticator =
        builder.authenticator ?: platformDeviceAuthenticator(declared.debug, hostLogger)

    /**
     * Confirmation for apps (D133), offered wherever the platform can ask at all. A device with no
     * screen lock *can*, once somebody sets one, so it gets the service too; desktop and the web
     * cannot and get none, and an app there does what it did before (§5.5).
     */
    private val services: Map<ServiceKey<*>, Any> = builder.services.let { declaredServices ->
        val offersPresence = UserPresence.Key !in declaredServices &&
            authenticator.availability(builder.lockStrength) != AuthAvailability.Unsupported
        if (!offersPresence) return@let declaredServices
        declaredServices + (UserPresence.Key to object : UserPresence {
            override suspend fun confirm(reason: String): Boolean = lockController.presence.confirm(reason)
        })
    }

    override val hostInfo: HostInfo = declared.copy(services = services.keys + builder.boundServices.keys)

    private val _events = MutableSharedFlow<SuperizerEvent>(replay = 0, extraBufferCapacity = 64)
    override val events: SharedFlow<SuperizerEvent> = _events.asSharedFlow()

    override val registry: AppRegistry = AppRegistry(hostInfo, _events)

    private val unlockStore = UnlockStore(_events, json)
    override val unlocked: StateFlow<Set<AppId>> = unlockStore.unlocked

    private val homeStore = HomeStore(builder.home, _events, json)
    override val home: StateFlow<List<AppId>> = homeStore.home

    override suspend fun addToHome(id: AppId) {
        val app = registry.get(id)
        if (app == null) {
            hostLogger.warn("addToHome(${id.value}) ignored: not registered")
            return
        }
        if (!app.listed(unlockStore.unlocked.value)) {
            // Home is not a way around D8: a tile for a locked app would be the reveal itself, and a
            // secret app never gets one at all (idea/09).
            hostLogger.warn("addToHome(${shownId(id)}) ignored: locked")
            return
        }
        homeStore.add(id)
    }

    override suspend fun removeFromHome(id: AppId) {
        homeStore.remove(id)
    }

    /** An id as the host's log may print it: a release build's log is the Service Menu's, and names no secret app. */
    private fun shownId(id: AppId): String = if (!declared.debug && registry.isSecret(id)) "…" else id.value

    /** D8 and D48 in one place: whatever reveals a hidden app also puts it on Home. */
    private fun reveal(id: AppId) {
        if (registry.isSecret(id)) {
            // Not even the Service Menu: a secret app is opened by its code, one visit at a time.
            hostLogger.warn("unlock(${shownId(id)}) ignored: a secret app is never unlocked")
            return
        }
        unlockStore.unlock(id)
        homeStore.add(id)
    }

    /** The inverse, and the only path that takes a tile away without the user's own long press. */
    private suspend fun conceal(id: AppId) {
        homeStore.remove(id)
        unlockStore.lock(id)
        // A locked app must not stay on screen or come back through its snapshot.
        if (handler.current.value?.app?.id == id) handler.close(force = true)
        handler.forgetSnapshot(id)
    }

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

    private val pendingRoute = PendingRoute(_events) { id ->
        registry.get(id)?.manifest?.protection?.sensitive == true
    }
    override val route: RoutePort = pendingRoute

    private val snapshots = PrefsSnapshotStore(json)

    private val hostSecrets = HostSecrets(builder.secretVault)
    override val secrets: SecretsPort = hostSecrets

    private val appsView = RegistryView(registry, { handler }, unlockStore.unlocked)

    /** The events an app may hear: none about a secret app but its own (idea/09). */
    private fun eventsFor(appId: AppId): SharedFlow<SuperizerEvent> =
        FilteredSharedFlow(events) { it.appId == appId || !registry.isSecret(it.appId) }

    private val runtimeFactory = DefaultHostRuntimeFactory(
        hostInfo = hostInfo,
        network = network,
        logBuffer = logBuffer,
        locale = localeService,
        haptics = haptics,
        auth = builder.auth,
        apps = appsView,
        events = events,
        eventsFor = ::eventsFor,
        quiet = { appId -> !declared.debug && registry.isSecret(appId) },
        services = ServiceRegistry(services, builder.boundServices.toMap()),
        clock = builder.clock,
        pushFor = { appId, logger ->
            pushes.getOrPut(appId) {
                RoutedPush(appId, registry.get(appId)?.manifest?.pushTopics.orEmpty(), topics, logger)
            }
        },
        navigationFor = { appId -> ShellNavigation(appId) },
        lifecycle = builder.lifecycle ?: PlatformLifecycle.state,
        secretsFor = { appId -> hostSecrets.forApp(appId) },
        diagnosticsFor = { appId -> diagnosticsHub.forApp(appId) },
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
        onUnlock = ::reveal,
        scanner = builder.scanner,
        screens = builder.codeScreens.keys.toSet(),
    )

    private val lockController = AppLockController(
        apps = registry.apps,
        authenticator = authenticator,
        store = LockStore(json),
        clock = builder.clock,
        lifecycle = builder.lifecycle ?: PlatformLifecycle.state,
        screenOff = PlatformScreen.off,
        screenOn = PlatformScreen::interactive,
        strength = builder.lockStrength,
        deviceSettings = ::openSecuritySettings,
        logger = hostLogger,
    )
    override val lock: AppLockPort = lockController

    private val hostBackup = HostBackup(
        host = hostInfo,
        clock = builder.clock,
        registry = registry,
        secrets = hostSecrets,
        cipher = builder.backupCipher,
        keyNames = builder.backupKeys,
        confirm = { reason -> lockController.presence.confirm(reason) },
        replaced = { id ->
            // Data changed under a live screen: close it, and let its snapshot go with the old data.
            if (handler.current.value?.app?.id == id) handler.close(force = true)
            handler.forgetSnapshot(id)
        },
    )
    override val backup: BackupPort = hostBackup

    private val diagnosticsHub: DiagnosticsHub = DiagnosticsHub(
        host = hostInfo,
        clock = builder.clock,
        secrets = hostSecrets,
        lastBackupAt = hostBackup.lastBackupAt,
        sources = builder.diagnosticsSources.toList(),
        runner = builder.selfTest,
        scope = scope,
        logger = hostLogger,
        unlisted = registry::isSecret,
    )
    override val storage: StorageDiagnostics = diagnosticsHub

    override val hostSections: List<HostSection> = builder.hostSections.toList()
    override val homeBanners: List<HomeBanner> = builder.homeBanners.toList()
    override val activateBanners: List<ActivateBanner> = builder.activateBanners.toList()
    override val codeScreens: Map<String, HostScreen> = builder.codeScreens.toMap()

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
            HostSecrets.erase(id)
            topics.clear(id)
            conceal(id)
            _events.tryEmit(SuperizerEvent.Reset(id))
        }

        override suspend fun unlock(id: AppId) {
            reveal(id)
        }

        override suspend fun lock(id: AppId) {
            conceal(id)
        }

        override suspend fun simulatePush(payload: Map<String, String>) {
            router.route(IncomingPush(payload, tapped = payload["tapped"] == "true"))
        }
    }

    init {
        builder.apps.forEach { registry.register(it) }
        forgetSecretsRevealed()
        handler.observeLifecycle()
        closeSecretAfterLongBackground(
            builder.lifecycle ?: PlatformLifecycle.state,
            builder.clock,
            builder.secretBackgroundLimitMs,
        )
        lockController.attach(scope)
        router.attach(scope)
        registrar.attach(scope)
        // The last hundred, because the Service Menu is read after something has gone wrong and
        // the interesting part is always just before the end.
        scope.launch {
            _events.collect { event ->
                // The Service Menu opens by a code in a release build, and that code is not the
                // secret app's (idea/09): what a secret app did stays out of its list there.
                if (!declared.debug && registry.isSecret(event.appId)) return@collect
                recent.value = (recent.value + event).takeLast(RECENT_EVENTS)
            }
        }
        scope.launch { handler.enableAll() }
        // After the apps: a self-test on first run must not hold up the first frame's apps.
        scope.launch { runCatching { diagnosticsHub.onStart() }.onFailure { hostLogger.warn("diagnostics at start failed", it) } }
    }

    /**
     * A build that turned a hidden app secret finds it unlocked and on Home from before (idea/09):
     * both go, or the tile would outlive the decision that it must not exist.
     */
    private fun forgetSecretsRevealed() {
        registry.all().filter { it.metadata.secret }.forEach { app ->
            unlockStore.lock(app.id)
            homeStore.remove(app.id)
        }
    }

    /**
     * Back from the background after [limitMs] or more with a secret app open: it is closed and the
     * shell goes Home, so the next visit is by code again (idea/09, question 126). A short trip —
     * copying something from another app — finds it where it was left.
     */
    private fun closeSecretAfterLongBackground(
        lifecycle: StateFlow<cx.m42.superizer.runtime.HostLifecycle>,
        clock: Clock,
        limitMs: Long,
    ) {
        scope.launch {
            var leftAt: Long? = null
            lifecycle.collect { now ->
                when (now) {
                    cx.m42.superizer.runtime.HostLifecycle.Background -> leftAt = clock.now()
                    cx.m42.superizer.runtime.HostLifecycle.Foreground -> {
                        val away = leftAt?.let { clock.now() - it }
                        leftAt = null
                        val open = handler.current.value?.app ?: return@collect
                        if (open.metadata.secret && away != null && away >= limitMs) {
                            handler.close(force = true)
                            _commands.tryEmit(ShellCommand.Close)
                        }
                    }
                }
            }
        }
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
            // A secret app is not there for anybody to ask for (idea/09): `Locked` would say it is.
            if (target.metadata.secret) return Result.failure(NavigationError.UnknownApp(id))
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

/** Five minutes: long enough to copy a code from a message, short enough to count as leaving. */
private const val DEFAULT_SECRET_BACKGROUND_LIMIT_MS: Long = 5L * 60 * 1000

/** The session type the ports hand back, re-exported so a host file needs one import fewer. */
public typealias HostSession = AppSession
