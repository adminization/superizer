package cx.m42.superizer

import cx.m42.superizer.app.AppConfig
import cx.m42.superizer.app.AppId
import cx.m42.superizer.event.SuperizerEvent
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
import cx.m42.superizer.runtime.NavigationService
import cx.m42.superizer.runtime.NetworkRequest
import cx.m42.superizer.runtime.NetworkResponse
import cx.m42.superizer.runtime.NetworkService
import cx.m42.superizer.runtime.PushMessage
import cx.m42.superizer.runtime.PushService
import cx.m42.superizer.runtime.ServiceKey
import cx.m42.superizer.runtime.StorageService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A runtime for `core`'s own tests.
 *
 * Written here rather than imported from `superizer:testing`, because `testing` depends on `core`
 * and the arrow does not get to point both ways. It is deliberately the dullest possible one: these
 * tests are about the handler, and a clever fake would put its own behaviour in the way.
 */
internal class TestRuntime(
    override val appId: AppId,
    override val scope: CoroutineScope,
    private val sharedEvents: SharedFlow<SuperizerEvent>,
    private val lifecycleState: StateFlow<HostLifecycle>,
) : InstanceRuntime {

    override val host: HostInfo = testHost()
    override val storage: StorageService = object : StorageService {
        val map = mutableMapOf<String, String>()

        override suspend fun get(key: String): String? = map[key]

        override suspend fun set(key: String, value: String) {
            map[key] = value
        }

        override suspend fun remove(key: String) {
            map.remove(key)
        }

        override suspend fun keys(): Set<String> = map.keys
    }

    override val network: NetworkService = object : NetworkService {
        override val online: StateFlow<Boolean> = MutableStateFlow(true)

        override suspend fun request(request: NetworkRequest): NetworkResponse =
            error("a core test must not reach the network")
    }

    override val logger: Logger = object : Logger {
        override fun debug(message: String, throwable: Throwable?) = Unit

        override fun info(message: String, throwable: Throwable?) = Unit

        override fun warn(message: String, throwable: Throwable?) = Unit

        override fun error(message: String, throwable: Throwable?) = Unit
    }

    override val analytics: AnalyticsService = object : AnalyticsService {
        override fun event(name: String, params: Map<String, String>) = Unit

        override fun screen(name: String) = Unit
    }

    override val locale: LocaleService = object : LocaleService {
        override val langTag: StateFlow<String> = MutableStateFlow("ru")

        override fun <S> pick(tables: Map<String, S>, fallback: S): S = tables["ru"] ?: fallback
    }

    override val haptics: HapticsService = HapticsService { }

    override val push: PushService = object : PushService {
        override val enabled: StateFlow<Boolean> = MutableStateFlow(false)
        override val subscriptions: StateFlow<Set<String>> = MutableStateFlow(emptySet())
        override val messages: SharedFlow<PushMessage> = MutableSharedFlow()

        override suspend fun requestPermission(): Boolean = false

        override suspend fun subscribe(topic: String) = Unit

        override suspend fun unsubscribe(topic: String) = Unit
    }

    override val auth: AuthService = object : AuthService {
        override val session: StateFlow<AuthSession?> = MutableStateFlow(null)

        override suspend fun signIn(): AuthSession? = null

        override suspend fun signOut() = Unit
    }

    override val apps: AppsService = object : AppsService {
        override fun list(): List<AppSummary> = emptyList()

        override fun get(id: AppId): AppSummary? = null
    }

    override val events: SharedFlow<SuperizerEvent> get() = sharedEvents
    override val lifecycle: StateFlow<HostLifecycle> get() = lifecycleState
    override val clock: Clock = Clock { 1_700_000_000_000 }

    override val navigation: NavigationService = object : NavigationService {
        override suspend fun openApp(id: AppId, config: AppConfig): Result<AppResult> =
            Result.success(AppResult())

        override suspend fun close() = Unit

        override fun openSettings() = Unit

        override fun openUrl(url: String): Boolean = false
    }

    override fun <T : Any> service(key: ServiceKey<T>): T? = null
}

internal class TestRuntimeFactory(
    private val events: SharedFlow<SuperizerEvent>,
    private val lifecycle: StateFlow<HostLifecycle>,
) : HostRuntimeFactory {
    override fun createApp(appId: AppId, scope: CoroutineScope): AppRuntime =
        TestRuntime(appId, scope, events, lifecycle)

    override fun createInstance(app: AppRuntime, scope: CoroutineScope): InstanceRuntime =
        TestRuntime(app.appId, scope, events, lifecycle)
}
