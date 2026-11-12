package cx.m42.superizer.host.files

import cx.m42.superizer.app.AppId
import cx.m42.superizer.runtime.AppFolder
import cx.m42.superizer.runtime.FolderFile

/**
 * What a platform does with plain files, blocking: [HostAppFolder] puts every call on IO. Paths are absolute and use
 * `/`, which every platform here accepts.
 */
internal interface FileIo {
    /** Regular files directly in [dir], hidden ones left out; empty when [dir] does not exist. */
    fun list(dir: String): List<FolderFile>

    fun read(path: String): ByteArray?

    /** Creates [dir] when needed and replaces [name] in it whole: a half-written file is never what the app reads. */
    fun write(dir: String, name: String, bytes: ByteArray)

    fun delete(path: String): Boolean
}

/** This platform's files; null where an app gets no folder (the web). */
internal expect fun platformFileIo(): FileIo?

/**
 * Where a host named [hostName] keeps its apps' folders unless told otherwise; null when the platform cannot say yet
 * (Android before `AndroidHost.init`). Asked on every call rather than once, so a late init still finds it.
 */
internal expect fun defaultFolderRoot(hostName: String): String?

/** How the person finds [path]: the path itself, or on iOS what Files shows when it is the default. */
internal expect fun describeFolder(path: String, hostName: String, appId: AppId, isDefault: Boolean): String

/** Off the main thread where there is more than one; the web has one, and no files to wait for. */
internal expect suspend fun <T> onIo(block: () -> T): T

/** One app's folder: `<root>/<app id>`, the root fixed by the builder or the platform's default. */
internal class HostAppFolder(
    private val appId: AppId,
    private val hostName: String,
    private val customRoot: String?,
    private val io: FileIo,
) : AppFolder {

    private fun dir(): String? = (customRoot ?: defaultFolderRoot(hostName))?.trimEnd('/')?.let { "$it/${appId.value}" }

    override val location: String
        get() = dir()?.let { describeFolder(it, hostName, appId, customRoot == null) } ?: ""

    override suspend fun list(): List<FolderFile> {
        val dir = dir() ?: return emptyList()
        return onIo { io.list(dir) }.filter { !it.name.startsWith('.') }.sortedBy { it.name }
    }

    override suspend fun read(name: String): ByteArray? {
        AppFolder.checkName(name)
        val dir = dir() ?: return null
        return onIo { io.read("$dir/$name") }
    }

    override suspend fun write(name: String, bytes: ByteArray) {
        AppFolder.checkName(name)
        val dir = checkNotNull(dir()) { "This device has no folder for ${appId.value} yet" }
        onIo { io.write(dir, name, bytes) }
    }

    override suspend fun delete(name: String): Boolean {
        AppFolder.checkName(name)
        val dir = dir() ?: return false
        return onIo { io.delete("$dir/$name") }
    }
}
