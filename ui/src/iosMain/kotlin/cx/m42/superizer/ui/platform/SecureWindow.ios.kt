package cx.m42.superizer.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIColor
import platform.UIKit.UIScreen
import platform.UIKit.UIScreenCapturedDidChangeNotification
import platform.UIKit.UIView
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIWindow
// A category on UIColor, which Kotlin/Native exposes as an extension that has to be imported.
import platform.UIKit.systemBackgroundColor

/**
 * What iOS allows of `FLAG_SECURE`, and no more (06 §5.6).
 *
 * Covered: the app-switcher snapshot — a plain view goes over the window as the app resigns active,
 * before the system takes its picture — and screen recording, mirroring and AirPlay, during which
 * `UIScreen.isCaptured` holds and the same view stays up.
 *
 * Not covered, because iOS gives an app no way to: a screenshot. The system tells the app one was
 * taken only after it was.
 */
@Composable
public actual fun SecureWindow(active: Boolean) {
    DisposableEffect(active) {
        if (active) PrivacyCover.acquire()
        onDispose { if (active) PrivacyCover.release() }
    }
}

/** Counted, for the same reason as on Android: two containers overlap during a screen change. */
private object PrivacyCover {
    private var holders = 0
    private var cover: UIView? = null

    fun acquire() {
        holders += 1
        if (captured()) show()
    }

    fun release() {
        holders -= 1
        if (holders <= 0) {
            holders = 0
            hide()
        }
    }

    private fun captured(): Boolean = UIScreen.mainScreen.captured

    @OptIn(ExperimentalForeignApi::class)
    private fun show() {
        if (cover != null) return
        @Suppress("DEPRECATION")
        val window: UIWindow = UIApplication.sharedApplication.keyWindow ?: return
        cover = UIView(frame = window.bounds).apply {
            backgroundColor = UIColor.systemBackgroundColor
            autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
            window.addSubview(this)
        }
    }

    private fun hide() {
        cover?.removeFromSuperview()
        cover = null
    }

    init {
        val center = NSNotificationCenter.defaultCenter
        val main = NSOperationQueue.mainQueue
        // The Face ID sheet resigns active too, so the screen goes blank under it for a moment —
        // which is what the bank apps this imitates do as well.
        center.addObserverForName(UIApplicationWillResignActiveNotification, null, main) {
            if (holders > 0) show()
        }
        center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, main) {
            if (!(holders > 0 && captured())) hide()
        }
        center.addObserverForName(UIScreenCapturedDidChangeNotification, null, main) {
            if (holders > 0 && captured()) show() else hide()
        }
    }
}
