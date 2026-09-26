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
