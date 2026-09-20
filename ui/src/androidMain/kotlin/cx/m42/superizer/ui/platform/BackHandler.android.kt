package cx.m42.superizer.ui.platform

import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler as ActivityBackHandler

/**
 * Straight onto the activity's `OnBackPressedDispatcher`, which is what the back gesture, the back
 * button and the three-button bar all arrive through — and, from Android 13, what drives the
 * predictive-back animation as well.
 */
@Composable
public actual fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit): Unit =
    ActivityBackHandler(enabled = enabled, onBack = onBack)
