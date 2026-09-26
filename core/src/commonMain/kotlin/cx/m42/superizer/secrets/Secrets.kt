package cx.m42.superizer.secrets

import cx.m42.superizer.app.AppId
import cx.m42.superizer.runtime.ServiceKey
import kotlinx.coroutines.flow.StateFlow

/**
 * Encrypts what the host keeps, under a key nobody holding the files can use.
 *
 * Text in, text out, as every service contract is. On Android the key lives in the Keystore, on iOS
 * in the Secure Enclave; where there is no chip it is the person's SSH key and its passphrase
 * (`AgeVault`, 05 §3). The host seals `runtime.secrets` with it (D171); the implementations are the
 * host's (D181), and moved here unchanged from Unitool's `services/vault` (D182).
 *
 * [Key] stays registered for one release so that an app which sealed with the vault itself can
 * move its data into `runtime.secrets` (04 §9, Б4). After that no app sees a vault.
 */
public interface SecretVault {

    /**
     * [plaintext], encrypted and authenticated, with a fresh nonce every call. Never asks the person.
     * Throws when the vault cannot be used; the caller must then keep nothing rather than keep it
     * in the clear.
     */
    public suspend fun seal(plaintext: String): String

    /**
     * What [seal] was given, or null when this vault cannot open [sealed]: changed by even a bit,
     * sealed elsewhere, the key gone — or, for a vault that asks, the person declined.
     */
    public suspend fun open(sealed: String): String?

    /**
     * False when [open] would have to ask the person first — a passphrase not yet given this session.
     * The host opens nothing at start unless this is true, so starting never puts up a prompt.
     */
    public val opensSilently: Boolean get() = true

    public companion object {
        public val Key: ServiceKey<SecretVault> = ServiceKey("secret-vault")
    }
}

/**
 * A secret is there and cannot be opened now: the person declined the passphrase, or the key that
 * sealed it is gone. Thrown by `runtime.secrets.get` rather than answering null — null means "never
 * stored", and an app that took the one for the other would write an empty value over the real one.
 */
public class SecretsUnavailableException(message: String = "secret cannot be opened now") : Exception(message)

/** How many secrets an app has, and how many of them the host could not seal (05 §3.2). */
public data class SecretsCount(val sealed: Int, val plain: Int)

/**
 * The host's side of `runtime.secrets` (05 §3.2, D171): which vault seals them, and moving them all
 * from one vault to another.
 */
public interface SecretsPort {

    /** The vault in use. Null: no vault on this platform yet, and secrets are stored as plain text (06: `vault.missing`). */
    public val vault: StateFlow<SecretVault?>

    /**
     * Every app's secrets, re-sealed under [next]: each is opened with the current vault — which may
     * ask, once — and sealed with [next], which never asks. All or nothing: when one cannot be
     * opened, nothing is written and the answer is false. Plain secrets are sealed without asking.
     */
    public suspend fun reseal(next: SecretVault): Boolean

    /** Per app with at least one secret. Reads keys and prefixes only; opens nothing. */
    public suspend fun counts(): Map<AppId, SecretsCount>
}
