package cx.m42.superizer.ui.platform

import androidx.compose.runtime.Composable

/** A desktop window has no back gesture; the top bar's own arrow is the whole affordance there. */
@Composable
internal actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit): Unit = Unit
