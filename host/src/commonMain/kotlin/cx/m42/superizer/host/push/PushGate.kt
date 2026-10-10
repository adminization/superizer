package cx.m42.superizer.host.push

import cx.m42.superizer.app.AppId
import cx.m42.superizer.app.PushUse
import cx.m42.superizer.host.storage.HostKeys
import cx.m42.superizer.host.storage.SafePrefs
import cx.m42.superizer.push.PushPermission
import cx.m42.superizer.registry.AppRegistry
import cx.m42.superizer.runtime.HostLifecycle
import cx.m42.superizer.runtime.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Which push apps the person has started using, and everything that waits on it (push-opt-in).
 *
 * The rule the owner set: no question at first start, and none for an app nobody uses. An app
 * starts being used when it is added from the catalog, when it is first opened — by a tile, the
 * catalog, a link, a hidden app's first visit — or when a backup brings it back (D418, D419). Only
 * then does the host ask, straight away and with the system's own dialog (D420), wake FCM (D421),
 * hand the topics to the transport (D423), and let the registrar talk to the server (D422).
 *
 * Revealing a hidden app does not count: an activation puts it on Home, but the question waits for
 * the first visit.
 */
public class PushGate(
    private val registry: AppRegistry,
    private val topics: TopicStore,
    private val control: PushControl,
    private val logger: Logger,
    private val json: Json = Json,
) {
    private val _used = MutableStateFlow<Set<AppId>>(emptySet())
    public val used: StateFlow<Set<AppId>> get() = _used.asStateFlow()

    private val _permission = MutableStateFlow(PushPermission.Unavailable)
    public val permission: StateFlow<PushPermission> get() = _permission.asStateFlow()

    private var scope: CoroutineScope? = null

    /** One system dialog at a time: Android hands a second caller `false` without asking. */
    private val asking = Mutex()

    /**
     * After registration, before any app is enabled: reads the stored set, keeping only what is
     * still a registered push app, and wakes FCM at once if it is not empty — the person already
     * uses a push app, so this start is no different from the one before it.
     */
    public fun attach(scope: CoroutineScope, lifecycle: StateFlow<HostLifecycle>) {
        this.scope = scope
        _used.value = load().filter(::takesPush).toSet()
        scope.launch {
            if (_used.value.isNotEmpty()) control.activate()
            refresh()
        }
        // Coming back from the system's Settings is the usual way a Blocked becomes Granted.
        scope.launch {
            lifecycle.collect { if (it == HostLifecycle.Foreground) refresh() }
        }
    }

    /** Every entry point in the class comment ends here. Ids that take no push are ignored. */
    public fun startUsing(ids: Collection<AppId>) {
        val fresh = ids.filter { takesPush(it) && it !in _used.value }.distinct()
        if (fresh.isEmpty()) return
        val wasEmpty = _used.value.isEmpty()
        _used.value = _used.value + fresh
        persist()
        val scope = scope ?: return
        scope.launch {
            if (wasEmpty) control.activate()
            fresh.forEach { id -> topics.current(id).forEach { control.subscribeTopic(topics.qualified(id, it)) } }
            if (fresh.any { registry.pushUse(it) == PushUse.Alerts }) ask()
        }
    }

    /** Taken off Home, or reset (D35). The topics stay in the store and come back with the app. */
    public fun stopUsing(id: AppId) {
        if (id !in _used.value) return
        _used.value = _used.value - id
        persist()
        val scope = scope ?: return
        scope.launch {
            topics.current(id).forEach { control.unsubscribeTopic(topics.qualified(id, it)) }
        }
    }

    public fun isUsed(id: AppId): Boolean = id in _used.value

    /** The system's question if it can still be asked, its Settings if it cannot (D424). */
    public suspend fun enable() {
        when (refresh()) {
            PushPermission.Off -> ask()
            PushPermission.Blocked -> control.openSettings()
            PushPermission.Granted, PushPermission.Unavailable -> Unit
        }
    }

    /** D420: nothing explained, nothing deferred — the system's dialog, unless it already said yes. */
    private suspend fun ask() {
        if (!control.available) return
        asking.withLock {
            if (refresh() != PushPermission.Off) return
            val granted = runCatching { control.requestPermission() }
                .onFailure { logger.warn("notification permission request failed", it) }
                .getOrDefault(false)
            logger.info("notification permission: ${if (granted) "granted" else "not granted"}")
            refresh()
        }
    }

    private suspend fun refresh(): PushPermission {
        val now = runCatching { control.permission() }.getOrDefault(PushPermission.Unavailable)
        _permission.value = now
        return now
    }

    private fun takesPush(id: AppId): Boolean = registry.pushUse(id) != PushUse.None && !registry.isSecret(id)

    private fun load(): List<AppId> {
        val raw = SafePrefs.get(HostKeys.PUSH_USED) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(String.serializer()), raw).mapNotNull { AppId.parseOrNull(it) }
        }.getOrDefault(emptyList())
    }

    private fun persist() {
        SafePrefs.put(HostKeys.PUSH_USED, json.encodeToString(ListSerializer(String.serializer()), _used.value.map { it.value }))
    }
}
