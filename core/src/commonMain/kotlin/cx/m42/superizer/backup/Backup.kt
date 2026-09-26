package cx.m42.superizer.backup

import cx.m42.superizer.runtime.Platform
import cx.m42.superizer.ssh.SshOutcome
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What of an app goes into the host's backup (05 §3.3, D172). The only thing an app says about
 * backups, and it says it by declaration, in its manifest.
 *
 * Anything but [All] needs `minHostContract = 3`: an older host has never heard of the field and
 * would back up what the app asked it not to (D138).
 */
@Serializable
public sealed interface BackupPolicy {
    /** Storage and secrets, every key. The default. */
    @Serializable
    @SerialName("all")
    public data object All : BackupPolicy

    /** Nothing: a calculator with no state, a cache. */
    @Serializable
    @SerialName("none")
    public data object None : BackupPolicy

    /** Everything but these keys, in storage and in secrets alike. */
    @Serializable
    @SerialName("except")
    public data class Except(val keys: Set<String>) : BackupPolicy
}

/**
 * Encrypts the bundle to the person's public keys and opens it with one of their private ones — age
 * on the host's side (D181); the library has no cryptography.
 */
public interface HostBackupCipher {

    /** [recipients]: `authorized_keys` lines — what `github.com/<login>.keys` serves. */
    public suspend fun encrypt(bundle: ByteArray, recipients: List<String>): ByteArray

    /** With the person's keyring, through its sheet. */
    public suspend fun decrypt(file: ByteArray): SshOutcome<ByteArray>
}

/** The backup, as JSON (05 §3.3). The host reads no app's values; it carries them. */
@Serializable
public data class BackupBundle(
    val schema: Int = SCHEMA,
    val host: BackupHost,
    /** The host's own keys (`host.*`), minus the session snapshot. */
    val settings: Map<String, String> = emptyMap(),
    /** By app id. */
    val apps: Map<String, AppBackup> = emptyMap(),
    /** The keys the person had, by name: a hint to import them again, never the keys (D170). */
    val keys: List<BackupKeyName> = emptyList(),
) {
    public companion object {
        public const val SCHEMA: Int = 1
    }
}

@Serializable
public data class BackupHost(
    val name: String,
    val version: String,
    val contract: Int,
    val platform: Platform,
    val at: Long,
)

@Serializable
public data class AppBackup(
    /** The app's version that wrote these values, so a newer app can refuse a format it does not know. */
    val version: String?,
    val storage: Map<String, String> = emptyMap(),
    /** Opened by the host's vault before they went into the bundle, sealed again by the new host's on restore. */
    val secrets: Map<String, String> = emptyMap(),
)

@Serializable
public data class BackupKeyName(
    val fingerprint: String,
    val comment: String,
    val level: Int,
)

/** What a backup would carry, before it is made — for the screen that asks. */
public data class BackupPreview(
    val apps: List<AppPreview>,
    val keys: List<BackupKeyName>,
)

public data class AppPreview(
    val id: String,
    val storageKeys: Int,
    val secretKeys: Int,
    /** Declared [BackupPolicy.None]: listed so the screen can say it is left out. */
    val excluded: Boolean,
)

/** A backup file, opened and read, not yet written anywhere. */
public class RestorePlan(
    public val bundle: BackupBundle,
    public val apps: List<AppPreview>,
) {
    override fun toString(): String = "RestorePlan(${apps.size} apps, ${bundle.keys.size} key names)"
}

public data class RestoreReport(
    val restored: List<String>,
    /** Keys the old device had and this one does not: to import again (05 §3.1). */
    val keysToImport: List<BackupKeyName>,
)

public sealed interface BackupOutcome<out T> {
    public data class Ok<T>(val value: T) : BackupOutcome<T>

    /** The person declined the confirmation, the passphrase or the key. */
    public data object Cancelled : BackupOutcome<Nothing>

    /** The host has no cipher. */
    public data object Unsupported : BackupOutcome<Nothing>

    /** No recipients, or none the cipher can encrypt to. */
    public data object NoRecipients : BackupOutcome<Nothing>

    /** A secret could not be opened, so a backup would be missing it; nothing was made. */
    public data object SecretsLocked : BackupOutcome<Nothing>

    /** The file opened but is not a backup this host understands; [code] says how. */
    public data class Unreadable(val code: String) : BackupOutcome<Nothing>

    public data class Failed(val code: String, val evidence: String? = null) : BackupOutcome<Nothing>
}

/** The host's backup (02 §6.8): a walk over the prefixes, sealed to GitHub's keys, and back. */
public interface BackupPort {

    /** When the last backup was made on this device. */
    public val lastBackupAt: StateFlow<Long?>

    public suspend fun preview(): BackupPreview

    /**
     * Confirms with the person ([reason] is what the sheet says), opens every secret with the host's
     * vault, builds the bundle, encrypts it to [recipients]. The file's bytes; sharing them is the
     * caller's.
     */
    public suspend fun export(recipients: List<String>, reason: String): BackupOutcome<ByteArray>

    /**
     * Opens [file] with the cipher and reads it. Writes nothing. A bundle already opened on a
     * computer (`age -d -i ~/.ssh/id_ed25519 … > bundle.json`, 05 §3.4) is read as it is.
     */
    public suspend fun read(file: ByteArray): BackupOutcome<RestorePlan>

    /**
     * Confirms, then replaces the listed [apps] whole — storage as it was, secrets sealed by this
     * device's vault — and, when [settings], the host's own. Merging is not v1 (question 86).
     */
    public suspend fun restore(
        plan: RestorePlan,
        apps: Set<String>,
        settings: Boolean,
        reason: String,
    ): BackupOutcome<RestoreReport>
}
