package cx.m42.superizer.host.files

import cx.m42.superizer.app.AppId
import cx.m42.superizer.runtime.FolderFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal actual fun platformFileIo(): FileIo? = JavaFileIo

/** `<documents>/<host name>`: the person's own documents, where a reinstall of the host does not reach. */
internal actual fun defaultFolderRoot(hostName: String): String? =
    File(documents(), hostName.replace(Regex("""[/\\:]"""), "_").ifBlank { "Superizer" }).path

internal actual fun describeFolder(path: String, hostName: String, appId: AppId, isDefault: Boolean): String = path

internal actual suspend fun <T> onIo(block: () -> T): T = withContext(Dispatchers.IO) { block() }

/**
 * The documents folder as the desktop names it: `XDG_DOCUMENTS_DIR` on Linux, from the environment or from
 * `user-dirs.dirs` (it may be `~/Документы`), and `~/Documents` everywhere else or when XDG says nothing useful.
 */
private fun documents(): File {
    val home = File(System.getProperty("user.home"))
    fun usable(path: String?): File? =
        path?.replace("\$HOME", home.path)?.trim()?.takeIf { it.isNotEmpty() }?.let(::File)?.takeIf { it != home }
    usable(System.getenv("XDG_DOCUMENTS_DIR"))?.let { return it }
    val config = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() } ?: File(home, ".config").path
    val line = Regex("""^\s*XDG_DOCUMENTS_DIR\s*=\s*"(.*)"\s*$""")
    val named = runCatching { File(config, "user-dirs.dirs").readLines() }.getOrNull()
        ?.firstNotNullOfOrNull { line.find(it)?.groupValues?.get(1) }
    return usable(named) ?: File(home, "Documents")
}

/** java.io, with the write done beside the file and moved over it. */
internal object JavaFileIo : FileIo {
    override fun list(dir: String): List<FolderFile> =
        File(dir).listFiles().orEmpty()
            .filter { it.isFile && !it.name.startsWith(".") }
            .map { FolderFile(it.name, it.length(), it.lastModified()) }

    override fun read(path: String): ByteArray? = File(path).takeIf { it.isFile }?.readBytes()

    override fun write(dir: String, name: String, bytes: ByteArray) {
        val folder = File(dir)
        if (!folder.isDirectory && !folder.mkdirs()) throw IOException("cannot create $dir")
        val part = File(folder, ".$name.part")
        FileOutputStream(part).use { out ->
            out.write(bytes)
            out.fd.sync()
        }
        val target = File(folder, name).toPath()
        try {
            Files.move(part.toPath(), target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(part.toPath(), target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    override fun delete(path: String): Boolean = File(path).let { it.isFile && it.delete() }
}
