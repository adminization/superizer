package cx.m42.superizer.host.storage

/**
 * The one thing each platform has to supply for anything here to persist: a string keyed by a
 * string, surviving a restart.
 *
 * Deliberately untyped and tiny — every value the host stores is a short string or a small JSON
 * blob, and an interface any wider would have to be written four times for no gain.
 *
 * Implementations may throw: a browser with site data blocked fails on the very first read.
 * [SafePrefs] is what guards that, so no caller has to.
 */
public expect object PrefsStorage {
    public fun get(key: String): String?

    public fun put(key: String, value: String)

    public fun remove(key: String)

    /** Every key this store holds. Needed by `StorageService.keys()` and by reset (D35). */
    public fun keys(): Set<String>
}

/** Every access guarded: a store nobody can write to is a preference nobody set, not a crash. */
public object SafePrefs {
    public fun get(key: String): String? =
        runCatching { PrefsStorage.get(key)?.takeIf { it.isNotEmpty() } }.getOrNull()

    public fun put(key: String, value: String) {
        runCatching { PrefsStorage.put(key, value) }
    }

    public fun remove(key: String) {
        runCatching { PrefsStorage.remove(key) }
    }

    public fun keys(): Set<String> = runCatching { PrefsStorage.keys() }.getOrDefault(emptySet())
}
