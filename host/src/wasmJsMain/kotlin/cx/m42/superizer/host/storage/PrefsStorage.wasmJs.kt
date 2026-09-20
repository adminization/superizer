package cx.m42.superizer.host.storage

import kotlinx.browser.localStorage
import org.w3c.dom.get

/**
 * The browser's `localStorage`, scoped to the page's origin, under a prefix so the host's keys
 * cannot collide with anything else served from the same host.
 *
 * Every access may throw — a browser with site data blocked, or a page in a partitioned
 * third-party context, fails on the very first read. [SafePrefs] is what guards it.
 */
public actual object PrefsStorage {
    private const val PREFIX = "cx.m42.superizer."

    public actual fun get(key: String): String? = localStorage[PREFIX + key]

    public actual fun put(key: String, value: String) {
        localStorage.setItem(PREFIX + key, value)
    }

    public actual fun remove(key: String) {
        localStorage.removeItem(PREFIX + key)
    }

    public actual fun keys(): Set<String> = buildSet {
        for (index in 0 until localStorage.length) {
            val key = localStorage.key(index) ?: continue
            if (key.startsWith(PREFIX)) add(key.removePrefix(PREFIX))
        }
    }
}
