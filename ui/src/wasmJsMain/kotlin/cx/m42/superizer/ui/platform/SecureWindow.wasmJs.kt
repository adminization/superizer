package cx.m42.superizer.ui.platform

import androidx.compose.runtime.Composable

/** A browser tab cannot refuse a screenshot; there is nothing to set. */
@Composable
public actual fun SecureWindow(active: Boolean): Unit = Unit
