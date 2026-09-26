package cx.m42.superizer.host.storage

import platform.Foundation.NSUserDefaults

/**
 * A defaults suite of the host's own, inside the app's sandbox — the same name Android gives its
 * preferences file, so a person looking for the host's store finds one name on both.
 *
 * A suite rather than `standardUserDefaults`: the standard domain answers `dictionaryRepresentation`
 * with the global domain folded in, and reset (D35) needs the list of keys this host wrote and no
 * others. `persistentDomainForName` is exactly that list.
 */
public actual object PrefsStorage {
    private const val SUITE = "cx.m42.superizer.store"

    private val defaults: NSUserDefaults by lazy { NSUserDefaults(suiteName = SUITE) }

    public actual fun get(key: String): String? = defaults.stringForKey(key)

    public actual fun put(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }

    public actual fun remove(key: String) {
        defaults.removeObjectForKey(key)
    }

    public actual fun keys(): Set<String> =
        defaults.persistentDomainForName(SUITE)?.keys?.mapNotNull { it as? String }?.toSet().orEmpty()
}
