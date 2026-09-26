package cx.m42.superizer.ui.platform

import androidx.compose.runtime.Composable

/**
 * iOS has no system back. The top bar's own arrow is the affordance, as on desktop; the edge swipe
 * belongs to a navigation controller, and the host is one screen with no controller to swipe.
 */
@Composable
public actual fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit): Unit = Unit
