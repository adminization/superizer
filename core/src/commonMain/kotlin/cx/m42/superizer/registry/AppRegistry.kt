package cx.m42.superizer.registry

import cx.m42.superizer.backup.BackupPolicy

import cx.m42.superizer.app.AppId
import cx.m42.superizer.app.AppManifest
import cx.m42.superizer.app.SuperizerApp
import cx.m42.superizer.event.SuperizerEvent
import cx.m42.superizer.runtime.HostInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Why a manifest was turned away, or that it was not. Shown in the Service Menu (D47). */
public sealed interface RegistrationOutcome {
    public data object Accepted : RegistrationOutcome
    public data class Rejected(val reason: String) : RegistrationOutcome
}

/**
 * Every app the host knows about, keyed by id.
 *
 * Usually filled at startup and never touched again, but late registration is allowed and `apps`
 * is observable for that reason — a downloaded catalog (10) would arrive this way, and leaving
 * room for it costs nothing today.
 *
 * Apps never see this. They get the read-only `AppsService` (§19).
 */
public class AppRegistry(
    private val host: HostInfo,
    private val events: MutableSharedFlow<SuperizerEvent>? = null,
) {
    private val byId = LinkedHashMap<AppId, SuperizerApp<*>>()
    private val _apps = MutableStateFlow<List<SuperizerApp<*>>>(emptyList())
    private val _manifests = MutableStateFlow<List<Pair<AppManifest, RegistrationOutcome>>>(emptyList())

    public val apps: StateFlow<List<SuperizerApp<*>>> get() = _apps.asStateFlow()

    /** Manifests of everything ever offered, accepted or not — what the Service Menu lists. */
    public val manifests: StateFlow<List<Pair<AppManifest, RegistrationOutcome>>> get() = _manifests.asStateFlow()

    /**
     * Throws on a duplicate id, as Adminizer's `AppManager` does: two apps answering to one name is
     * a build mistake, and the host that registered them is in the same process.
     *
     * Returns false — with a `RegistrationRejected` event carrying the reason — when the *manifest*
     * does not fit this host (D47). That is not a mistake in the code in front of you; it is a
     * deployment fact ("this app wants a newer host", "this host has no camera"), and the Service
     * Menu showing it beats a crash nobody can read.
     */
    public fun register(app: SuperizerApp<*>): Boolean {
        val manifest = app.manifest
        require(!byId.containsKey(manifest.id)) { "duplicate app id: ${manifest.id}" }

        val rejection = reject(manifest)
        if (rejection != null) {
            _manifests.value = _manifests.value + (manifest to RegistrationOutcome.Rejected(rejection))
            events?.tryEmit(SuperizerEvent.RegistrationRejected(manifest.id, rejection))
            return false
        }

        byId[manifest.id] = app
        _apps.value = byId.values.toList()
        _manifests.value = _manifests.value + (manifest to RegistrationOutcome.Accepted)
        events?.tryEmit(SuperizerEvent.Registered(manifest.id, manifest.version))
        return true
    }

    /** The reason this host cannot run this manifest, or null when it can. */
    private fun reject(manifest: AppManifest): String? {
        if (manifest.minHostContract > host.contractVersion) {
            return "needs host contract ${manifest.minHostContract}, this host is ${host.contractVersion}"
        }
        if (manifest.protection.sensitive && manifest.minHostContract < PROTECTION_CONTRACT) {
            // D138: on a host of contract 1 this manifest would run with no lock and no one would
            // notice. Refusing it here too makes the mistake fail in the app's first test instead.
            return "declares protection but asks only for host contract ${manifest.minHostContract}; " +
                "protection needs $PROTECTION_CONTRACT"
        }
        if (manifest.backup != BackupPolicy.All && manifest.minHostContract < BACKUP_CONTRACT) {
            // The same mistake one field later (04 §9, Б10): a contract-2 host would back up what
            // this app asked it to leave out.
            return "declares a backup policy but asks only for host contract ${manifest.minHostContract}; " +
                "a backup policy needs $BACKUP_CONTRACT"
        }
        val missing = manifest.requires - host.services
        if (missing.isNotEmpty()) {
            return "host provides no ${missing.joinToString(", ") { it.name }}"
        }
        val badPath = manifest.deepLinks.firstOrNull { !DEEP_LINK_PATH.matches(it) }
        if (badPath != null) return "malformed deep-link path: '$badPath'"
        val badTopic = manifest.pushTopics.firstOrNull { !TOPIC.matches(it) }
        if (badTopic != null) return "malformed push topic: '$badTopic'"
        return null
    }

    public fun get(id: AppId): SuperizerApp<*>? = byId[id]

    public fun all(): List<SuperizerApp<*>> = byId.values.toList()

    /** What All Apps and the drawer draw: everything not hidden, plus whatever has been unlocked. */
    public fun visible(unlocked: Set<AppId>): List<SuperizerApp<*>> =
        all().filter { !it.metadata.hidden || it.id in unlocked }

    public fun outcome(id: AppId): RegistrationOutcome? =
        _manifests.value.firstOrNull { it.first.id == id }?.second

    private companion object {
        /** One or more slash-separated segments, no query, no scheme — `rate`, `chart/day`. */
        val DEEP_LINK_PATH = Regex("[a-z0-9][a-z0-9-]*(/[a-z0-9][a-z0-9-]*)*")
        val TOPIC = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")

        /** The contract that introduced `AppManifest.protection` (06). */
        const val PROTECTION_CONTRACT = 2

        /** The contract that introduced `AppManifest.backup`, `runtime.secrets` and `runtime.diagnostics` (ssh-new 07). */
        const val BACKUP_CONTRACT = 3
    }
}
