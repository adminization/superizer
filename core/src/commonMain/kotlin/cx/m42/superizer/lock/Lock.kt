package cx.m42.superizer.lock

import cx.m42.superizer.app.AppId
import cx.m42.superizer.runtime.ServiceKey
import kotlinx.coroutines.flow.StateFlow

/*
 * The lock over protected apps (06).
 *
 * Three audiences, three types. The host implements [DeviceAuthenticator] and never lets an app
 * see it. The shell reads [AppLockPort] off `Superizer`, draws the curtain and the Settings section
 * from it. An app sees only [UserPresence], an optional service (D19), and only to confirm one of
 * its own actions.
 */

/** The platform's "is this the device's owner?" (D129). Implemented by the host; an app never sees it. */
public interface DeviceAuthenticator {

    /** What the device can ask with right now. Cheap: the host calls it on every return to the foreground. */
    public fun availability(strength: AuthStrength = AuthStrength.Any): AuthAvailability

    /**
     * Puts the system's own sheet up and waits for the person. [title] and [subtitle] are what the
     * sheet says; they come from the host's string tables or from the app asking for a confirmation.
     */
    public suspend fun authenticate(
        title: String,
        subtitle: String? = null,
        strength: AuthStrength = AuthStrength.Any,
    ): AuthOutcome
}

/**
 * [Any]: whatever the device can do — Class 2 face unlock included, which is the "Face ID" people
 * mean on most Android phones. [Strong]: only what the Keystore accepts for a key bound to
 * authentication (Class 3 biometrics or the screen-lock credential).
 *
 * Stage 1 uses [Any]. [Strong] is here so that binding the vault's key to authentication later (06
 * §8) needs no new contract (D142).
 */
public enum class AuthStrength { Any, Strong }

public enum class AuthAvailability {
    Available,

    /** The device has no PIN, pattern or password, so there is nothing to ask with (§5.8). */
    NoScreenLock,

    /** The platform has no such thing — desktop, the web — or the host was built without one. */
    Unsupported,
}

public sealed interface AuthOutcome {
    public data object Success : AuthOutcome

    /** The person closed the sheet. Nothing to say; the curtain stays. */
    public data object Cancelled : AuthOutcome

    /** Too many tries. [message] is the system's own, already in the device's language. */
    public data class LockedOut(val message: String) : AuthOutcome

    public data class Error(val message: String) : AuthOutcome
}

/**
 * Asks the person to prove they are the owner before one action — Export, Show key, Delete in the
 * 2FA app (D133). An optional service: a host with nothing to ask with registers none, and the
 * app then does what it did before.
 */
public interface UserPresence {

    /**
     * True when confirmed now. [reason] is what the system sheet says the confirmation is for, in
     * the app's words and language.
     *
     * Also true when the device has no screen lock: there is nothing to ask with, and the host is
     * already showing its "not protected" banner over the app (D140).
     */
    public suspend fun confirm(reason: String): Boolean

    public companion object {
        public val Key: ServiceKey<UserPresence> = ServiceKey("user-presence")
    }
}

/**
 * What the shell needs to know about the lock, and the handful of things it may do to it.
 *
 * One unlock for the whole host, held in memory only (D130): after a process death everything is
 * locked, and opening a second protected app a minute after the first asks nothing.
 */
public interface AppLockPort {

    public val state: StateFlow<LockState>

    /**
     * Puts the device's sheet up. On success the host is open until D131 closes it again: the screen
     * turning off, or longer than the grace away in another app.
     */
    public suspend fun unlock(title: String, subtitle: String? = null): AuthOutcome

    /** "Lock now" in Settings. The next look at a protected app asks again. */
    public fun lockNow()

    /** How long another app may be in front before coming back asks again. Remembered on this device. */
    public fun setGrace(millis: Long)

    /**
     * The person's own choice for an `OptionalOff`/`OptionalOn` app, remembered on this device. A
     * `Required` or `Off` app ignores it: there is no switch to have touched.
     */
    public fun setEnabled(id: AppId, enabled: Boolean)

    /** The device's own screen-lock settings, where someone turns a lock on (§5.8). False where there are none. */
    public fun openDeviceSettings(): Boolean

    public companion object {
        /** The choices Settings offers (§5.7): at once, 30 s, a minute, five. */
        public val GraceChoices: List<Long> = listOf(0L, 30_000L, 60_000L, 300_000L)

        /** A minute: long enough to send the export and come back, short enough to matter (question 63). */
        public const val DEFAULT_GRACE: Long = 60_000L
    }
}

/**
 * The lock as the shell draws it.
 *
 * [guarded] is policy and choice together — which apps want the lock — and says nothing about
 * whether the device can provide one. [covers] and [unprotected] are the two ways that meets the
 * device, and they never both hold for one app.
 */
public data class LockState(
    /** The owner has proven themselves and nothing has closed the lock since. */
    val open: Boolean = false,
    val availability: AuthAvailability = AuthAvailability.Unsupported,
    /** Apps whose screen the lock is meant to cover: `Required`, or optional and switched on. */
    val guarded: Set<AppId> = emptySet(),
    val graceMillis: Long = AppLockPort.DEFAULT_GRACE,
    /** The person's own switches, only for the apps they actually touched (D139). */
    val choices: Map<AppId, Boolean> = emptyMap(),
    /** The device's sheet is up. The shell does not put up a second one. */
    val prompting: Boolean = false,
) {
    /** Draw the curtain instead of [id]'s screen. */
    public fun covers(id: AppId): Boolean =
        id in guarded && availability == AuthAvailability.Available && !open

    /** [id] wants a lock this device cannot give it: open it, under the host's banner (D140). */
    public fun unprotected(id: AppId): Boolean =
        id in guarded && availability != AuthAvailability.Available
}
