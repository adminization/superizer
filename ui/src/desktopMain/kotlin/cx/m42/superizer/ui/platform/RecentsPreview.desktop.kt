package cx.m42.superizer.ui.platform

import androidx.compose.runtime.Composable

/** A desktop window has no recents thumbnail of its own to keep. */
@Composable
public actual fun RecentsPreview(hidden: Boolean): Unit = Unit
