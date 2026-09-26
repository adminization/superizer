package cx.m42.superizer.ssh

import cx.m42.superizer.runtime.ServiceKey
import kotlinx.coroutines.flow.StateFlow

/**
 * What an app gets of the person's SSH keys (02 §3): SSHSIG under a namespace, and age decryption.
 * An optional service (D19), and a caller-bound one — the host hands every app its own instance,
 * which is how the sheet can say which app is asking and how grants stay per app (D146).
 *
 * Deliberately missing: "sign these bytes". A raw signature over the right bytes is a login to a
 * server as the person (02 §3.1); SSHSIG's `"SSHSIG"` preamble makes that impossible, which is why
 * it is the only signature an app can ask for (D152).
 *
 * Every operation that needs the private half goes through the host's sheet: the grant first if
 * this app has none, then the device or the passphrase, as the key's [UsePolicy] says.
 */
public interface SshKeyring {

    /** Public halves of the keys this app holds a grant for. Nothing else is listed. */
    public val keys: StateFlow<List<SshKeyInfo>>

    /**
     * The person picks one of their keys for [purpose] in the host's sheet and confirms. From then
     * on this app holds a grant for that key and that purpose.
     */
    public suspend fun choose(purpose: KeyPurpose): SshOutcome<SshKeyInfo>

    /**
     * `ssh-keygen -Y sign -n <namespace>`: an armored SSH SIGNATURE over [message]. `*@m42.cx` is the
     * host's and always [SshOutcome.NotAllowed] here; a namespace outside `[A-Za-z0-9@._-]{1,64}`
     * likewise.
     */
    public suspend fun sign(key: KeyId, namespace: String, message: ByteArray): SshOutcome<SshSignature>

    /**
     * `age -d -i <key>`: the plaintext of [file], binary or armored. The key is whichever of the
     * person's keys one of the file's stanzas is for; [SshOutcome.NoKey] when none is.
     */
    public suspend fun decrypt(file: ByteArray): SshOutcome<ByteArray>

    public companion object {
        public val Key: ServiceKey<SshKeyring> = ServiceKey("ssh-keyring")
    }
}

/**
 * The keyring as the host's own Settings screen sees it (02 §5). Never in the service registry: an
 * app that could reach this could delete the person's keys. The host builds it and hands it to its
 * screens directly.
 *
 * Every method that takes secret input — a file, a passphrase — zeroes it before it returns,
 * whatever it returns (D155).
 */
public interface SshKeyAdmin {

    public val keys: StateFlow<List<SshKeyInfo>>

    public val device: KeyringDevice

    /** What [file] is, before any passphrase: type, fingerprint, encrypted or not, where it would live — or why not. Does not zero [file]. */
    public fun inspect(file: ByteArray): Inspection

    /**
     * Imports [file] under [passphrase]. A file with no passphrase or a short one is not stored
     * (D167): the answer is [ImportOutcome.NeedsNewPassphrase] until [newPassphrase] of at least
     * [MIN_PASSPHRASE] characters is given, and then the stored file is the one re-encrypted under it.
     */
    public suspend fun import(
        file: ByteArray,
        passphrase: CharArray?,
        options: ImportOptions,
        newPassphrase: CharArray? = null,
    ): ImportOutcome

    /** "Create a key" (01 §4.4). [passphrase] is required where the key lands on level 3. */
    public suspend fun generate(kind: GeneratedKind, options: ImportOptions, passphrase: CharArray? = null): ImportOutcome

    /** `ssh-keygen -p` for a level-3 key: asks for the old passphrase itself, stores the file under [newPassphrase]. */
    public suspend fun rekey(id: KeyId, newPassphrase: CharArray): SshOutcome<Unit>

    public suspend fun rename(id: KeyId, comment: String)

    /** Asks the person first (D148). Removes the record, its grants, its journal and every chip alias. */
    public suspend fun delete(id: KeyId): SshOutcome<Unit>

    public fun grants(id: KeyId): StateFlow<List<Grant>>

    public suspend fun revoke(grant: Grant)

    public fun journal(id: KeyId): StateFlow<List<UseRecord>>

    /** `ssh-add -x` without a passphrase: every open session and every cached secret, forgotten now. */
    public fun lockAll()
}

/**
 * ssh-agent, one method per message (draft-ietf-sshm-ssh-agent, 02 §2). Not in the service
 * registry: raw signing is a login, and in v1 nobody gets it (question 76). It exists so the real
 * `ssh-add` can talk to the keyring in a test, and for a future client.
 *
 * "Confirm" is not a constraint here, it is the only mode: every signature goes through the sheet.
 */
public interface SshAgent {
    public suspend fun identities(): List<AgentIdentity>

    /** [flags]: 2 = `rsa-sha2-256`, 4 = `rsa-sha2-512`. RSA with neither (SHA-1) is refused. */
    public suspend fun sign(publicBlob: ByteArray, data: ByteArray, flags: Int): SshOutcome<ByteArray>

    /** `ssh-add key`. [file] and [passphrase] are zeroed before this returns, whatever it returns. */
    public suspend fun add(file: ByteArray, passphrase: CharArray?, constraints: Set<AgentConstraint>): ImportOutcome

    public suspend fun remove(publicBlob: ByteArray): SshOutcome<Unit>

    public suspend fun removeAll(): SshOutcome<Unit>

    /** `ssh-add -x`. Only a hash of [passphrase] is kept, only in memory. */
    public suspend fun lock(passphrase: CharArray): Boolean

    public suspend fun unlock(passphrase: CharArray): Boolean

    /** `query`, `session-bind@openssh.com`; anything else is a failure. */
    public suspend fun extension(name: String, body: ByteArray): SshOutcome<ByteArray>
}
