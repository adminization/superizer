package cx.m42.superizer.registry

import androidx.compose.runtime.Composable
import cx.m42.superizer.app.AppConfig
import cx.m42.superizer.app.AppConfigSpec
import cx.m42.superizer.app.AppId
import cx.m42.superizer.app.AppInstance
import cx.m42.superizer.app.AppSetupContext
import cx.m42.superizer.app.Disposable
import cx.m42.superizer.app.SuperizerApp
import cx.m42.superizer.event.SuperizerEvent
import cx.m42.superizer.runtime.AppRuntime
import cx.m42.superizer.runtime.Clock
import cx.m42.superizer.runtime.HostLifecycle
import cx.m42.superizer.runtime.HostRuntimeFactory
import cx.m42.superizer.runtime.InstanceRuntime
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** The specification's §10 states, as the handler publishes them. */
public enum class AppState { Registered, Enabled, Creating, Launching, Active, Closing, Disposed, Failed }

/** One open app, as the host's container sees it. */
public class AppSession internal constructor(
    public val app: SuperizerApp<*>,
    public val config: AppConfig,
    public val instance: AppInstance,
    public val runtime: InstanceRuntime,
    public val state: StateFlow<AppState>,
)

/**
 * What survives a process death (D16): enough to relaunch and hand the instance its snapshot back.
 * Stored by the host under one key, because there is one session (D4).
 */
@Serializable
public data class SessionSnapshot(
    val appId: AppId,
    val config: AppConfig,
    val state: JsonObject?,
    val savedAt: Long,
)

/** Where [SessionSnapshot] lives. Host storage, one key; a fake in tests. */
public interface SnapshotStore {
    public suspend fun save(snapshot: SessionSnapshot)
    public suspend fun load(): SessionSnapshot?
    public suspend fun clear()
}

/**
 * The state machine with one current session (D4).
 *
 * Every step of §9 is a step here and publishes an event, which is what makes the lifecycle
 * *observable* rather than merely documented: the bench app and the Service Menu read the same
 * flow, and a test asserts the order rather than the outcome.
 *
 * Nothing here throws at the caller. A failure becomes `Failed` plus an event, because the host
 * has a screen to show for that and no screen to show for an exception.
 */
public class AppHandler(
    private val registry: AppRegistry,
    private val runtimeFactory: HostRuntimeFactory,
    private val events: MutableSharedFlow<SuperizerEvent>,
    private val hostLifecycle: StateFlow<HostLifecycle>,
    private val snapshots: SnapshotStore,
    private val clock: Clock,
    /**
     * The host's own scope. App and instance scopes are children of it, so shutting the host down
     * cancels everything an app started — and so a test can supply its own dispatcher instead of
     * the handler reaching for `Dispatchers.Main`, which does not exist in a unit test.
     */
    private val scope: CoroutineScope,
    /** Hidden apps this device has unlocked (D8); a launch of anything else needs `force`. */
    private val unlocked: () -> Set<AppId> = { emptySet() },
) {
    private val _current = MutableStateFlow<AppSession?>(null)
    public val current: StateFlow<AppSession?> get() = _current.asStateFlow()

    private val _states = MutableStateFlow<Map<AppId, AppState>>(emptyMap())
    public val states: StateFlow<Map<AppId, AppState>> get() = _states.asStateFlow()

    private val _settingsSections =
        MutableStateFlow<List<Pair<AppId, @Composable (AppRuntime) -> Unit>>>(emptyList())

    /** Blocks apps contributed to the host's Settings screen, in registration order. */
    public val settingsSections: StateFlow<List<Pair<AppId, @Composable (AppRuntime) -> Unit>>>
        get() = _settingsSections.asStateFlow()

    private class Enabled(
        val runtime: AppRuntime,
        val scope: CoroutineScope,
        val disposers: MutableList<Disposable> = mutableListOf(),
        val deepLinks: MutableMap<String, (Map<String, String>) -> AppConfig> = mutableMapOf(),
        val sections: MutableList<@Composable (AppRuntime) -> Unit> = mutableListOf(),
    )

    private val enabled = LinkedHashMap<AppId, Enabled>()

    /**
     * One at a time. Launch and close both run several suspension points over the same single
     * current session, and a second tap while the first is still in `onLaunch` must queue rather
     * than interleave — the race tests in 12 §4.7 are this mutex's specification.
     */
    private val gate = Mutex()

    /** The app-level runtime, for the push router and anything else that addresses a closed app. */
    public fun appRuntime(id: AppId): AppRuntime? = enabled[id]?.runtime

    public fun isEnabled(id: AppId): Boolean = enabled.containsKey(id)

    /** The handler for a declared deep-link path, or null when the app serves no such path. */
    public fun deepLinkHandler(id: AppId, path: String): ((Map<String, String>) -> AppConfig)? =
        enabled[id]?.deepLinks?.get(path)

    /**
     * `setup(ctx)` for every registered app, with an app-level runtime each (D15). Once, at startup.
     *
     * Afterwards what `ctx` registered is checked against the manifest (D47): a deep link it did
     * not declare is a `SetupMismatch` and the app stays disabled. Declared but *not* registered is
     * only a warning — a path may be wired conditionally.
     */
    public suspend fun enableAll() {
        registry.all().forEach { enable(it.id) }
    }

    public suspend fun enable(id: AppId): Boolean = gate.withLock { enableLocked(id) }

    private fun enableLocked(id: AppId): Boolean {
        if (enabled.containsKey(id)) return true
        val app = registry.get(id) ?: return false
        setState(id, AppState.Registered)

        val appScope = childScope(id)
        val runtime = runtimeFactory.createApp(id, appScope)
        val slot = Enabled(runtime, appScope)
        val ctx = Context(slot, id)

        val failure = runCatching { app.setup(ctx) }.exceptionOrNull()
        if (failure != null) {
            appScope.cancel()
            events.tryEmit(SuperizerEvent.LaunchFailed(id, "setup failed: ${failure.message}", failure))
            setState(id, AppState.Failed)
            return false
        }

        val undeclared = slot.deepLinks.keys - app.manifest.deepLinks
        if (undeclared.isNotEmpty()) {
            appScope.cancel()
            slot.disposers.asReversed().forEach { runCatching { it.dispose() } }
            events.tryEmit(SuperizerEvent.SetupMismatch(id, "deep links not in the manifest: $undeclared"))
            setState(id, AppState.Failed)
            return false
        }

        enabled[id] = slot
        _settingsSections.value = _settingsSections.value + slot.sections.map { id to it }
        setState(id, AppState.Enabled)
        events.tryEmit(SuperizerEvent.Enabled(id))
        return true
    }

    /**
     * Undoes a `setup` — disposers in reverse order, scope cancelled, sections withdrawn. Only the
     * Service Menu asks for this in the MVP, and it is here to prove the mechanism works rather
     * than because anything needs it. It does not touch stored data; erasing that is a separate,
     * confirmed action (D35).
     */
    public suspend fun disable(id: AppId): Boolean = gate.withLock {
        if (_current.value?.app?.id == id) closeLocked(force = true, keepSnapshot = false)
        val slot = enabled.remove(id) ?: return@withLock false
        slot.disposers.asReversed().forEach { runCatching { it.dispose() } }
        slot.scope.cancel()
        _settingsSections.value = _settingsSections.value.filterNot { it.first == id }
        setState(id, AppState.Registered)
        events.tryEmit(SuperizerEvent.Disabled(id))
        true
    }

    /**
     * The full sequence of §9, with the current session closed first.
     *
     * @param force launches a hidden app that has not been unlocked. Only the activation service
     *   and the Service Menu pass true — otherwise "hidden" would be a suggestion (D8).
     */
    public suspend fun launch(
        id: AppId,
        config: AppConfig = AppConfig.Empty,
        restoreFrom: JsonObject? = null,
        force: Boolean = false,
    ): Result<AppSession> = gate.withLock { launchLocked(id, config, restoreFrom, force) }

    private suspend fun launchLocked(
        id: AppId,
        config: AppConfig,
        restoreFrom: JsonObject?,
        force: Boolean,
    ): Result<AppSession> {
        val app = registry.get(id) ?: return fail(id, "unknown app $id")
        if (app.metadata.hidden && !force && id !in unlocked()) {
            return fail(id, "hidden app $id is not unlocked")
        }
        if (!enabled.containsKey(id) && !enableLocked(id)) {
            return fail(id, "app $id could not be enabled")
        }

        // The outgoing app keeps its snapshot: when a stack arrives (11) back will want it, and
        // until then it is what makes "open the converter, come back" not lose the sum.
        if (_current.value != null) closeLocked(force = true, keepSnapshot = true)

        val state = MutableStateFlow(AppState.Creating)
        setState(id, AppState.Creating)

        val instanceScope = childScope(id) { cause ->
            // D31: an exception escaping the instance's own coroutines fails the session and shows
            // the host's error screen. Without this the first unhandled throw in a `scope.launch`
            // takes the whole host down with it.
            state.value = AppState.Failed
            setState(id, AppState.Failed)
            events.tryEmit(SuperizerEvent.Crashed(id, cause))
        }
        val runtime = runtimeFactory.createInstance(enabled.getValue(id).runtime, instanceScope)
        events.tryEmit(SuperizerEvent.RuntimeCreated(id))

        val typed = decode(app, config)
        if (typed.isFailure) {
            instanceScope.cancel()
            return fail(id, "config rejected: ${typed.exceptionOrNull()?.message}", typed.exceptionOrNull())
        }
        events.tryEmit(SuperizerEvent.Configured(id, config))

        val instance = runCatching { create(app, runtime, typed.getOrThrow()) }.getOrElse { cause ->
            instanceScope.cancel()
            return fail(id, "launch() threw: ${cause.message}", cause)
        }
        events.tryEmit(SuperizerEvent.Created(id))

        state.value = AppState.Launching
        setState(id, AppState.Launching)
        val launched = runCatching { instance.onLaunch() }.exceptionOrNull()
        if (launched != null) {
            runCatching { instance.dispose() }
            instanceScope.cancel()
            return fail(id, "onLaunch() threw: ${launched.message}", launched)
        }
        events.tryEmit(SuperizerEvent.Launched(id))

        if (restoreFrom != null) {
            runCatching { instance.restore(restoreFrom) }
                .onFailure { runtime.logger.warn("restore() threw; starting fresh", it) }
            events.tryEmit(SuperizerEvent.Restored(id))
        }

        state.value = AppState.Active
        setState(id, AppState.Active)
        val session = AppSession(app, config, instance, runtime, state.asStateFlow())
        _current.value = session
        if (hostLifecycle.value == HostLifecycle.Background) instance.onBackground()
        events.tryEmit(SuperizerEvent.Active(id))
        return Result.success(session)
    }

    /**
     * Asks the instance first, then takes it apart: `saveState` → `onClose` → `dispose` → cancel.
     *
     * @return false when the app vetoed and [force] was not set, so the caller can put up the
     *   host's own "close anyway?" dialog rather than the app inventing one.
     */
    public suspend fun close(force: Boolean = false): Boolean =
        gate.withLock { closeLocked(force, keepSnapshot = false) }

    private suspend fun closeLocked(force: Boolean, keepSnapshot: Boolean): Boolean {
        val session = _current.value ?: return true
        val id = session.app.id

        if (!force) {
            val agreed = runCatching { session.instance.onCloseRequested() }.getOrDefault(true)
            if (!agreed) return false
        }

        setState(id, AppState.Closing)
        val snapshot = runCatching { session.instance.saveState() }.getOrNull()
        if (keepSnapshot) {
            snapshots.save(SessionSnapshot(id, session.config, snapshot, clock.now()))
        } else {
            snapshots.clear()
        }

        runCatching { session.instance.onClose() }
        events.tryEmit(SuperizerEvent.Closed(id))
        runCatching { session.instance.dispose() }
        session.runtime.scope.cancel()
        _current.value = null
        setState(id, AppState.Enabled)
        events.tryEmit(SuperizerEvent.Disposed(id))
        return true
    }

    /**
     * After a process death — or simply after a restart, because the three platforms cannot
     * reliably tell those apart (question 14 in 09). A snapshot older than a day is dropped: nobody
     * comes back the next morning expecting yesterday's half-typed sum.
     */
    public suspend fun restoreLastSession(): Result<AppSession>? {
        val snapshot = snapshots.load() ?: return null
        if (clock.now() - snapshot.savedAt > SNAPSHOT_TTL_MS) {
            snapshots.clear()
            return null
        }
        if (registry.get(snapshot.appId) == null) {
            snapshots.clear()
            return null
        }
        // Not `force`: a snapshot is yesterday's screen, not an activation. A hidden app that was
        // locked again in between (the Service Menu, a reset) must not walk back in through it —
        // the restore fails like any other launch of a locked app, and the snapshot goes with it.
        val restored = launch(snapshot.appId, snapshot.config, snapshot.state)
        if (restored.isFailure) snapshots.clear()
        return restored
    }

    /**
     * Wired by the host once. Background is where the snapshot is written, because on Android the
     * process may not come back — and it is `ON_STOP`, not `ON_PAUSE`, or a permission dialog would
     * write one on every tap (08).
     */
    public fun observeLifecycle() {
        scope.launch {
            var last = hostLifecycle.value
            hostLifecycle.collect { now ->
                if (now == last) return@collect
                last = now
                val session = _current.value
                when (now) {
                    HostLifecycle.Background -> {
                        session?.instance?.onBackground()
                        if (session != null) {
                            snapshots.save(
                                SessionSnapshot(
                                    session.app.id,
                                    session.config,
                                    runCatching { session.instance.saveState() }.getOrNull(),
                                    clock.now(),
                                ),
                            )
                        }
                        events.tryEmit(SuperizerEvent.Background(session?.app?.id))
                    }

                    HostLifecycle.Foreground -> {
                        session?.instance?.onForeground()
                        events.tryEmit(SuperizerEvent.Foreground(session?.app?.id))
                    }
                }
            }
        }
    }

    /** Erases the session snapshot — part of `Superizer.reset(id)` (D35). */
    public suspend fun forgetSnapshot(id: AppId) {
        if (snapshots.load()?.appId == id) snapshots.clear()
    }

    // ------------------------------------------------------------------ internals

    private fun fail(id: AppId?, reason: String, cause: Throwable? = null): Result<AppSession> {
        if (id != null) setState(id, AppState.Failed)
        events.tryEmit(SuperizerEvent.LaunchFailed(id, reason, cause))
        return Result.failure(cause ?: IllegalStateException(reason))
    }

    /**
     * The one place the star projection on [SuperizerApp] is opened. Decoding and launching both
     * need the app's own `C`, and the registry can only hold `SuperizerApp<*>`.
     */
    @Suppress("UNCHECKED_CAST")
    private fun decode(app: SuperizerApp<*>, config: AppConfig): Result<Any> {
        val spec = app.configSpec as AppConfigSpec<Any>
        val decoded = spec.decode(config)
        if (decoded.isSuccess) return decoded
        val reason = decoded.exceptionOrNull()?.message ?: "malformed config"
        events.tryEmit(SuperizerEvent.ConfigRejected(app.id, config, reason))
        // D18: a broken payload is reported either way. Whether it is also fatal is the app's call,
        // because only the app knows whether running on defaults is better than not running.
        return if (spec.fallbackToDefault) Result.success(spec.default) else decoded
    }

    @Suppress("UNCHECKED_CAST")
    private fun create(app: SuperizerApp<*>, runtime: InstanceRuntime, config: Any): AppInstance =
        (app as SuperizerApp<Any>).launch(runtime, config)

    private fun setState(id: AppId, state: AppState) {
        _states.value = _states.value + (id to state)
    }

    private fun childScope(id: AppId, onCrash: ((Throwable) -> Unit)? = null): CoroutineScope {
        val parent = scope.coroutineContext[Job]
        var context: CoroutineContext = scope.coroutineContext.minusKey(Job) + kotlinx.coroutines.SupervisorJob(parent)
        if (onCrash != null) {
            context += CoroutineExceptionHandler { _, cause -> onCrash(cause) }
        }
        return CoroutineScope(context)
    }

    private inner class Context(private val slot: Enabled, private val id: AppId) : AppSetupContext {
        override val runtime: AppRuntime get() = slot.runtime

        override fun settingsSection(section: @Composable (runtime: AppRuntime) -> Unit) {
            slot.sections += section
            slot.disposers += Disposable { slot.sections.remove(section) }
        }

        override fun deepLink(path: String, toConfig: (params: Map<String, String>) -> AppConfig) {
            slot.deepLinks[path] = toConfig
            slot.disposers += Disposable { slot.deepLinks.remove(path) }
        }

        override fun listener(handler: suspend (event: SuperizerEvent) -> Unit) {
            val job = slot.scope.launch { events.collect { handler(it) } }
            slot.disposers += Disposable { job.cancel() }
        }
    }

    private companion object {
        /** A day. Past it the user is starting over, not resuming (05). */
        const val SNAPSHOT_TTL_MS: Long = 24L * 60 * 60 * 1000
    }
}
