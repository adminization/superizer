package cx.m42.superizer.host

import cx.m42.superizer.app.AppId
import cx.m42.superizer.diagnostics.AppDiagnostics
import cx.m42.superizer.diagnostics.Finding
import cx.m42.superizer.event.SuperizerEvent
import cx.m42.superizer.host.platform.PlatformLifecycle
import cx.m42.superizer.host.push.RoutedPush
import cx.m42.superizer.host.storage.PrefsStorageService
import cx.m42.superizer.runtime.AnalyticsService
import cx.m42.superizer.runtime.AppRuntime
import cx.m42.superizer.runtime.AppsService
import cx.m42.superizer.runtime.AuthService
import cx.m42.superizer.runtime.Clock
import cx.m42.superizer.runtime.HapticsService
import cx.m42.superizer.runtime.HostInfo
import cx.m42.superizer.runtime.HostLifecycle
import cx.m42.superizer.runtime.HostRuntimeFactory
import cx.m42.superizer.runtime.InstanceRuntime
import cx.m42.superizer.runtime.LocaleService
import cx.m42.superizer.runtime.Logger
import cx.m42.superizer.runtime.NavigationService
import cx.m42.superizer.runtime.NetworkService
import cx.m42.superizer.runtime.PushService
import cx.m42.superizer.runtime.ServiceKey
import cx.m42.superizer.runtime.StorageService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * One app's platform, assembled.
 *
 * Every service here is either scoped to the app id (storage, logger, analytics, push) or shared
 * and read-only (locale, clock, events). That split is the whole of what "the host gives an app a
 * runtime" means: the scoped ones are why two apps cannot see each other's data, and the shared
 * ones are why they agree about what language it is.
 */
internal class DefaultAppRuntime(
    override val appId: AppId,
    override val host: HostInfo,
    override val storage: StorageService,
    override val network: NetworkService,
    override val logger: Logger,
    override val analytics: AnalyticsService,
    override val locale: LocaleService,
    override val haptics: HapticsService,
    override val push: PushService,
    override val auth: AuthService,
    override val apps: AppsService,
    override val events: SharedFlow<SuperizerEvent>,
    override val lifecycle: StateFlow<HostLifecycle>,
    override val clock: Clock,
    override val scope: CoroutineScope,
    override val secrets: StorageService,
    override val diagnostics: AppDiagnostics,
    private val services: ServiceRegistry,
) : AppRuntime {
    override fun <T : Any> service(key: ServiceKey<T>): T? = services.get(key, appId)
}

/** For a factory built without a host behind it (a standalone wrapper): secrets that last as long as the process. */
private class InMemorySecrets : StorageService {
    private val values = mutableMapOf<String, String>()

    override suspend fun get(key: String): String? = values[key]

    override suspend fun set(key: String, value: String) {
        values[key] = value
    }

    override suspend fun remove(key: String) {
        values.remove(key)
    }

    override suspend fun keys(): Set<String> = values.keys.toSet()
}

private object NoDiagnostics : AppDiagnostics {
    override val findings: StateFlow<List<Finding>> = MutableStateFlow(emptyList())
}

/**
 * The same thing plus navigation and a scope that dies with the screen (D15).
 *
 * It delegates rather than copies, so a service added to the app level is on the instance level
 * without a second line — and so the two can never disagree about which storage an app is using.
 */
internal class DefaultInstanceRuntime(
    app: AppRuntime,
    override val navigation: NavigationService,
    /**
     * Declared here rather than left to the delegate on purpose: `by app` would hand back the
     * *app-level* scope, and screen work that outlives its screen is the bug D15 exists to prevent.
     */
    override val scope: CoroutineScope,
) : InstanceRuntime, AppRuntime by app

/**
 * Builds both levels for every app in the host.
 *
 * A factory rather than construction at the call site, because a standalone wrapper and a test both
 * need to substitute the whole pair at once (04) — and because the handler must not be the thing
 * that knows how a runtime is made.
 */
public class DefaultHostRuntimeFactory(
    private val hostInfo: HostInfo,
    private val network: NetworkService,
    private val logBuffer: LogBuffer,
    private val locale: LocaleService,
    private val haptics: HapticsService,
    private val auth: AuthService,
    private val apps: AppsService,
    private val events: SharedFlow<SuperizerEvent>,
    private val services: ServiceRegistry,
    private val clock: Clock,
    private val pushFor: (AppId, Logger) -> RoutedPush,
    private val navigationFor: (AppId) -> NavigationService,
    private val lifecycle: StateFlow<HostLifecycle> = PlatformLifecycle.state,
    private val secretsFor: (AppId) -> StorageService = { InMemorySecrets() },
    private val diagnosticsFor: (AppId) -> AppDiagnostics = { NoDiagnostics },
) : HostRuntimeFactory {

    override fun createApp(appId: AppId, scope: CoroutineScope): AppRuntime {
        val logger = ConsoleLogger("app:${appId.value}", logBuffer, verbose = hostInfo.debug)
        return DefaultAppRuntime(
            appId = appId,
            host = hostInfo,
            storage = PrefsStorageService(appId),
            network = network,
            logger = logger,
            analytics = LoggingAnalytics(appId, logger),
            locale = locale,
            haptics = haptics,
            push = pushFor(appId, logger),
            auth = auth,
            apps = apps,
            events = events,
            lifecycle = lifecycle,
            clock = clock,
            scope = scope,
            secrets = secretsFor(appId),
            diagnostics = diagnosticsFor(appId),
            services = services,
        )
    }

    override fun createInstance(app: AppRuntime, scope: CoroutineScope): InstanceRuntime =
        DefaultInstanceRuntime(app, navigationFor(app.appId), scope)
}
