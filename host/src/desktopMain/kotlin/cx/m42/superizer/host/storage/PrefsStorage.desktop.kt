package cx.m42.superizer.host.storage

import java.io.File
import java.util.Properties

/**
 * A single properties file under the user's home directory, loaded once and written back on every
 * change: this holds short strings, so there is nothing to gain from a real store and a plain file
 * is one a person can read and delete.
 *
 * The directory is overridable so a test never touches the developer's own file.
 */
public actual object PrefsStorage {
    private var directory: File = File(System.getProperty("user.home"), ".superizer")

    /** Points the store somewhere else — a temp directory in a test, an app dir in a packaged host. */
    public fun useDirectory(dir: File) {
        directory = dir
        loaded = null
    }

    private var loaded: Properties? = null

    private val file: File get() = File(directory, "settings.properties")

    private val properties: Properties
        get() = loaded ?: Properties().apply {
            runCatching { if (file.isFile) file.inputStream().use { load(it) } }
            loaded = this
        }

    public actual fun get(key: String): String? = properties.getProperty(key)

    public actual fun put(key: String, value: String) {
        properties.setProperty(key, value)
        flush()
    }

    public actual fun remove(key: String) {
        properties.remove(key)
        flush()
    }

    public actual fun keys(): Set<String> = properties.stringPropertyNames()

    private fun flush() {
        runCatching {
            directory.mkdirs()
            file.outputStream().use { properties.store(it, "Superizer host") }
        }
    }
}
