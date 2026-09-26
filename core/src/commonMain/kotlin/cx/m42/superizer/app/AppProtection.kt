package cx.m42.superizer.app

import kotlinx.serialization.Serializable

/**
 * Whether an app's screen is its owner's business only (06, D127).
 *
 * The app *declares* this and the host *enforces* it: the lock, the window flag and the silence in
 * the event log are all the host's, because the window, the log and the snapshot are the host's.
 * The app decides only which of its own actions to confirm again, through
 * [cx.m42.superizer.lock.UserPresence].
 *
 * A manifest field rather than a call in `setup()`: the catalog and the Service Menu show it
 * before a line of the app has run, and the registry can check it at registration (D138).
 */
@Serializable
public data class AppProtection(
    val lock: LockPolicy = LockPolicy.Off,
    /** Keep the app's screen out of screenshots, recordings, casting and the recents thumbnail. */
    val secureWindow: Boolean = false,
) {
    /**
     * Anything declared at all. Such an app's config is a secret to the host (D134): it is left out
     * of events and out of the session snapshot, and the query is cut off its links in the log.
     */
    val sensitive: Boolean get() = lock != LockPolicy.Off || secureWindow
}

/**
 * Who decides whether the lock is on (D139): the app, in one of four ways.
 *
 * Four values rather than "a switch plus a default", so that a combination that means nothing —
 * fixed on, but off by default — cannot be written down.
 */
@Serializable
public enum class LockPolicy {
    /** No lock, and no switch for one in Settings. */
    Off,

    /** A switch in Settings, off until the person turns it on. */
    OptionalOff,

    /** A switch in Settings, on until the person turns it off. */
    OptionalOn,

    /** Always on; Settings lists the app as locked and offers no switch. */
    Required,
}
