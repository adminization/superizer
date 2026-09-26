@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package cx.m42.superizer.host.platform

import cx.m42.superizer.lock.DeviceAuthenticator
import cx.m42.superizer.runtime.HostLifecycle
import cx.m42.superizer.runtime.Logger
import cx.m42.superizer.runtime.Platform
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

public actual fun currentPlatform(): Platform = Platform.Web

/**
 * The Vibration API, which in practice means Chrome on Android — desktop browsers and every iOS
 * browser expose no motor, and the call answers false there rather than throwing.
 */
internal actual fun platformHapticTick() {
    runCatching { vibrateMillis(20) }
}

private fun vibrateMillis(millis: Int): Boolean =
    js("(navigator.vibrate && navigator.vibrate(millis)) || false")

/**
 * The browser's own preference (`navigator.language`), which is not necessarily the OS language.
 * Guarded like every other reach into JS: a context without a `navigator` answers "".
 */
public actual fun deviceLanguageTag(): String = runCatching { navigatorLanguage() }.getOrDefault("")

private fun navigatorLanguage(): String = js("(navigator && navigator.language) || ''")

/** A hidden tab is the browser's version of background: timers throttle and the page may be frozen. */
public actual object PlatformLifecycle {
    private val _state = MutableStateFlow(HostLifecycle.Foreground)
    public actual val state: StateFlow<HostLifecycle> = _state.asStateFlow()

    init {
        runCatching {
            document.addEventListener("visibilitychange", {
                _state.value = if (documentHidden()) HostLifecycle.Background else HostLifecycle.Foreground
            })
        }
    }
}

private fun documentHidden(): Boolean = js("document.hidden === true")

public actual fun openExternalUrl(url: String): Boolean = runCatching {
    window.open(url, "_blank")
    true
}.getOrDefault(false)

public actual object Connectivity {
    private val _online = MutableStateFlow(true)
    public actual val online: StateFlow<Boolean> = _online.asStateFlow()

    init {
        runCatching {
            _online.value = navigatorOnLine()
            window.addEventListener("online", { _online.value = true })
            window.addEventListener("offline", { _online.value = false })
        }
    }
}

private fun navigatorOnLine(): Boolean = js("navigator.onLine !== false")

/** No screen to go dark that this process could hear about. */
public actual object PlatformScreen {
    public actual val off: SharedFlow<Unit> = MutableSharedFlow<Unit>().asSharedFlow()

    public actual fun interactive(): Boolean = true
}

internal actual fun openSecuritySettings(): Boolean = false

internal actual fun platformDeviceAuthenticator(debug: Boolean, logger: Logger): DeviceAuthenticator =
    NoDeviceAuthenticator
