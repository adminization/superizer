package cx.m42.superizer.ui.platform

import androidx.compose.runtime.Composable

/**
 * Keeps the screen out of the recents thumbnail while [hidden] is true and this is composed —
 * without refusing screenshots, which only [SecureWindow] does (Unitool idea/09, D253).
 *
 * Android 13 and later: `Activity.setRecentsScreenshotEnabled(false)`. Older Android has no such
 * switch, and only an app that also asks for [SecureWindow] gets the thumbnail hidden. iOS: the
 * same privacy cover [SecureWindow] puts up as the app resigns active. Desktop and the web have no
 * thumbnail to keep.
 */
@Composable
public expect fun RecentsPreview(hidden: Boolean)
