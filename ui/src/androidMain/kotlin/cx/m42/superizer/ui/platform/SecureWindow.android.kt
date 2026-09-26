package cx.m42.superizer.ui.platform

import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

@Composable
public actual fun SecureWindow(active: Boolean) {
    val window = LocalActivity.current?.window ?: return
    DisposableEffect(window, active) {
        if (active) SecureFlag.acquire(window)
        onDispose { if (active) SecureFlag.release(window) }
    }
}

/** One count per window. The host has one activity, so in practice one count. */
private object SecureFlag {
    private val holders = mutableMapOf<Window, Int>()

    fun acquire(window: Window) {
        val count = holders[window] ?: 0
        if (count == 0) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        holders[window] = count + 1
    }

    fun release(window: Window) {
        val count = (holders[window] ?: return) - 1
        if (count <= 0) {
            holders.remove(window)
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            holders[window] = count
        }
    }
}
