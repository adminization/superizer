package cx.m42.superizer.ssh

import cx.m42.superizer.app.AppId
import cx.m42.superizer.runtime.Platform
import kotlin.jvm.JvmInline
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * The data of the SSH keyring (Unitool ssh-new, 02 and 05): what a host shows, stores and passes
 * between its keyring and its screens. Data only — no parsing, no cryptography, not a line of text
 * for a person. The implementation lives in the host (D181), and so do the words.
 *
 * Nothing here ever holds the private half of a key. There is no type in this package that could:
 * that is invariant I4, and it starts with the shape of these classes.
 */

/** The shortest passphrase a key may be kept under (D167). Counted in characters, and that is all that is counted. */
public const val MIN_PASSPHRASE: Int = 12

/**
 * A key's identity: its SHA-256 fingerprint, `SHA256:…`, exactly as `ssh-keygen -l` prints it.
 * The same key imported twice is the same id — "already here" rather than a second copy.
 */
@Serializable
@JvmInline
public value class KeyId(public val fingerprint: String) {
    override fun toString(): String = fingerprint
}

/**
 * Where a key's private half lives on *this* device (05 §2): the three levels, and which chip.
 *
 * There is no "plain": a key without a passphrase is never accepted (D167), so the weakest thing
 * a key can be is sealed by its own passphrase.
 */
@Serializable
public sealed interface KeyStorage {
    /** 1 in the chip, 2 sealed by a key from the chip, 3 sealed by the key's passphrase. */
    public val level: Int

    /** Level 1 on Android: a separate secure element (Titan M, Knox Vault). */
    @Serializable
    @SerialName("strongbox")
    public data object StrongBox : KeyStorage {
        override val level: Int get() = 1
    }

    /** Level 1 on Android: the trusted environment of the main processor. */
    @Serializable
    @SerialName("tee")
    public data object Tee : KeyStorage {
        override val level: Int get() = 1
    }

    /** Level 1 on iOS: only a P-256 key made inside the enclave (05 §4.3). */
    @Serializable
    @SerialName("secure-enclave")
    public data object SecureEnclave : KeyStorage {
        override val level: Int get() = 1
    }

    /** Level 2: in the app's files, encrypted by a key that lives in [under] and needs the person to use. */
    @Serializable
    @SerialName("sealed-by-chip")
    public data class SealedByChip(val under: Chip) : KeyStorage {
        override val level: Int get() = 2
    }

    /** Level 3: the original `openssh-key-v1`, under its own passphrase and nothing else. */
    @Serializable
    @SerialName("sealed-by-passphrase")
    public data object SealedByPassphrase : KeyStorage {
        override val level: Int get() = 3
    }
}

@Serializable
public enum class Chip { StrongBox, Tee, SecureEnclave }

/** What this device can offer a key (05 §2.1). The facts behind every "where it would live" in the wizard. */
@Serializable
public data class KeyringDevice(
    val platform: Platform,
    /** Android's API level; null elsewhere. */
    val sdk: Int? = null,
    val strongBox: Boolean = false,
    /** A Keystore backed by a TEE rather than by software. */
    val tee: Boolean = false,
    val secureEnclave: Boolean = false,
    /** Ed25519 and X25519 can be imported into the Keystore (step 0, P1/P2). */
    val curve25519InKeystore: Boolean = false,
    /** A PIN, pattern, password or passcode is set; levels 1 and 2 need one. */
    val screenLockSet: Boolean = false,
    /** The levels a key can be kept at here, right now. Always contains 3. */
    val levels: Set<Int> = setOf(3),
)

public enum class KeyCapability { Sign, DecryptAge }

@Serializable
public sealed interface KeyOrigin {
    @Serializable
    @SerialName("imported")
    public data object Imported : KeyOrigin

    /** Made on this device. [attested]: a hardware attestation chain exists (Android StrongBox/TEE, 01 §4.4). */
    @Serializable
    @SerialName("generated")
    public data class Generated(val attested: Boolean) : KeyOrigin
}

/** What "Create a key" makes (01 §4.4). */
public enum class GeneratedKind {
    /** Signs and decrypts age. Level 1 where the Keystore takes Curve25519, else 2 or 3. */
    Ed25519,

    /** Signs only. The one way to level 1 on iOS (Secure Enclave); StrongBox or TEE on Android. */
    EcdsaP256,
}

/** How often the person is asked (01 §5.1, §5.3). */
@Serializable
public sealed interface UsePolicy {
    /** Every signature, every decryption. The default. */
    @Serializable
    @SerialName("every-use")
    public data object EveryUse : UsePolicy

    /** `ssh-add -t`: after one confirmation the key works for [seconds]. At most 300 on iOS for levels 1–2. */
    @Serializable
    @SerialName("session")
    public data class Session(val seconds: Int) : UsePolicy
}

/** A key as everyone but the keyring sees it: the public half and the facts about where the private half is. */
@Serializable
public data class SshKeyInfo(
    val id: KeyId,
    /** `ssh-ed25519`, `ssh-rsa`, `ecdsa-sha2-nistp256`, … */
    val type: String,
    /** `type base64`, without a comment: the line for `authorized_keys`. */
    val publicKey: String,
    /** What `ssh-keygen -l` puts in brackets: `ED25519`, `RSA 3072`, `ECDSA 256`. */
    val label: String,
    val comment: String,
    val storage: KeyStorage,
    val policy: UsePolicy,
    val origin: KeyOrigin,
    val can: Set<KeyCapability>,
    val createdAt: Long,
    /** False once the chip dropped the key — the screen lock was removed (01 §5.7). The public half stays. */
    val usable: Boolean = true,
)

/** What a file is, before any passphrase (02 §5, §8). */
@Serializable
public data class Inspection(
    /** Null when [refusal] says it is not a key at all. */
    val type: String? = null,
    val id: KeyId? = null,
    val label: String? = null,
    val comment: String? = null,
    /** The file is under a passphrase. False is never imported as is (D167): the wizard asks for one. */
    val encrypted: Boolean = false,
    /** bcrypt rounds of an encrypted file (24 by default in OpenSSH 9.6). */
    val rounds: Int? = null,
    /** Where it would live if imported now; null when it cannot be imported. */
    val wouldBe: KeyStorage? = null,
    /** Why it cannot be imported; null when it can. */
    val refusal: ImportRefusal? = null,
    /** A key with this fingerprint is already in the keyring. */
    val alreadyThere: Boolean = false,
)

/**
 * Why a file is turned away (02 §8). Each has a command that fixes it on the computer; the command
 * and the words are the host's.
 */
@Serializable
public enum class ImportRefusal {
    NotAKey,
    /** `.pub`: the public half. */
    PublicKeyOnly,
    /** PEM or PKCS#8 (`BEGIN RSA PRIVATE KEY`, …): `ssh-keygen -p` rewrites it. */
    OldFormat,
    PuttyFormat,
    /** chacha20-poly1305, CBC, 3DES: `ssh-keygen -p -Z aes256-ctr`. */
    UnsupportedCipher,
    UnsupportedKdf,
    /** `sk-…`: the key lives on a hardware token. */
    HardwareToken,
    Dsa,
    RsaTooShort,
    UnsupportedType,
    /** Checks agreed with nothing, or the inner public key differs from the outer one: edited by hand. */
    Corrupted,
}

/** Chosen in the wizard (02 §5). */
@Serializable
public data class ImportOptions(
    val policy: UsePolicy = UsePolicy.EveryUse,
    /** Null keeps the file's own comment. */
    val comment: String? = null,
    /**
     * Level 2 only (01 §5.4): keep the file's passphrase as a second lock on every use. Level 3 has
     * no other lock, so it is ignored there (05).
     */
    val keepPassphrase: Boolean = false,
)

public sealed interface ImportOutcome {
    public data class Imported(val key: SshKeyInfo) : ImportOutcome

    public data class AlreadyThere(val key: SshKeyInfo) : ImportOutcome

    public data object WrongPassphrase : ImportOutcome

    /**
     * No passphrase, or one shorter than [MIN_PASSPHRASE] (D167): nothing was stored. Call again with
     * a new passphrase; [length] is what the file had, 0 for none.
     */
    public data class NeedsNewPassphrase(val length: Int) : ImportOutcome

    public data class Rejected(val refusal: ImportRefusal) : ImportOutcome

    /** Levels 1–2 were the only way and there is no screen lock to bind the key to (01 §5.6). */
    public data object NoScreenLock : ImportOutcome

    public data object Cancelled : ImportOutcome

    /** [code] is stable and names the failure; [evidence] is the platform's own error, never input. */
    public data class Failed(val code: String, val evidence: String? = null) : ImportOutcome
}

/** What an app is allowed to do with a key (02 §3). */
@Serializable
public sealed interface KeyPurpose {
    /** SSHSIG under [namespace] — `ssh-keygen -Y sign -n`. */
    @Serializable
    @SerialName("sign")
    public data class Sign(val namespace: String) : KeyPurpose

    /** `age -d -i`. */
    @Serializable
    @SerialName("decrypt")
    public data object Decrypt : KeyPurpose
}

/** A grant, made only by the person in the host's sheet (D153). */
@Serializable
public data class Grant(
    val app: AppId,
    val key: KeyId,
    val purpose: KeyPurpose,
    /** The brick behind a composed screen (idea/04); a changed brick is a different grantee. */
    val brick: String? = null,
    val grantedAt: Long,
)

public enum class KeyOperation { Sign, Decrypt, AgentSign, Import, Generate, Delete, Rekey, Grant, Revoke }

/** One line of a key's journal: who, when, what — and of the data only the first 8 hex of its hash. */
@Serializable
public data class UseRecord(
    val at: Long,
    /** Null for the host's own use. */
    val app: AppId?,
    val key: KeyId,
    val operation: KeyOperation,
    val namespace: String? = null,
    val digest: String? = null,
    /** `ok`, `cancelled`, `refused`, `failed:<code>`. */
    val outcome: String,
)

/** An armored `-----BEGIN SSH SIGNATURE-----` block, as `ssh-keygen -Y sign` writes it. */
@Serializable
@JvmInline
public value class SshSignature(public val armored: String)

/**
 * The answer of every keyring operation. A sealed type rather than exceptions, so that "the person
 * said no" is a value the caller has to look at and never a stack trace in a log.
 */
public sealed interface SshOutcome<out T> {
    public data class Ok<T>(val value: T) : SshOutcome<T>

    /** The person closed the sheet or the passphrase prompt. */
    public data object Cancelled : SshOutcome<Nothing>

    /** The person declined to grant this app the key. */
    public data object Refused : SshOutcome<Nothing>

    /** Not for apps: a reserved or malformed namespace, SHA-1 RSA, raw signing. [reason] is a stable code. */
    public data class NotAllowed(val reason: String) : SshOutcome<Nothing>

    /** None of the person's keys fits: no key at all, or none a file's stanzas are for. */
    public data object NoKey : SshOutcome<Nothing>

    /** The key exists but the chip dropped it (01 §5.7). */
    public data object Unusable : SshOutcome<Nothing>

    /** Another prompt is already up; one at a time. */
    public data object Busy : SshOutcome<Nothing>

    /** The agent is locked (`ssh-add -x`). */
    public data object Locked : SshOutcome<Nothing>

    public data class Failed(val code: String, val evidence: String? = null) : SshOutcome<Nothing>
}

/** One identity as `ssh-add -l` lists it. */
public class AgentIdentity(public val publicBlob: ByteArray, public val comment: String) {
    override fun equals(other: Any?): Boolean =
        other is AgentIdentity && other.publicBlob.contentEquals(publicBlob) && other.comment == comment

    override fun hashCode(): Int = publicBlob.contentHashCode() * 31 + comment.hashCode()

    override fun toString(): String = "AgentIdentity(${publicBlob.size} bytes, $comment)"
}

/** `ssh-add -t`, `-c`, `-h` (02 §2). */
public sealed interface AgentConstraint {
    public data class Lifetime(val seconds: Long) : AgentConstraint

    public data object Confirm : AgentConstraint

    public class Extension(public val name: String, public val body: ByteArray) : AgentConstraint {
        override fun equals(other: Any?): Boolean =
            other is Extension && other.name == name && other.body.contentEquals(body)

        override fun hashCode(): Int = name.hashCode() * 31 + body.contentHashCode()

        override fun toString(): String = "Extension($name, ${body.size} bytes)"
    }
}
