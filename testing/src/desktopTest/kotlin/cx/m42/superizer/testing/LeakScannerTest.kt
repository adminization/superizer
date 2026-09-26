package cx.m42.superizer.testing

import java.util.Base64
import java.util.HexFormat
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The scanner finds a canary in every form 03 §3.2 names, at any offset — and finds nothing where
 * there is nothing. Each case hides the canary in random bytes, because a scanner tested only on
 * clean input is a scanner tested on nothing.
 */
class LeakScannerTest {

    private val seed = Canary.ED25519_SEED
    private fun noise(n: Int) = Random(n).nextBytes(n)

    private fun hidden(form: ByteArray, before: Int = 37): ByteArray = noise(before) + form + noise(53)

    private fun encodings(found: List<LeakFinding>) = found.map { it.encoding }.toSet()

    @Test
    fun rawAtAnOffset() {
        val found = LeakScanner.scan(hidden(seed.reveal()), listOf(seed))
        assertTrue("raw" in encodings(found))
        assertEquals(37, found.first { it.encoding == "raw" }.offset)
    }

    @Test
    fun base64AtEveryAlignmentBothAlphabets() {
        for (lead in 0..2) {
            val encoded = Base64.getEncoder().encodeToString(ByteArray(lead) { 0x7f } + seed.reveal() + byteArrayOf(1, 2))
            assertTrue(LeakScanner.scan(hidden(encoded.encodeToByteArray()), listOf(seed)).any { it.encoding == "base64@$lead" }, "std @$lead")
            val url = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(lead) { -1 } + seed.reveal())
            assertTrue(LeakScanner.scan(hidden(url.encodeToByteArray()), listOf(seed)).any { it.encoding.startsWith("base64") }, "url @$lead")
        }
    }

    @Test
    fun hexUtf16AndHalfAKey() {
        val hex = HexFormat.of().formatHex(seed.reveal())
        assertTrue("hex" in encodings(LeakScanner.scan(hidden(hex.encodeToByteArray()), listOf(seed))))
        assertTrue("HEX" in encodings(LeakScanner.scan(hidden(hex.uppercase().encodeToByteArray()), listOf(seed))))
        val utf16 = String(seed.reveal(), Charsets.US_ASCII).toByteArray(Charsets.UTF_16LE)
        assertTrue("utf16le" in encodings(LeakScanner.scan(hidden(utf16), listOf(seed))))
        val half = seed.reveal().copyOfRange(9, 25)
        assertTrue(LeakScanner.scan(hidden(half), listOf(seed)).any { it.encoding == "raw[9+16]" })
    }

    @Test
    fun aNonAsciiPassphraseInAJvmString() {
        val text = "prefix " + Canary.PASSPHRASE.reveal().decodeToString() + " suffix"
        assertTrue("utf16le" in encodings(LeakScanner.scan(text.toByteArray(Charsets.UTF_16LE), listOf(Canary.PASSPHRASE))))
        assertTrue("raw" in encodings(LeakScanner.scan(text, listOf(Canary.PASSPHRASE))))
    }

    @Test
    fun nothingInNoiseAndAFindingNamesNoBytes() {
        assertEquals(emptyList(), LeakScanner.scan(noise(200_000), listOf(seed, Canary.PASSPHRASE, Canary.PLAINTEXT)))
        val finding = LeakScanner.scan(hidden(seed.reveal()), listOf(seed)).first()
        assertTrue("SUPERIZER" !in finding.toString())
        assertTrue("SUPERIZER" !in seed.toString())
    }
}
