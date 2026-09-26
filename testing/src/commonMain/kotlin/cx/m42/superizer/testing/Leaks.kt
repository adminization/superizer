package cx.m42.superizer.testing

import kotlin.io.encoding.Base64

/**
 * A secret a test can recognise anywhere — in a file, a log, a heap dump — without ever holding it
 * whole in memory itself (Unitool ssh-new 03 §3.1, D157).
 *
 * Kept XOR-masked. A test that held its canary as a plain constant would find it in its own class
 * in the heap dump and prove nothing; this one only exists unmasked inside the code under test,
 * which is exactly where the scan is looking.
 */
public class Canary private constructor(
    public val name: String,
    private val masked: ByteArray,
) {
    public val size: Int get() = masked.size

    /** The secret itself, for building a fixture. The caller zeroes it when done. */
    public fun reveal(): ByteArray = ByteArray(masked.size) { (masked[it].toInt() xor MASK).toByte() }

    internal fun maskedCopy(): ByteArray = masked.copyOf()

    override fun toString(): String = "Canary($name, $size bytes)"

    public companion object {
        internal const val MASK: Int = 0x5A

        /** A canary from its masked bytes, as hex — how the constants below are written. */
        public fun masked(name: String, maskedHex: String): Canary =
            Canary(name, maskedHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())

        /** A canary from a secret a test made; [secret] is masked at once and the caller zeroes its copy. */
        public fun of(name: String, secret: ByteArray): Canary =
            Canary(name, ByteArray(secret.size) { (secret[it].toInt() xor MASK).toByte() })

        /** The 32-byte Ed25519 seed `SUPERIZER-CANARY-ED25519-SEED-01` (03 §3.1). */
        public val ED25519_SEED: Canary = masked("ed25519-seed", "090f0a1f0813001f0877191b141b0803771f1e686f6f6b6377091f1f1e776a6b")

        /** The passphrase `canary-pass-Ω-7f3a`, UTF-8 — the Ω is there to catch an encoding slip. */
        public val PASSPHRASE: Canary = masked("passphrase", "393b343b2823772a3b29297794f3776d3c693b")

        /** The first 32 bytes of an age plaintext, `CANARY-PLAINTEXT-otpauth://totp/`. */
        public val PLAINTEXT: Canary = masked("plaintext", "191b141b0803770a161b13140e1f020e77352e2a3b2f2e326075752e352e2a75")
    }
}

/** Where a canary was found. Never the bytes around it: the report must not become the leak. */
public data class LeakFinding(val canary: String, val encoding: String, val offset: Int)

/**
 * Looks for canaries in bytes, in every form a secret takes on its way out (03 §3.2): raw, Base64
 * (standard and URL-safe, at each of the three alignments a secret can start at inside a longer
 * text), hex in either case, UTF-16LE (a JVM `String` with a non-Latin-1 character), and any
 * 16-byte window of each canary raw and in Base64 — half a key is a leak too.
 *
 * One pass over the haystack whatever the number of patterns: patterns are indexed by their first
 * two bytes, so a heap dump of tens of megabytes is scanned in seconds on a phone.
 */
public object LeakScanner {

    private const val WINDOW = 16

    public fun scan(haystack: ByteArray, canaries: List<Canary>): List<LeakFinding> {
        val patterns = canaries.flatMap { patterns(it) }.filter { it.bytes.size >= 2 }
        val table = arrayOfNulls<MutableList<Int>>(65_536)
        patterns.forEachIndexed { index, p ->
            val key = ((p.bytes[0].toInt() and 0xff) shl 8) or (p.bytes[1].toInt() and 0xff)
            (table[key] ?: mutableListOf<Int>().also { table[key] = it }) += index
        }
        val found = LinkedHashSet<LeakFinding>()
        for (i in 0 until haystack.size - 1) {
            val key = ((haystack[i].toInt() and 0xff) shl 8) or (haystack[i + 1].toInt() and 0xff)
            val candidates = table[key] ?: continue
            for (index in candidates) {
                val p = patterns[index]
                if (matches(haystack, i, p.bytes)) found += LeakFinding(p.canary, p.encoding, i)
            }
        }
        patterns.forEach { it.bytes.fill(0) }
        return found.toList()
    }

    /** Text, as it would sit in a file: its UTF-8 bytes. */
    public fun scan(text: String, canaries: List<Canary>): List<LeakFinding> = scan(text.encodeToByteArray(), canaries)

    private class Pattern(val canary: String, val encoding: String, val bytes: ByteArray)

    private fun matches(haystack: ByteArray, at: Int, needle: ByteArray): Boolean {
        if (at + needle.size > haystack.size) return false
        for (k in needle.indices) if (haystack[at + k] != needle[k]) return false
        return true
    }

    private fun patterns(canary: Canary): List<Pattern> {
        val secret = canary.reveal()
        try {
            val out = mutableListOf<Pattern>()
            fun add(encoding: String, bytes: ByteArray) = out.add(Pattern(canary.name, encoding, bytes))
            add("raw", secret.copyOf())
            add("hex", hex(secret, upper = false))
            add("HEX", hex(secret, upper = true))
            base64Inner(secret).forEach { (name, bytes) -> add(name, bytes) }
            utf16le(secret)?.let { add("utf16le", it) }
            if (secret.size > WINDOW) {
                for (start in 0..secret.size - WINDOW) {
                    val window = secret.copyOfRange(start, start + WINDOW)
                    add("raw[$start+16]", window.copyOf())
                    base64Inner(window).forEach { (name, bytes) -> add("$name[$start+16]", bytes) }
                    window.fill(0)
                }
            }
            return out
        } finally {
            secret.fill(0)
        }
    }

    /**
     * The characters of the Base64 of [secret] that depend on nothing but [secret], for each way it
     * can be aligned inside a longer encoding: with `a` bytes before it, the first `⌈8a/6⌉`
     * characters carry the neighbour's bits and so does the tail.
     */
    private fun base64Inner(secret: ByteArray): List<Pair<String, ByteArray>> = buildList {
        for (a in 0..2) {
            val padded = ByteArray(a) + secret
            val encoded = Base64.Default.withPadding(Base64.PaddingOption.ABSENT).encode(padded)
            padded.fill(0)
            val from = (8 * a + 5) / 6
            val to = (8 * a + 8 * secret.size) / 6
            if (to - from < 4) continue
            val inner = encoded.substring(from, to)
            add("base64@$a" to inner.encodeToByteArray())
            add("base64url@$a" to inner.replace('+', '-').replace('/', '_').encodeToByteArray())
        }
    }

    private fun hex(bytes: ByteArray, upper: Boolean): ByteArray {
        val digits = if (upper) "0123456789ABCDEF" else "0123456789abcdef"
        return ByteArray(bytes.size * 2) { i ->
            val b = bytes[i / 2].toInt() and 0xff
            digits[if (i % 2 == 0) b ushr 4 else b and 0x0f].code.toByte()
        }
    }

    /** How the JVM keeps a `String` with a character outside Latin-1; null for bytes that are not UTF-8 text. */
    private fun utf16le(bytes: ByteArray): ByteArray? {
        val text = runCatching { bytes.decodeToString(throwOnInvalidSequence = true) }.getOrNull() ?: return null
        return ByteArray(text.length * 2) { i ->
            val c = text[i / 2].code
            (if (i % 2 == 0) c and 0xff else c ushr 8).toByte()
        }
    }
}
