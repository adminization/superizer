package cx.m42.superizer.host.platform

import cx.m42.superizer.runtime.HostLifecycle
import cx.m42.superizer.runtime.Platform
import java.awt.Desktop
import java.net.URI
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

public actual fun currentPlatform(): Platform = Platform.Desktop

/** Desktop machines have no vibration motor, so an action is signalled visually only. */
internal actual fun platformHapticTick(): Unit = Unit

/** The JVM's default locale, which it takes from the OS (or from `-Duser.language`). */
public actual fun deviceLanguageTag(): String = Locale.getDefault().toLanguageTag()

/**
 * A desktop window is always "foreground" as far as an app is concerned: it is never killed for
 * being out of sight, which is the only thing the background state exists to defend against.
 * A host that wants window focus to count can push into [set].
 */
public actual object PlatformLifecycle {
    private val _state = MutableStateFlow(HostLifecycle.Foreground)
    public actual val state: StateFlow<HostLifecycle> = _state.asStateFlow()

    public fun set(value: HostLifecycle) {
        _state.value = value
    }
}

public actual fun openExternalUrl(url: String): Boolean = runCatching {
    if (!Desktop.isDesktopSupported()) return false
    Desktop.getDesktop().browse(URI(url))
    true
}.getOrDefault(false)

/** Nothing reliable to ask, so the honest answer is "assume yes and let the request fail" (D32). */
public actual object Connectivity {
    public actual val online: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
}
