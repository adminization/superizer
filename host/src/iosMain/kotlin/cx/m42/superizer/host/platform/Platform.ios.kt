package cx.m42.superizer.host.platform

import cx.m42.superizer.host.lock.IosDeviceAuthenticator
import cx.m42.superizer.lock.DeviceAuthenticator
import cx.m42.superizer.runtime.HostLifecycle
import cx.m42.superizer.runtime.Logger
import cx.m42.superizer.runtime.Platform
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSLocale
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.preferredLanguages
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIApplicationProtectedDataWillBecomeUnavailable
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.darwin.dispatch_get_main_queue

public actual fun currentPlatform(): Platform = Platform.Ios

/**
 * The Taptic Engine's light impact — the one iOS itself uses to acknowledge a key. A generator per
 * tap, prepared right before it: that is what keeps the latency down, and UIKit ignores the whole
 * thing on a device without the engine or with system haptics switched off.
 */
internal actual fun platformHapticTick() {
    val generator = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
    generator.prepare()
    generator.impactOccurred()
}

/**
 * The head of Settings → General → Language & Region, already intersected by the system with the
 * languages the app declares in `CFBundleLocalizations`. `currentLocale` would answer the *region*
 * format instead — English in Spain reads `es_ES` there.
 */
public actual fun deviceLanguageTag(): String =
    (NSLocale.preferredLanguages.firstOrNull() as? String).orEmpty()

/**
 * `didEnterBackground` / `willEnterForeground`, not `willResignActive` / `didBecomeActive`: the
 * Face ID sheet, Control Centre and an incoming call all resign active without the app leaving,
 * and a snapshot — or a lock (06) — written on each of those would be the Android `ON_PAUSE` mistake
 * again (08).
 */
public actual object PlatformLifecycle {
    private val _state = MutableStateFlow(HostLifecycle.Foreground)
    public actual val state: StateFlow<HostLifecycle> = _state.asStateFlow()

    init {
        val center = NSNotificationCenter.defaultCenter
        center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue) {
            _state.value = HostLifecycle.Background
        }
        center.addObserverForName(UIApplicationWillEnterForegroundNotification, null, NSOperationQueue.mainQueue) {
            _state.value = HostLifecycle.Foreground
        }
    }
}

public actual fun openExternalUrl(url: String): Boolean {
    val target = NSURL.URLWithString(url) ?: return false
    val application = UIApplication.sharedApplication
    if (!application.canOpenURL(target)) return false
    application.openURL(target, options = emptyMap<Any?, Any?>(), completionHandler = null)
    return true
}

/** `NWPathMonitor`, on the main queue: "satisfied" is the Network framework's own word for online. */
public actual object Connectivity {
    private val _online = MutableStateFlow(true)
    public actual val online: StateFlow<Boolean> = _online.asStateFlow()

    private val monitor = nw_path_monitor_create()

    init {
        nw_path_monitor_set_update_handler(monitor) { path ->
            _online.value = nw_path_get_status(path) == nw_path_status_satisfied
        }
        nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
        nw_path_monitor_start(monitor)
    }
}

/**
 * iOS never tells an app the screen went dark. What it does tell, when a passcode is set, is that
 * the device locked: protected data is about to become unreadable. That is the event the lock
 * needs — the phone left its owner's hands — and it comes a few seconds after the side button,
 * which is before anyone else can get past the lock screen.
 */
public actual object PlatformScreen {
    private val _off = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    public actual val off: SharedFlow<Unit> = _off.asSharedFlow()

    init {
        NSNotificationCenter.defaultCenter.addObserverForName(
            UIApplicationProtectedDataWillBecomeUnavailable,
            null,
            NSOperationQueue.mainQueue,
        ) { _off.tryEmit(Unit) }
    }

    public actual fun interactive(): Boolean = UIApplication.sharedApplication.protectedDataAvailable
}

/**
 * The app's own page in Settings. iOS offers no public way into Face ID & Passcode — the private
 * URL that does is a reason for App Review to reject the build — so this is one level down from
 * it, which is as close as an app may go.
 */
internal actual fun openSecuritySettings(): Boolean {
    val settings = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return false
    UIApplication.sharedApplication.openURL(settings, options = emptyMap<Any?, Any?>(), completionHandler = null)
    return true
}

internal actual fun platformDeviceAuthenticator(debug: Boolean, logger: Logger): DeviceAuthenticator =
    IosDeviceAuthenticator(debug, logger)
