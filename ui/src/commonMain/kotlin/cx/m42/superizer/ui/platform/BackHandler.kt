package cx.m42.superizer.ui.platform

import androidx.compose.runtime.Composable

/**
 * Intercepts the platform's own "go back", where the platform has one.
 *
 * Android does: the gesture from the screen edge, and the button before it. Left unhandled, both
 * finish the activity — so a user inside an app, or with the drawer open, is dropped out of the
 * host rather than taken one step back.
 *
 * Desktop and the browser have no equivalent and do nothing.
 *
 * Handlers nest, and the nesting is the whole navigation policy (D12): the drawer's claim is
 * registered deepest, then the open app's inside `Content()`, then the shell's. The innermost
 * enabled one wins, which is exactly the order a user expects back to unwind.
 *
 * @param enabled false unregisters the handler rather than swallowing the event — a disabled
 *   handler must let back mean what it meant before there was one.
 */
@Composable
public expect fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit)
