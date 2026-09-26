package cx.m42.superizer.testing

import cx.m42.superizer.diagnostics.AppDiagnostics
import cx.m42.superizer.diagnostics.DiagnosticsReport
import cx.m42.superizer.diagnostics.Finding
import cx.m42.superizer.diagnostics.StorageDiagnostics
import cx.m42.superizer.runtime.FilePicker
import cx.m42.superizer.runtime.PickedFile
import cx.m42.superizer.runtime.Platform
import cx.m42.superizer.runtime.StorageService
import cx.m42.superizer.secrets.SecretVault
import cx.m42.superizer.secrets.SecretsUnavailableException
import cx.m42.superizer.ssh.KeyId
import cx.m42.superizer.ssh.KeyPurpose
import cx.m42.superizer.ssh.SshKeyInfo
import cx.m42.superizer.ssh.SshKeyring
import cx.m42.superizer.ssh.SshOutcome
import cx.m42.superizer.ssh.SshSignature
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.random.Random

/*
 * Contract 3, faked (Unitool ssh-new 07 §2.1): the vault, an app's secrets, its diagnostics, the
 * keyring and the file picker. As short as every other fake here — the library has no cryptography,
 * so a fake that needs some takes it from the test as a lambda.
 */

/**
 * A vault that really transforms what it seals — so a test that scans storage for a secret finds
 * nothing — and that a test can break: lose its key, refuse to open (a declined passphrase), refuse
 * to seal, or ask before opening like a passphrase vault does.
 */
public class FakeSecretVault(private val name: String = "fake") : SecretVault {

    private val key = Random.nextBytes(32)

    /** Every open after this returns null: the chip dropped the key. */
    public var keyLost: Boolean = false

    /** The person declined the prompt: open returns null, but the key is still there. */
    public var declines: Boolean = false

    /** seal throws, as a Keystore that is not available does. */
    public var failsToSeal: Boolean = false

    /** What [opensSilently] answers; a passphrase vault before its session is `false`. */
    public var silent: Boolean = true

    /** How many times [open] had to ask, i.e. was called while not [silent]. */
    public var asked: Int = 0
        private set

    public var sealed: Int = 0
        private set

    override val opensSilently: Boolean get() = silent

    override suspend fun seal(plaintext: String): String {
        check(!failsToSeal) { "$name: keystore unavailable" }
        sealed++
        val nonce = Random.nextBytes(8)
        val body = xor(plaintext.encodeToByteArray(), nonce)
        return "$name:" + hex(nonce) + ":" + hex(body) + ":" + hex(tag(nonce, body))
    }

    override suspend fun open(sealed: String): String? {
        if (!silent) asked++
        if (keyLost || declines) return null
        val parts = sealed.split(':')
        if (parts.size != 4 || parts[0] != name) return null
        val nonce = unhex(parts[1]) ?: return null
        val body = unhex(parts[2]) ?: return null
        if (!tag(nonce, body).contentEquals(unhex(parts[3]) ?: return null)) return null
        return xor(body, nonce).decodeToString()
    }

    private fun xor(data: ByteArray, nonce: ByteArray): ByteArray =
        ByteArray(data.size) { i -> (data[i].toInt() xor key[(i + nonce[i % 8]) and 31].toInt() xor nonce[i % 8].toInt()).toByte() }

    /** Not a MAC anyone should use; enough that a flipped bit is noticed, which is what a test asks of it. */
    private fun tag(nonce: ByteArray, body: ByteArray): ByteArray {
        var h = 1469598103934665603L
        (key + nonce + body).forEach { h = (h xor (it.toLong() and 0xff)) * 1099511628211L }
        return ByteArray(8) { (h ushr (8 * it)).toByte() }
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    private fun unhex(text: String): ByteArray? =
        if (text.length % 2 != 0) null else runCatching { ByteArray(text.length / 2) { text.substring(2 * it, 2 * it + 2).toInt(16).toByte() } }.getOrNull()
}

/**
 * An app's `runtime.secrets` in a test: a map the test can read, plus the two failures an app has
 * to survive — a secret that is there but cannot be opened now, and a host that cannot seal.
 */
public class FakeSecrets(initial: Map<String, String> = emptyMap()) : StorageService {
    public val entries: MutableMap<String, String> = initial.toMutableMap()

    /** `get` of a stored key throws [SecretsUnavailableException], as the host's does when its vault declines. */
    public var unavailable: Boolean = false

    /** `set` throws, as the host's does when its vault cannot seal. Nothing is written. */
    public var failsToSeal: Boolean = false

    override suspend fun get(key: String): String? {
        val value = entries[key] ?: return null
        if (unavailable) throw SecretsUnavailableException()
        return value
    }

    override suspend fun set(key: String, value: String) {
        check(!failsToSeal) { "vault unavailable" }
        entries[key] = value
    }

    override suspend fun remove(key: String) {
        entries.remove(key)
    }

    override suspend fun keys(): Set<String> = entries.keys.toSet()
}

/** `runtime.diagnostics`, set by the test. */
public class FakeAppDiagnostics(initial: List<Finding> = emptyList()) : AppDiagnostics {
    override val findings: MutableStateFlow<List<Finding>> = MutableStateFlow(initial)
}

/** The host's storage report, set by the test; the self-test counts its runs and returns the same report. */
public class FakeStorageDiagnostics(
    initial: DiagnosticsReport = DiagnosticsReport(at = 0, hostVersion = "0.0.0", platform = Platform.Desktop),
) : StorageDiagnostics {
    override val report: MutableStateFlow<DiagnosticsReport> = MutableStateFlow(initial)
    public var selfTests: Int = 0
        private set

    override suspend fun inspect(): DiagnosticsReport = report.value

    override suspend fun selfTest(withConfirmation: Boolean): DiagnosticsReport {
        selfTests++
        return report.value
    }

    override fun exportText(): String = report.value.toString()
}

/**
 * The keyring an app sees, with its sheet answered by the test. The operations are the test's
 * lambdas — the library has no cryptography, so a test that needs a real decryption brings one.
 * [calls] records what the app asked for, by name, in order: `choose`, `sign:<namespace>`, `decrypt`.
 */
public class FakeSshKeyring(initial: List<SshKeyInfo> = emptyList()) : SshKeyring {
    override val keys: MutableStateFlow<List<SshKeyInfo>> = MutableStateFlow(initial)

    public val calls: MutableList<String> = mutableListOf()

    public var onChoose: suspend (KeyPurpose) -> SshOutcome<SshKeyInfo> = { SshOutcome.NoKey }
    public var onSign: suspend (KeyId, String, ByteArray) -> SshOutcome<SshSignature> = { _, _, _ -> SshOutcome.NoKey }
    public var onDecrypt: suspend (ByteArray) -> SshOutcome<ByteArray> = { SshOutcome.NoKey }

    override suspend fun choose(purpose: KeyPurpose): SshOutcome<SshKeyInfo> {
        calls += "choose"
        return onChoose(purpose)
    }

    override suspend fun sign(key: KeyId, namespace: String, message: ByteArray): SshOutcome<SshSignature> {
        calls += "sign:$namespace"
        return onSign(key, namespace, message)
    }

    override suspend fun decrypt(file: ByteArray): SshOutcome<ByteArray> {
        calls += "decrypt"
        return onDecrypt(file)
    }
}

/** The file the person "picks": [next], or null for a picker they closed. */
public class FakeFilePicker(public var next: PickedFile? = null) : FilePicker {
    public val asked: MutableList<List<String>> = mutableListOf()

    override suspend fun pick(mimeTypes: List<String>): PickedFile? {
        asked += mimeTypes
        return next
    }
}
