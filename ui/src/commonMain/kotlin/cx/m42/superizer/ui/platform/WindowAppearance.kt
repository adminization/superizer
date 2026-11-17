package cx.m42.superizer.ui.platform

import androidx.compose.runtime.Composable
import cx.m42.superizer.ThemeMode

/**
 * The window's own idea of light and dark, kept to the host's [mode]: on iOS the window's
 * `overrideUserInterfaceStyle` (the status bar, the keyboard, system sheets follow it), on Android the
 * colour of the bars' glyphs. Nothing on the desktop and the web, which have no bars of their own.
 */
@Composable
internal expect fun WindowAppearance(mode: ThemeMode)
