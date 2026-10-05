package cx.m42.superizer.ui.platform

import androidx.compose.runtime.Composable

/**
 * The app-switcher snapshot is the one thing iOS lets an app keep out, and [SecureWindow] already
 * does exactly that — plus screen recording, which costs a secret app nothing it needs.
 */
@Composable
public actual fun RecentsPreview(hidden: Boolean) {
    SecureWindow(active = hidden)
}
