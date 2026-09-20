package cx.m42.superizer.host.platform

import cx.m42.superizer.runtime.HostLifecycle
import cx.m42.superizer.runtime.Platform
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
