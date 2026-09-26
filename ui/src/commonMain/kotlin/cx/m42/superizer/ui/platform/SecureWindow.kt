package cx.m42.superizer.ui.platform

import androidx.compose.runtime.Composable

/**
 * Keeps the window out of screenshots, screen recordings, casting and the recents thumbnail while
 * [active] is true and this is composed (06 §5.6, D132). Android's `FLAG_SECURE`; nothing elsewhere,
 * because neither a desktop window nor a browser tab can refuse to be captured.
 *
 * Counted rather than toggled: during a screen change two containers are composed at once, and the
 * one leaving must not clear the flag the one arriving has just set.
 */
@Composable
public expect fun SecureWindow(active: Boolean)
