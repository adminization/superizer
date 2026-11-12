package cx.m42.superizer.host.files

import cx.m42.superizer.app.AppId
import cx.m42.superizer.host.platform.AndroidHost
import cx.m42.superizer.runtime.FolderFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal actual fun platformFileIo(): FileIo? = JavaFileIo

/**
 * The host's external files dir, which a computer sees over USB as `Android/data/<package>/files`; its private files
 * dir on a device with no external storage. Null before `AndroidHost.init`.
 */
internal actual fun defaultFolderRoot(hostName: String): String? {
    val context = AndroidHost.appContext ?: return null
    return (context.getExternalFilesDir(null) ?: context.filesDir).path
}

internal actual fun describeFolder(path: String, hostName: String, appId: AppId, isDefault: Boolean): String = path

internal actual suspend fun <T> onIo(block: () -> T): T = withContext(Dispatchers.IO) { block() }

/** java.io as on desktop, but by `renameTo`: `java.nio.file` needs API 26, and a rename within one folder is atomic here. */
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
        if (!part.renameTo(File(folder, name))) {
            part.delete()
            throw IOException("cannot write $dir/$name")
        }
    }

    override fun delete(path: String): Boolean = File(path).let { it.isFile && it.delete() }
}
