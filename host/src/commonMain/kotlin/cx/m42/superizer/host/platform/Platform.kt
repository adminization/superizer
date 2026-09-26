package cx.m42.superizer.host.platform

import cx.m42.superizer.lock.AuthAvailability
import cx.m42.superizer.lock.AuthOutcome
import cx.m42.superizer.lock.AuthStrength
import cx.m42.superizer.lock.DeviceAuthenticator
import cx.m42.superizer.runtime.HostLifecycle
import cx.m42.superizer.runtime.Logger
import cx.m42.superizer.runtime.Platform
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Which of the three this build is running on. Reported in `HostInfo`, shown by the bench app. */
public expect fun currentPlatform(): Platform

/** The platform's own "that happened" primitive; nothing where there is no motor. */
internal expect fun platformHapticTick()

/** The device's language as a BCP-47 tag, or "" for "could not tell" — which is not a language. */
public expect fun deviceLanguageTag(): String

/**
 * Foreground/background of the host process (D17).
 *
 * On Android this must follow `ON_STOP` and not `ON_PAUSE`: a permission dialog puts the activity
 * in `PAUSED`, and a handler wired to that would write a session snapshot on every vibration (08).
 */
public expect object PlatformLifecycle {
    public val state: StateFlow<HostLifecycle>
}

/** Opens a URL in whatever the platform considers its browser. False when it refused. */
public expect fun openExternalUrl(url: String): Boolean

/** Best-effort connectivity (D32). Desktop assumes true: there is nothing reliable to ask. */
public expect object Connectivity {
    public val online: StateFlow<Boolean>
}

/**
 * The screen, as the lock needs it (06 §5.3): the moment it goes dark, and whether it is on.
 * Desktop and the web have neither — [off] never fires there and [interactive] is always true.
 */
public expect object PlatformScreen {
    public val off: SharedFlow<Unit>
    public fun interactive(): Boolean
}

/** Opens the device's screen-lock settings (§5.8). False where there are none to open. */
internal expect fun openSecuritySettings(): Boolean

/**
 * The platform's "is this the owner?": the system's biometric sheet on Android, nothing elsewhere.
 * [debug] turns a host wired wrongly for one — an activity that cannot show the sheet — into a
 * crash rather than a lock that silently is not there.
 */
internal expect fun platformDeviceAuthenticator(debug: Boolean, logger: Logger): DeviceAuthenticator

/** Desktop and the web: nothing to ask with, and honest about it (§5.8). */
internal object NoDeviceAuthenticator : DeviceAuthenticator {
    override fun availability(strength: AuthStrength): AuthAvailability = AuthAvailability.Unsupported

    override suspend fun authenticate(title: String, subtitle: String?, strength: AuthStrength): AuthOutcome =
        AuthOutcome.Error("this platform has no device authentication")
}
