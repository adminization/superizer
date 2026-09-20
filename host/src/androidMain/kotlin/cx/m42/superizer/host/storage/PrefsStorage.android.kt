package cx.m42.superizer.host.storage

import android.content.Context
import android.content.SharedPreferences

/**
 * Android's private per-app preferences — `MODE_PRIVATE`, so only this app's UID can read the file.
 * [initPrefs] must run before the first access; the host activity does it.
 */
public actual object PrefsStorage {
    private const val PREFS = "cx.m42.superizer.store"

    private var prefs: SharedPreferences? = null

    public fun init(context: Context) {
        // The application context, not the activity: this object outlives any one activity, and
        // holding an activity here would leak it across a rotation.
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    public actual fun get(key: String): String? = prefs?.getString(key, null)

    public actual fun put(key: String, value: String) {
        prefs?.edit()?.putString(key, value)?.apply()
    }

    public actual fun remove(key: String) {
        prefs?.edit()?.remove(key)?.apply()
    }

    public actual fun keys(): Set<String> = prefs?.all?.keys.orEmpty()
}

/** Wires the store to a context. Call once, before the first composition. */
public fun initPrefs(context: Context): Unit = PrefsStorage.init(context)
