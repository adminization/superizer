package cx.m42.superizer.host

import cx.m42.superizer.HostSettingsPort
import cx.m42.superizer.LanguageOption
import cx.m42.superizer.app.AppId
import cx.m42.superizer.host.platform.deviceLanguageTag
import cx.m42.superizer.host.platform.platformHapticTick
import cx.m42.superizer.host.storage.HostKeys
import cx.m42.superizer.host.storage.SafePrefs
import cx.m42.superizer.runtime.AnalyticsService
import cx.m42.superizer.runtime.AppSummary
import cx.m42.superizer.runtime.AppsService
import cx.m42.superizer.runtime.AuthService
import cx.m42.superizer.runtime.AuthSession
import cx.m42.superizer.runtime.Clock
import cx.m42.superizer.runtime.HapticsService
import cx.m42.superizer.runtime.LocaleService
import cx.m42.superizer.runtime.Logger
import cx.m42.superizer.runtime.ServiceKey
import cx.m42.superizer.registry.AppHandler
import cx.m42.superizer.registry.AppRegistry
import cx.m42.superizer.registry.AppState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Epoch milliseconds from whatever the platform calls a clock (D30). */
public expect object SystemClock : Clock

/**
 * A ring buffer of everything that was logged, at every level, for the Service Menu to show (D33).
 *
 * Five hundred lines: enough to hold the launch of an app and what it did next, small enough that
 * nobody has to think about it. The console gets a filtered view; this gets all of it, because the
 * line you want is always the one that was not important enough to print.
 */
public class LogBuffer(private val capacity: Int = 500) {
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    public val lines: StateFlow<List<String>> get() = _lines.asStateFlow()

    public fun add(line: String) {
        val next = _lines.value + line
        _lines.value = if (next.size > capacity) next.takeLast(capacity) else next
    }

    public fun clear() {
        _lines.value = emptyList()
    }
}

/**
 * Tagged with the app's id, so two apps that both log "failed" are two apps you can tell apart
 * (D33). In a release build the console sees warnings and errors only — the rest is noise there,
 * and some of it is somebody's data.
 */
public class ConsoleLogger(
    private val tag: String,
    private val buffer: LogBuffer,
    private val verbose: Boolean,
) : Logger {
    override fun debug(message: String, throwable: Throwable?) {
        write("D", message, throwable, toConsole = verbose)
    }

    override fun info(message: String, throwable: Throwable?) {
        write("I", message, throwable, toConsole = verbose)
    }

    override fun warn(message: String, throwable: Throwable?) {
        write("W", message, throwable, toConsole = true)
    }

    override fun error(message: String, throwable: Throwable?) {
        write("E", message, throwable, toConsole = true)
    }

    private fun write(level: String, message: String, throwable: Throwable?, toConsole: Boolean) {
        val line = "$level/$tag: $message" + (throwable?.let { " — ${it::class.simpleName}: ${it.message}" } ?: "")
        buffer.add(line)
        if (toConsole) println(line)
    }
}

/**
 * Analytics, as the interface an app writes against and a log line until there is a backend.
 *
 * The app's own name is prefixed with its id before it leaves (D33), which is the same reason the
 * logger is tagged: `error` from two apps is one event nobody can act on.
 */
public class LoggingAnalytics(private val appId: AppId, private val logger: Logger) : AnalyticsService {
    override fun event(name: String, params: Map<String, String>) {
        logger.info("analytics ${appId.value}.$name ${if (params.isEmpty()) "" else params}")
    }

    override fun screen(name: String) {
        logger.info("analytics screen ${appId.value}.$name")
    }
}

/**
 * The language in effect, and where the decision comes from: the choice stored on this device, then
 * the device's own language, then the fallback.
 *
 * A `StateFlow` and not a global `mutableStateOf` (which is what Unitool had): an app reads it from
 * a coroutine as readily as from a composition, and a fake in a test is one line.
 */
public class AppLocaleService(
    private val supported: List<LanguageOption>,
    private val fallback: String,
) : LocaleService, HostSettingsPort {

    private val _langTag = MutableStateFlow(resolve())
    override val langTag: StateFlow<String> get() = _langTag.asStateFlow()

    override val languages: List<LanguageOption> get() = supported

    private val _haptics = MutableStateFlow(SafePrefs.get(HostKeys.HAPTICS) != "off")
    override val haptics: StateFlow<Boolean> get() = _haptics.asStateFlow()

    /** Picks a table by primary subtag; the one place `startsWith("ru")` is written (D11). */
    override fun <S> pick(tables: Map<String, S>, fallback: S): S {
        val primary = _langTag.value.substringBefore('-').substringBefore('_').lowercase()
        return tables[primary] ?: fallback
    }

    override fun chooseLanguage(tag: String) {
        _langTag.value = tag
        SafePrefs.put(HostKeys.LANGUAGE, tag)
    }

    override fun setHaptics(enabled: Boolean) {
        _haptics.value = enabled
        SafePrefs.put(HostKeys.HAPTICS, if (enabled) "on" else "off")
    }

    /** Pins a language for a test, bypassing both the device and the stored choice. */
    public fun useForTesting(tag: String) {
        _langTag.value = tag
    }

    /**
     * Re-resolves, for a host that has reason to think the device's answer changed under it —
     * Android 13's per-app language picker restarts the activity in a warm process, so the stored
     * choice has to be re-read rather than assumed still in effect.
     */
    public fun refreshFromDevice() {
        _langTag.value = resolve()
    }

    private fun resolve(): String {
        val stored = SafePrefs.get(HostKeys.LANGUAGE)?.takeIf { supports(it) }
        if (stored != null) return stored
        val device = deviceLanguageTag().takeIf { supports(it) }
        return device ?: fallback
    }

    private fun supports(tag: String): Boolean {
        val primary = tag.substringBefore('-').substringBefore('_').lowercase()
        return supported.any { it.tag == primary }
    }
}

/**
 * A no-op when the switch in Settings is off (D38).
 *
 * Checked here rather than at every call site, so an app calling `haptics.tick()` never has to know
 * the host has a setting — and so turning it off is one decision made in one place.
 */
public class PlatformHaptics(private val enabled: StateFlow<Boolean>) : HapticsService {
    override fun tick() {
        if (!enabled.value) return
        platformHapticTick()
    }
}

/** Anonymous, and honest about it. The shape is fixed so apps can be written against it today. */
public class AnonymousAuth : AuthService {
    override val session: StateFlow<AuthSession?> = MutableStateFlow(null).asStateFlow()

    override suspend fun signIn(): AuthSession? = null

    override suspend fun signOut(): Unit = Unit
}

/** The registry, as an app is allowed to see it: read-only, no register, no handler (§19). */
public class RegistryView(
    private val registry: AppRegistry,
    private val handler: () -> AppHandler?,
    private val unlocked: StateFlow<Set<AppId>>,
) : AppsService {
    override fun list(): List<AppSummary> = registry.all().map { summary(it.id) !! }

    override fun get(id: AppId): AppSummary? = summary(id)

    private fun summary(id: AppId): AppSummary? {
        val app = registry.get(id) ?: return null
        return AppSummary(
            id = app.id,
            version = app.version,
            metadata = app.metadata,
            state = handler()?.states?.value?.get(id) ?: AppState.Registered,
            unlocked = id in unlocked.value,
        )
    }
}

/**
 * The optional services this host provides (D19). Empty in the MVP, and that is the point: the
 * mechanism is in place and costs nothing, so the first host that gains a camera adds one line
 * rather than a field on every runtime implementation.
 */
public class ServiceRegistry(private val services: Map<ServiceKey<*>, Any> = emptyMap()) {
    public val keys: Set<ServiceKey<*>> get() = services.keys

    @Suppress("UNCHECKED_CAST")
    public fun <T : Any> get(key: ServiceKey<T>): T? = services[key] as T?
}
