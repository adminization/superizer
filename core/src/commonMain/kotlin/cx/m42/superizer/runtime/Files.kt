package cx.m42.superizer.runtime

/**
 * The platform's own "open a file" (04 §9, Б6): the Storage Access Framework, `UIDocumentPicker`,
 * a file dialog, `<input type=file>`. An optional service (D19); the host implements it.
 *
 * The person picks; the app gets the bytes and nothing else — no path, no permission to come back
 * for more. Whoever picked a private key zeroes [PickedFile.bytes] when done with them.
 */
public interface FilePicker {

    /** Null when the person closed the picker. [mimeTypes] narrow the list where the platform can; empty is anything. */
    public suspend fun pick(mimeTypes: List<String> = emptyList()): PickedFile?

    public companion object {
        public val Key: ServiceKey<FilePicker> = ServiceKey("file-picker")
    }
}

/** A class and not a data class: a generated `toString` would print the bytes. */
public class PickedFile(public val name: String, public val bytes: ByteArray) {
    override fun toString(): String = "PickedFile($name, ${bytes.size} bytes)"
}

/**
 * A folder of the app's own that the person sees too (0.8.2), the way a video player keeps its films: what the app
 * puts there is a file they can find, copy in and take out. The host makes one per app, named after the app's id.
 * An optional service (D19).
 *
 * | Platform | Where | Seen in |
 * |---|---|---|
 * | iOS | `Documents/<app id>` of the host | Files › On My iPhone › the host, once the host's Info.plist sets `UIFileSharingEnabled` and `LSSupportsOpeningDocumentsInPlace` |
 * | Desktop | `<documents>/<host name>/<app id>` | any file manager |
 * | Android | `Android/data/<package>/files/<app id>` | a computer over USB |
 * | Web | none: the service is absent | |
 *
 * It outlives what the platform lets it outlive: a reinstall on desktop; offloading the app and a restored backup on
 * iOS. Deleting the app deletes it on iOS and Android — the platform's rule, not the host's.
 *
 * Flat: a name is a plain file name, and [checkName] turns away anything that could reach outside. Nothing is
 * created before the first [write]. The person may add, rename or delete files there at any time, so [list] is what
 * is there, not what the app last wrote.
 */
public interface AppFolder {
    /** Where the person finds it, in the platform's terms: a path on desktop and Android, `<host>/<app id>` on iOS. */
    public val location: String

    /** The files in it, hidden ones and folders left out; empty when it has none or does not exist yet. */
    public suspend fun list(): List<FolderFile>

    /** Null when there is no such file. */
    public suspend fun read(name: String): ByteArray?

    /** Whole or not at all: written beside it under a hidden name, then moved over the old one. */
    public suspend fun write(name: String, bytes: ByteArray)

    /** False when there was nothing to delete. */
    public suspend fun delete(name: String): Boolean

    public companion object {
        public val Key: ServiceKey<AppFolder> = ServiceKey("app-folder")

        /**
         * What every implementation accepts as a name, here so a fake turns away the same ones: not empty, at most
         * 255 characters, no `/`, `\`, `:` or control character, not hidden. Throws [IllegalArgumentException].
         */
        public fun checkName(name: String) {
            require(name.isNotEmpty() && name.length <= 255) { "A file name is 1 to 255 characters: \"$name\"" }
            require(!name.startsWith('.')) { "A file name in an app folder is not hidden: \"$name\"" }
            require(name.none { it == '/' || it == '\\' || it == ':' || it.code < 0x20 || it.code == 0x7f }) {
                "A file name has no / \\ : or control characters: \"$name\""
            }
        }
    }
}

/** A file in an [AppFolder]: its name, its size in bytes and when it last changed, in epoch milliseconds. */
public data class FolderFile(val name: String, val size: Long, val modified: Long)

/**
 * A file over HTTP, as bytes (0.8.2): what [NetworkService], strings in and strings out, cannot carry. An optional
 * service (D19), offered by a host that talks to the network with its own client.
 *
 * A GET with the same rule as the rest of the network (D32): no credentials the app did not set itself. The app names
 * the most it will take, because a link a person pasted can lead anywhere. Two minutes rather than fifteen seconds:
 * a file is bigger than an answer.
 */
public interface FileDownloader {
    /**
     * The body of a 2xx answer. Throws [NetworkException] with the status for any other status and with a null status
     * when no answer came; [DownloadTooLarge] as soon as the body passes [maxBytes], before reading it when the server
     * says how long it is.
     */
    public suspend fun download(url: String, maxBytes: Long, headers: Map<String, String> = emptyMap()): ByteArray

    public companion object {
        public val Key: ServiceKey<FileDownloader> = ServiceKey("file-downloader")
    }
}

/** The file at the other end is longer than the app was willing to take. */
public class DownloadTooLarge(public val maxBytes: Long) : Exception("the file is larger than $maxBytes bytes")

/**
 * The host's camera reading one QR code (0.8.2): the scanner behind its Activate screen, offered to apps as well. An
 * optional service (D19): absent where the host has none — desktop, the web, a host that set no `qrScanner` — so an
 * app draws its scan button only when `runtime.service(QrScanner.Key)` answers.
 */
public fun interface QrScanner {
    /** The text the code carries, as it is, or null when the person closed the camera. The app decides what it means. */
    public suspend fun scan(): String?

    public companion object {
        public val Key: ServiceKey<QrScanner> = ServiceKey("qr-scanner")
    }
}
