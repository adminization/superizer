package cx.m42.superizer.host.secrets

import cx.m42.superizer.app.AppId
import cx.m42.superizer.host.storage.HostKeys
import cx.m42.superizer.host.storage.SafePrefs
import cx.m42.superizer.runtime.StorageService
import cx.m42.superizer.secrets.SecretVault
import cx.m42.superizer.secrets.SecretsCount
import cx.m42.superizer.secrets.SecretsPort
import cx.m42.superizer.secrets.SecretsUnavailableException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Every app's `runtime.secrets`, sealed by one vault the host holds (05 §3.2, D171).
 *
 * Values live in the host's prefs next to `app.<id>.`, under `secret.<id>.`, so reset (D35) and
 * backup (D172) walk them by the same prefix. Each value is whatever the vault's [SecretVault.seal]
 * returned — or, with no vault on this platform yet, `plain:` and the value, which the diagnostics
 * report as not protected and [reseal] seals the moment there is a vault.
 *
 * The library knows no cipher: the vault is the host's (the Keystore, the Secure Enclave, the
 * person's SSH key), and this class only decides where its output goes.
 */
public class HostSecrets(initial: SecretVault?) : SecretsPort {

    private val _vault = MutableStateFlow(initial)
    override val vault: StateFlow<SecretVault?> get() = _vault.asStateFlow()

    /** Reseal must not interleave with a write, or the write would land under the vault being left. */
    private val writing = Mutex()

    /** What goes into prefs for [value]: sealed when there is a vault, marked plain when there is not. */
    public suspend fun seal(value: String): String {
        val vault = _vault.value ?: return PLAIN + value
        return vault.seal(value)
    }

    /** What [stored] holds. Throws [SecretsUnavailableException] when the vault cannot open it now. */
    public suspend fun open(stored: String): String {
        if (stored.startsWith(PLAIN)) return stored.substring(PLAIN.length)
        val vault = _vault.value ?: throw SecretsUnavailableException("sealed, and this host has no vault")
        return vault.open(stored) ?: throw SecretsUnavailableException()
    }

    /** One app's view: plain keys in, the host's prefix and sealing underneath. */
    public fun forApp(appId: AppId): StorageService = AppSecrets(appId)

    override suspend fun reseal(next: SecretVault): Boolean = writing.withLock {
        val keys = SafePrefs.keys().filter { it.startsWith(SECRET_PREFIX) || it == HostKeys.HEARTBEAT }
        val opened = LinkedHashMap<String, String>()
        for (key in keys) {
            val stored = SafePrefs.get(key) ?: continue
            opened[key] = runCatching { open(stored) }.getOrElse { return@withLock false }
        }
        val resealed = runCatching { opened.mapValues { next.seal(it.value) } }.getOrElse { return@withLock false }
        resealed.forEach { (key, value) -> SafePrefs.put(key, value) }
        _vault.value = next
        true
    }

    override suspend fun counts(): Map<AppId, SecretsCount> {
        val byApp = mutableMapOf<AppId, SecretsCount>()
        SafePrefs.keys().filter { it.startsWith(SECRET_PREFIX) }.forEach { key ->
            val id = AppId.parseOrNull(key.removePrefix(SECRET_PREFIX).substringBefore('.')) ?: return@forEach
            val plain = SafePrefs.get(key)?.startsWith(PLAIN) == true
            val count = byApp[id] ?: SecretsCount(0, 0)
            byApp[id] = if (plain) count.copy(plain = count.plain + 1) else count.copy(sealed = count.sealed + 1)
        }
        return byApp
    }

    private inner class AppSecrets(appId: AppId) : StorageService {
        private val prefix = prefixOf(appId)

        override suspend fun get(key: String): String? {
            val stored = SafePrefs.get(prefix + key) ?: return null
            return open(stored)
        }

        override suspend fun set(key: String, value: String) {
            writing.withLock { SafePrefs.put(prefix + key, seal(value)) }
        }

        override suspend fun remove(key: String) {
            SafePrefs.remove(prefix + key)
        }

        override suspend fun keys(): Set<String> =
            SafePrefs.keys().filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }.toSet()
    }

    public companion object {
        public const val SECRET_PREFIX: String = "secret."

        /** The mark of a value written while there was no vault. Not a format any vault produces. */
        public const val PLAIN: String = "plain:"

        public fun prefixOf(appId: AppId): String = "$SECRET_PREFIX${appId.value}."

        /** D35: an app's secrets, gone with the rest of it. */
        public fun erase(appId: AppId) {
            val prefix = prefixOf(appId)
            SafePrefs.keys().filter { it.startsWith(prefix) }.forEach { SafePrefs.remove(it) }
        }
    }
}
