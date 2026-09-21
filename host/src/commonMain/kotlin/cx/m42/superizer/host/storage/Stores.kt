package cx.m42.superizer.host.storage

import cx.m42.superizer.app.AppId
import cx.m42.superizer.event.SuperizerEvent
import cx.m42.superizer.registry.SessionSnapshot
import cx.m42.superizer.registry.SnapshotStore
import cx.m42.superizer.runtime.StorageService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * One app's slice of the host's key/value store.
 *
 * The prefix is the whole of the isolation in Tier 0 (11): an app sees plain keys, two apps cannot
 * collide, and "erase this app" is a prefix scan (D35). Real boundaries need a process boundary,
 * which is what a bridge would bring (10); this is what is honest without one.
 */
public class PrefsStorageService(private val appId: AppId) : StorageService {

    private val prefix = prefixOf(appId)

    override suspend fun get(key: String): String? = SafePrefs.get(prefix + key)

    override suspend fun set(key: String, value: String) {
        SafePrefs.put(prefix + key, value)
    }

    override suspend fun remove(key: String) {
        SafePrefs.remove(prefix + key)
    }

    override suspend fun keys(): Set<String> =
        SafePrefs.keys().filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }.toSet()

    public companion object {
        public fun prefixOf(appId: AppId): String = "app.${appId.value}."

        /** D35: everything this app ever stored, gone. Used by reset, never by disable. */
        public fun erase(appId: AppId) {
            val prefix = prefixOf(appId)
            SafePrefs.keys().filter { it.startsWith(prefix) }.forEach { SafePrefs.remove(it) }
        }
    }
}

/** The host's own keys, in a namespace no app's prefix can reach. */
public object HostKeys {
    public const val LANGUAGE: String = "host.language"
    public const val HAPTICS: String = "host.haptics"
    public const val UNLOCKED: String = "host.unlocked"
    public const val HOME: String = "host.home"
    public const val SESSION: String = "host.session"
    public const val TOPICS: String = "host.push.topics"
}

/**
 * Which hidden apps this device has unlocked (D8).
 *
 * Stored rather than derived, because an activation is a one-time event and the app has to stay
 * visible afterwards. Observable, because All Apps and the drawer both filter on it.
 */
public class UnlockStore(
    private val events: MutableSharedFlow<SuperizerEvent>? = null,
    private val json: Json = Json,
) {
    private val _unlocked = MutableStateFlow(load())
    public val unlocked: StateFlow<Set<AppId>> get() = _unlocked.asStateFlow()

    public fun unlock(id: AppId) {
        if (id in _unlocked.value) return
        _unlocked.value = _unlocked.value + id
        persist()
        events?.tryEmit(SuperizerEvent.Unlocked(id))
    }

    public fun lock(id: AppId) {
        if (id !in _unlocked.value) return
        _unlocked.value = _unlocked.value - id
        persist()
        events?.tryEmit(SuperizerEvent.Locked(id))
    }

    private fun load(): Set<AppId> {
        val raw = SafePrefs.get(HostKeys.UNLOCKED) ?: return emptySet()
        return runCatching {
            json.decodeFromString(ListSerializer(String.serializer()), raw)
                .mapNotNull { AppId.parseOrNull(it) }
                .toSet()
        }.getOrDefault(emptySet())
    }

    private fun persist() {
        SafePrefs.put(
            HostKeys.UNLOCKED,
            json.encodeToString(ListSerializer(String.serializer()), _unlocked.value.map { it.value }),
        )
    }
}

/**
 * Which apps the user keeps on Home, in the order they were added (D48).
 *
 * Absent means a first run and the host's [defaults] apply; present-but-empty means the user took
 * everything off, which is a choice to keep. Ids are stored as written and filtered by the shell
 * against what is registered and unlocked *now*, so an app a build dropped and a later build
 * brought back lands where the user had put it.
 */
public class HomeStore(
    private val defaults: List<AppId>,
    private val events: MutableSharedFlow<SuperizerEvent>? = null,
    private val json: Json = Json,
) {
    private val _home = MutableStateFlow(load())
    public val home: StateFlow<List<AppId>> get() = _home.asStateFlow()

    public fun add(id: AppId) {
        if (id in _home.value) return
        _home.value = _home.value + id
        persist()
        events?.tryEmit(SuperizerEvent.AddedToHome(id))
    }

    public fun remove(id: AppId) {
        if (id !in _home.value) return
        _home.value = _home.value - id
        persist()
        events?.tryEmit(SuperizerEvent.RemovedFromHome(id))
    }

    private fun load(): List<AppId> {
        val raw = SafePrefs.get(HostKeys.HOME) ?: return defaults.distinct()
        return runCatching {
            json.decodeFromString(ListSerializer(String.serializer()), raw)
                .mapNotNull { AppId.parseOrNull(it) }
                .distinct()
        }.getOrDefault(defaults.distinct())
    }

    private fun persist() {
        SafePrefs.put(
            HostKeys.HOME,
            json.encodeToString(ListSerializer(String.serializer()), _home.value.map { it.value }),
        )
    }
}

/**
 * Where the open app's snapshot lives between processes (D16). One key, because there is one
 * session (D4) — a stack would make this a list and nothing else about it would change.
 */
public class PrefsSnapshotStore(private val json: Json = Json) : SnapshotStore {

    override suspend fun save(snapshot: SessionSnapshot) {
        runCatching { SafePrefs.put(HostKeys.SESSION, json.encodeToString(SessionSnapshot.serializer(), snapshot)) }
    }

    override suspend fun load(): SessionSnapshot? {
        val raw = SafePrefs.get(HostKeys.SESSION) ?: return null
        // A snapshot written by an older build may no longer parse. That is not an error worth
        // showing anyone: it means the same thing as having no snapshot.
        return runCatching { json.decodeFromString(SessionSnapshot.serializer(), raw) }.getOrNull()
    }

    override suspend fun clear() {
        SafePrefs.remove(HostKeys.SESSION)
    }
}
