package cx.m42.superizer.ui.shell

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import cx.m42.superizer.ThemeMode
import cx.m42.superizer.kit.KitAccent
import cx.m42.superizer.kit.KitTheme
import cx.m42.superizer.kit.KitTypography
import cx.m42.superizer.kit.kitTypography
import cx.m42.superizer.theme.DarkTokens
import cx.m42.superizer.theme.LightTokens
import cx.m42.superizer.theme.SuperizerTheme
import cx.m42.superizer.ui.platform.WindowAppearance

/**
 * Both themes — `ui-theme`'s tokens and the kit's colours — light or dark as [mode] says, and the
 * system's own bars (the status bar's glyphs, iOS's sheets and keyboard) told the same.
 *
 * [SuperizerShell] calls it with the person's choice from Settings; a host that draws a screen of its
 * own outside the shell wraps it in the same call, so the two never disagree.
 */
@Composable
public fun HostTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    HostTheme(mode, KitAccent.Graphite, kitTypography(), content)
}

/**
 * [HostTheme] in the person's [accent] and with the host's [typography] — the font reaches inside the
 * apps, which the first form never let it do (the kit's default typography replaced it).
 */
@Composable
public fun HostTheme(mode: ThemeMode, accent: KitAccent, typography: KitTypography, content: @Composable () -> Unit) {
    // Told to the platform before the system is asked: on iOS a forced style is what
    // isSystemInDarkTheme reads next, and System hands the device's answer back.
    WindowAppearance(mode)
    val dark = when (mode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> isSystemInDarkTheme()
    }
    SuperizerTheme(tokens = if (dark) DarkTokens else LightTokens) {
        KitTheme(dark = dark, accent = accent, typography = typography, content = content)
    }
}
