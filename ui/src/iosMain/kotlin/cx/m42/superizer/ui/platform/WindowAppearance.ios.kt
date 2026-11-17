package cx.m42.superizer.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import cx.m42.superizer.ThemeMode
import platform.UIKit.UIApplication
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIWindow

/**
 * Every window of the app gets the style, so the status bar, the keyboard and the system's sheets
 * (the document picker, Face ID) match the screen under them. System is `Unspecified`: the window
 * follows the device again, and so does `isSystemInDarkTheme`.
 */
@Composable
internal actual fun WindowAppearance(mode: ThemeMode) {
    DisposableEffect(mode) {
        val style = when (mode) {
            ThemeMode.Light -> UIUserInterfaceStyle.UIUserInterfaceStyleLight
            ThemeMode.Dark -> UIUserInterfaceStyle.UIUserInterfaceStyleDark
            ThemeMode.System -> UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified
        }
        @Suppress("DEPRECATION")
        UIApplication.sharedApplication.windows.forEach { (it as? UIWindow)?.overrideUserInterfaceStyle = style }
        onDispose { }
    }
}
