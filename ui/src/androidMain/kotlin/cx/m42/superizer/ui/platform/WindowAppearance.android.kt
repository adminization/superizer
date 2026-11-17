package cx.m42.superizer.ui.platform

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import cx.m42.superizer.ThemeMode

/**
 * The glyphs of the status and navigation bars: dark over the light theme, light over the dark one.
 * The bars themselves are transparent (edge to edge), so their colour is the screen's.
 */
@Composable
internal actual fun WindowAppearance(mode: ThemeMode) {
    val window = LocalActivity.current?.window ?: return
    val dark = when (mode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> isSystemInDarkTheme()
    }
    SideEffect {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}
