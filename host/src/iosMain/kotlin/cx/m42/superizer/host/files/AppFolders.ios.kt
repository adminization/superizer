package cx.m42.superizer.host.files

import cx.m42.superizer.app.AppId
import cx.m42.superizer.runtime.FolderFile
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeRegular
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.posix.memcpy

internal actual fun platformFileIo(): FileIo? = DarwinFileIo

/**
 * The host's own Documents: what Files lists under the host's name once its Info.plist sets `UIFileSharingEnabled`
 * and `LSSupportsOpeningDocumentsInPlace`, and what a backup carries.
 */
internal actual fun defaultFolderRoot(hostName: String): String? =
    NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).firstOrNull() as? String

/** Files › On My iPhone shows the host under its name and the app's folder in it. */
internal actual fun describeFolder(path: String, hostName: String, appId: AppId, isDefault: Boolean): String =
    if (isDefault) "$hostName/${appId.value}" else path

internal actual suspend fun <T> onIo(block: () -> T): T = withContext(Dispatchers.IO) { block() }

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal object DarwinFileIo : FileIo {
    private val files: NSFileManager get() = NSFileManager.defaultManager

    override fun list(dir: String): List<FolderFile> {
        val names = files.contentsOfDirectoryAtPath(dir, null) ?: return emptyList()
        return names.mapNotNull { it as? String }.filter { !it.startsWith(".") }.mapNotNull { name ->
            val attributes = files.attributesOfItemAtPath("$dir/$name", null) ?: return@mapNotNull null
            if (attributes[NSFileType] != NSFileTypeRegular) return@mapNotNull null
            val size = (attributes[NSFileSize] as? NSNumber)?.longLongValue ?: 0L
            val modified = (attributes[NSFileModificationDate] as? NSDate)?.timeIntervalSince1970?.let { (it * 1000).toLong() } ?: 0L
            FolderFile(name, size, modified)
        }
    }

    override fun read(path: String): ByteArray? = NSData.dataWithContentsOfFile(path)?.toByteArray()

    override fun write(dir: String, name: String, bytes: ByteArray) {
        if (!files.fileExistsAtPath(dir)) {
            check(files.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)) {
                "cannot create $dir"
            }
        }
        // `atomically` writes beside the file and moves it over, as JavaFileIo does by hand.
        check(bytes.toNSData().writeToFile("$dir/$name", atomically = true)) { "cannot write $dir/$name" }
    }

    override fun delete(path: String): Boolean = files.fileExistsAtPath(path) && files.removeItemAtPath(path, null)

    private fun NSData.toByteArray(): ByteArray {
        val size = length.toInt()
        val out = ByteArray(size)
        if (size > 0) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
        return out
    }

    private fun ByteArray.toNSData(): NSData =
        if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.convert()) }
}
