package cx.m42.superizer.testing

import cx.m42.superizer.runtime.AppFolder
import cx.m42.superizer.runtime.DownloadTooLarge
import cx.m42.superizer.runtime.FileDownloader
import cx.m42.superizer.runtime.FolderFile
import cx.m42.superizer.runtime.NetworkException
import cx.m42.superizer.runtime.QrScanner

/**
 * An app's folder in memory (0.8.2). [files] is the folder itself, so a test can put a file there the way a person
 * would drop one in, and read what the app wrote. Names go through the same [AppFolder.checkName] as the host's.
 */
public class FakeAppFolder(
    override val location: String = "Fixture/test",
    private val now: () -> Long = { 0L },
) : AppFolder {
    public val files: LinkedHashMap<String, ByteArray> = LinkedHashMap()
    private val modified = mutableMapOf<String, Long>()

    /** Makes the next write fail, as a full disk would. */
    public var failWrites: Boolean = false

    public fun put(name: String, bytes: ByteArray, modifiedAt: Long = now()) {
        AppFolder.checkName(name)
        files[name] = bytes
        modified[name] = modifiedAt
    }

    override suspend fun list(): List<FolderFile> =
        files.entries.map { (name, bytes) -> FolderFile(name, bytes.size.toLong(), modified[name] ?: 0L) }.sortedBy { it.name }

    override suspend fun read(name: String): ByteArray? {
        AppFolder.checkName(name)
        return files[name]?.copyOf()
    }

    override suspend fun write(name: String, bytes: ByteArray) {
        AppFolder.checkName(name)
        if (failWrites) throw IllegalStateException("no space left")
        put(name, bytes.copyOf())
    }

    override suspend fun delete(name: String): Boolean {
        AppFolder.checkName(name)
        modified.remove(name)
        return files.remove(name) != null
    }
}

/**
 * Downloads only what a test named, like [FakeNetwork]: a URL with no stub throws, so a test never reaches the real
 * network by accident. [DownloadTooLarge] is thrown the way the host throws it, from the stub's size and the limit.
 */
public class FakeDownloader : FileDownloader {
    /** Every download asked for, in order, with the limit it was asked with. */
    public val calls: MutableList<Pair<String, Long>> = mutableListOf()

    private val answers = mutableMapOf<String, () -> ByteArray>()

    public fun respond(url: String, bytes: ByteArray) {
        answers[url] = { bytes }
    }

    /** A status other than 2xx; null is no answer at all — offline, DNS, TLS. */
    public fun fail(url: String, status: Int? = null) {
        answers[url] = { throw NetworkException(status, "download failed: $status $url") }
    }

    override suspend fun download(url: String, maxBytes: Long, headers: Map<String, String>): ByteArray {
        calls += url to maxBytes
        val answer = answers[url] ?: error("FakeDownloader has no stub for $url")
        val bytes = answer()
        if (bytes.size > maxBytes) throw DownloadTooLarge(maxBytes)
        return bytes.copyOf()
    }
}

/** The code the camera "reads": [next], or null for a camera the person closed. */
public class FakeQrScanner(public var next: String? = null) : QrScanner {
    public var scans: Int = 0

    override suspend fun scan(): String? {
        scans++
        return next
    }
}
