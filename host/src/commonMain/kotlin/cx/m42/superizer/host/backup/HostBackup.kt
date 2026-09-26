package cx.m42.superizer.host.backup

import cx.m42.superizer.app.AppId
import cx.m42.superizer.backup.AppBackup
import cx.m42.superizer.backup.AppPreview
import cx.m42.superizer.backup.BackupBundle
import cx.m42.superizer.backup.BackupHost
import cx.m42.superizer.backup.BackupKeyName
import cx.m42.superizer.backup.BackupOutcome
import cx.m42.superizer.backup.BackupPolicy
import cx.m42.superizer.backup.BackupPort
import cx.m42.superizer.backup.BackupPreview
import cx.m42.superizer.backup.HostBackupCipher
import cx.m42.superizer.backup.RestorePlan
import cx.m42.superizer.backup.RestoreReport
import cx.m42.superizer.host.secrets.HostSecrets
import cx.m42.superizer.host.storage.HostKeys
import cx.m42.superizer.host.storage.PrefsStorageService
import cx.m42.superizer.host.storage.SafePrefs
import cx.m42.superizer.registry.AppRegistry
import cx.m42.superizer.runtime.Clock
import cx.m42.superizer.runtime.HostInfo
import cx.m42.superizer.secrets.SecretsUnavailableException
import cx.m42.superizer.ssh.SshOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * The host's backup (05 §3.1–§3.4, 02 §6.8, D168–D172): a walk over the prefixes, never over an
 * app's formats.
 *
 * `app.<id>.` is carried as written; `secret.<id>.` is opened by the host's vault and carried open,
 * inside a file only the person's keys open, and sealed again by the next device's vault. An app
 * takes part by its manifest's [BackupPolicy] and in no other way. Keys are never in it — only their
 * names, so the next device can say which to import again (D170).
 *
 * The cipher is the host's (age); the library only builds and reads the bundle.
 */
public class HostBackup(
    private val host: HostInfo,
    private val clock: Clock,
    private val registry: AppRegistry,
    private val secrets: HostSecrets,
    private val cipher: HostBackupCipher?,
    private val keyNames: suspend () -> List<BackupKeyName>,
    /** Puts the device's confirmation up with the host's words (D148, D169). */
    private val confirm: suspend (String) -> Boolean,
    /** Called for every app whose data was replaced, so a stale screen of it is closed. */
    private val replaced: suspend (AppId) -> Unit = {},
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) : BackupPort {

    private val _lastBackupAt = MutableStateFlow(SafePrefs.get(HostKeys.BACKUP_AT)?.toLongOrNull())
    override val lastBackupAt: StateFlow<Long?> get() = _lastBackupAt.asStateFlow()

    override suspend fun preview(): BackupPreview = BackupPreview(
        apps = appIds().map { preview(it) },
        keys = runCatching { keyNames() }.getOrDefault(emptyList()),
    )

    override suspend fun export(recipients: List<String>, reason: String): BackupOutcome<ByteArray> {
        val cipher = cipher ?: return BackupOutcome.Unsupported
        if (recipients.none { it.isNotBlank() }) return BackupOutcome.NoRecipients
        if (!confirm(reason)) return BackupOutcome.Cancelled

        val apps = LinkedHashMap<String, AppBackup>()
        for (id in appIds()) {
            val policy = policyOf(id)
            if (policy == BackupPolicy.None) continue
            val storage = values(PrefsStorageService.prefixOf(id), policy)
            val sealed = values(HostSecrets.prefixOf(id), policy)
            val opened = LinkedHashMap<String, String>()
            for ((key, stored) in sealed) {
                opened[key] = try {
                    secrets.open(stored)
                } catch (_: SecretsUnavailableException) {
                    return BackupOutcome.SecretsLocked
                }
            }
            if (storage.isEmpty() && opened.isEmpty()) continue
            apps[id.value] = AppBackup(registry.get(id)?.version, storage, opened)
        }
        val bundle = BackupBundle(
            host = BackupHost(host.name, host.version, host.contractVersion, host.platform, clock.now()),
            settings = HostKeys.BACKED_UP.mapNotNull { key -> SafePrefs.get(key)?.let { key to it } }.toMap(),
            apps = apps,
            keys = runCatching { keyNames() }.getOrDefault(emptyList()),
        )
        val plain = json.encodeToString(BackupBundle.serializer(), bundle).encodeToByteArray()
        return try {
            val file = cipher.encrypt(plain, recipients.filter { it.isNotBlank() })
            val at = clock.now()
            SafePrefs.put(HostKeys.BACKUP_AT, at.toString())
            _lastBackupAt.value = at
            BackupOutcome.Ok(file)
        } catch (e: IllegalArgumentException) {
            // The cipher's word for "none of these lines is a key it can encrypt to".
            BackupOutcome.NoRecipients
        } catch (e: Exception) {
            BackupOutcome.Failed("encrypt", e::class.simpleName)
        } finally {
            plain.fill(0)
        }
    }

    override suspend fun read(file: ByteArray): BackupOutcome<RestorePlan> {
        val plain: ByteArray = if (looksLikeJson(file)) {
            file
        } else {
            val cipher = cipher ?: return BackupOutcome.Unsupported
            when (val opened = cipher.decrypt(file)) {
                is SshOutcome.Ok -> opened.value
                SshOutcome.Cancelled, SshOutcome.Refused -> return BackupOutcome.Cancelled
                SshOutcome.NoKey -> return BackupOutcome.Unreadable("no-key")
                is SshOutcome.Failed -> return BackupOutcome.Unreadable(opened.code)
                else -> return BackupOutcome.Unreadable("not-opened")
            }
        }
        val bundle = try {
            json.decodeFromString(BackupBundle.serializer(), plain.decodeToString())
        } catch (_: Exception) {
            return BackupOutcome.Unreadable("not-a-backup")
        } finally {
            if (plain !== file) plain.fill(0)
        }
        if (bundle.schema > BackupBundle.SCHEMA) return BackupOutcome.Unreadable("newer-schema")
        val apps = bundle.apps.map { (id, app) ->
            AppPreview(id, app.storage.size, app.secrets.size, excluded = false)
        }
        return BackupOutcome.Ok(RestorePlan(bundle, apps))
    }

    override suspend fun restore(
        plan: RestorePlan,
        apps: Set<String>,
        settings: Boolean,
        reason: String,
    ): BackupOutcome<RestoreReport> {
        if (!confirm(reason)) return BackupOutcome.Cancelled
        // Sealed first, written after: a vault that refuses must leave every app as it was.
        val sealed = LinkedHashMap<String, Map<String, String>>()
        for (id in apps) {
            val app = plan.bundle.apps[id] ?: continue
            sealed[id] = try {
                app.secrets.mapValues { secrets.seal(it.value) }
            } catch (e: Exception) {
                return BackupOutcome.Failed("seal", e::class.simpleName)
            }
        }
        val restored = mutableListOf<String>()
        for (id in apps) {
            val app = plan.bundle.apps[id] ?: continue
            val appId = AppId.parseOrNull(id) ?: continue
            PrefsStorageService.erase(appId)
            HostSecrets.erase(appId)
            app.storage.forEach { (key, value) -> SafePrefs.put(PrefsStorageService.prefixOf(appId) + key, value) }
            sealed[id].orEmpty().forEach { (key, value) -> SafePrefs.put(HostSecrets.prefixOf(appId) + key, value) }
            replaced(appId)
            restored += id
        }
        if (settings) {
            plan.bundle.settings.filterKeys { it in HostKeys.BACKED_UP }.forEach { (key, value) -> SafePrefs.put(key, value) }
        }
        val here = runCatching { keyNames() }.getOrDefault(emptyList()).map { it.fingerprint }.toSet()
        return BackupOutcome.Ok(RestoreReport(restored, plan.bundle.keys.filter { it.fingerprint !in here }))
    }

    /** Every app with anything under either prefix, registered or not: data outlives a build that dropped its app. */
    private fun appIds(): List<AppId> {
        val registered = registry.all().map { it.id }
        val stored = SafePrefs.keys().mapNotNull { key ->
            when {
                key.startsWith(APP_PREFIX) -> AppId.parseOrNull(key.removePrefix(APP_PREFIX).substringBefore('.'))
                key.startsWith(HostSecrets.SECRET_PREFIX) -> AppId.parseOrNull(key.removePrefix(HostSecrets.SECRET_PREFIX).substringBefore('.'))
                else -> null
            }
        }
        return (registered + stored).distinct()
    }

    private fun policyOf(id: AppId): BackupPolicy = registry.get(id)?.manifest?.backup ?: BackupPolicy.All

    private fun preview(id: AppId): AppPreview {
        val policy = policyOf(id)
        return AppPreview(
            id = id.value,
            storageKeys = values(PrefsStorageService.prefixOf(id), policy).size,
            secretKeys = values(HostSecrets.prefixOf(id), policy).size,
            excluded = policy == BackupPolicy.None,
        )
    }

    private fun values(prefix: String, policy: BackupPolicy): Map<String, String> {
        if (policy == BackupPolicy.None) return emptyMap()
        val except = (policy as? BackupPolicy.Except)?.keys.orEmpty()
        return SafePrefs.keys()
            .filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix) }
            .filter { it !in except }
            .sorted()
            .mapNotNull { key -> SafePrefs.get(prefix + key)?.let { key to it } }
            .toMap()
    }

    private fun looksLikeJson(file: ByteArray): Boolean {
        val first = file.firstOrNull { it != ' '.code.toByte() && it != '\n'.code.toByte() && it != '\r'.code.toByte() && it != '\t'.code.toByte() }
        return first == '{'.code.toByte()
    }

    private companion object {
        const val APP_PREFIX = "app."
    }
}
