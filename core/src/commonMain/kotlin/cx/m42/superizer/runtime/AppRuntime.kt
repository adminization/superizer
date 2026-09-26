package cx.m42.superizer.runtime

import androidx.compose.runtime.staticCompositionLocalOf
import cx.m42.superizer.app.AppId
import cx.m42.superizer.diagnostics.AppDiagnostics
import cx.m42.superizer.event.SuperizerEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The app's whole view of the platform, at the *app* level: created once per enable, scoped to one
 * app id (storage namespaced, logger tagged, analytics prefixed), alive until disable.
 *
 * This is what `setup(ctx)` sees and what background work runs on — a push topic subscription or a
 * periodic refresh has to keep working while no screen is open (D15).
 *
 * An app holds this and nothing else of the host. That is Adminizer's rule, kept word for word.
 */
public interface AppRuntime {
    public val appId: AppId
    public val host: HostInfo

    // The core set. Every host provides all of them, so they are fields rather than lookups.
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

    /**
     * Key/value like [storage], but every value is sealed by the host before it is written (05 §3.2,
     * D171): by the chip where the device has one, by the person's SSH key where it does not. The app
     * knows nothing about either. Namespaced like [storage] and backed up with it.
     *
     * `get` throws [cx.m42.superizer.secrets.SecretsUnavailableException] when a value is there and
     * cannot be opened now — never null for that, which would read as "nothing stored". `set`
     * throws when the host cannot seal, and has then written nothing. Contract 3.
     */
    public val secrets: StorageService

    /** How this app's secrets and the device are protected, as findings the host's words describe (06 §1). Contract 3. */
    public val diagnostics: AppDiagnostics

    /** Foreground/Background of the host process (D17). */
    public val lifecycle: StateFlow<HostLifecycle>

    /** Wall clock (D30), injected so snapshots, push timestamps and "older than a day" are testable. */
    public val clock: Clock

    /**
     * Optional services by key (D19): a camera, biometrics, a share sheet — whatever a host adds
     * later without every [AppRuntime] implementation having to change. Null when the host has
     * none; an app that cannot live without one declares it in `manifest.requires` and never sees
     * null.
     */
    public fun <T : Any> service(key: ServiceKey<T>): T?

    /** Cancelled by the handler on disable. */
    public val scope: CoroutineScope
}

/**
 * What a live screen gets (D15): everything above, plus navigation and a scope that dies with the
 * instance. Composables inside `Content()` reach it through [LocalAppRuntime].
 */
public interface InstanceRuntime : AppRuntime {
    public val navigation: NavigationService

    /**
     * Instance-scoped, and it shadows [AppRuntime.scope] on purpose: work a screen starts must not
     * outlive the screen.
     */
    override val scope: CoroutineScope
}

/**
 * A service the manifest declares as required (D47).
 *
 * The registry has already refused to register an app whose `requires` the host cannot satisfy, so
 * this either answers or the app forgot to declare the key — which is a bug in the app and fails
 * in its first test rather than as a null somewhere in production.
 */
public fun <T : Any> AppRuntime.require(key: ServiceKey<T>): T =
    service(key) ?: error("$key is not declared in manifest.requires of $appId")

public enum class HostLifecycle { Foreground, Background }

/** Epoch milliseconds. `SystemClock` in a host, `FakeClock` in a test (D30). */
public fun interface Clock {
    public fun now(): Long
}

/** [Ios] arrived with contract 2: a `when` over this written against contract 1 needs a branch for it. */
public enum class Platform { Android, Desktop, Web, Ios }

/** Read-only facts about whoever is hosting. */
public data class HostInfo(
    val name: String,
    val version: String,
    /** The build stamp — commit, with a `*` when the tree was dirty. */
    val build: String,
    val platform: Platform,
    /** Contract revision this host implements (D14); apps declare the oldest they tolerate. */
    val contractVersion: Int,
    /**
     * A debug build (D34). Unlocks the seven-tap gesture into the Service Menu and verbose console
     * logging — in a release the Service Menu has one door, and it is the promo code.
     */
    val debug: Boolean = false,
    /** Optional services this host provides; the registry checks `manifest.requires` against it. */
    val services: Set<ServiceKey<*>> = emptySet(),
)

/**
 * Builds both levels of runtime. An interface in `core` so a host, a standalone wrapper and a test
 * can each supply their own — and so the two levels are always created by the same thing.
 */
public interface HostRuntimeFactory {
    /** One per enabled app, cancelled on disable. */
    public fun createApp(appId: AppId, scope: CoroutineScope): AppRuntime

    /** One per launch, cancelled on dispose. */
    public fun createInstance(app: AppRuntime, scope: CoroutineScope): InstanceRuntime
}

/**
 * For composables inside `AppInstance.Content()`. Provided by the host's app container, which is
 * also what puts the `<appId>:root` test tag on the node (D39).
 */
public val LocalAppRuntime: androidx.compose.runtime.ProvidableCompositionLocal<InstanceRuntime> =
    staticCompositionLocalOf { error("No AppRuntime: this composable is not inside an app container") }
