package cx.m42.superizer.ui.platform

import android.app.Activity
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

@Composable
public actual fun RecentsPreview(hidden: Boolean) {
    // Android 13 added the switch; before it only FLAG_SECURE hides the thumbnail, and that also
    // refuses screenshots, which is the app's call through `secureWindow`, not this one.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity, hidden) {
        if (hidden) RecentsSwitch.acquire(activity)
        onDispose { if (hidden) RecentsSwitch.release(activity) }
    }
}

/** Counted, as [SecureWindow] is: two containers are composed at once during a screen change. */
private object RecentsSwitch {
    private val holders = mutableMapOf<Activity, Int>()

    fun acquire(activity: Activity) {
        val count = holders[activity] ?: 0
        if (count == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.setRecentsScreenshotEnabled(false)
        }
        holders[activity] = count + 1
    }

    fun release(activity: Activity) {
        val count = (holders[activity] ?: return) - 1
        if (count <= 0) {
            holders.remove(activity)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) activity.setRecentsScreenshotEnabled(true)
        } else {
            holders[activity] = count
        }
    }
}
