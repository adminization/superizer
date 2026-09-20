package cx.m42.superizer.ui.platform

import androidx.compose.runtime.Composable

/**
 * Left alone deliberately. The browser's back button belongs to the history stack and this host
 * puts nothing on it; hijacking back without pushing entries first would trap the reader on the
 * page, which is worse than back leaving the app.
 */
@Composable
public actual fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit): Unit = Unit
