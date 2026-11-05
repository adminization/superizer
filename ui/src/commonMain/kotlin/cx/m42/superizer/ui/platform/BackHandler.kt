package cx.m42.superizer.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Claims "go back" for as long as it is composed and [enabled]: the platform's own, where it has one,
 * and on every platform the host's arrow and swipe while this is inside an open app's `Content()`.
 *
 * Android has a platform back: the gesture from the screen edge, and the button before it. Left
 * unhandled, both finish the activity — so a user inside an app, or with the drawer open, is dropped
 * out of the host rather than taken one step back. Desktop, iOS and the browser have no equivalent,
 * which leaves the arrow as the only way back there; that is why the arrow goes through the same claims.
 *
 * Handlers nest, and the nesting is the whole navigation policy (D12): the drawer's claim is
 * registered deepest, then the open app's inside `Content()`, then the shell's. The innermost
 * enabled one wins, which is exactly the order a user expects back to unwind.
 *
 * @param enabled false unregisters the handler rather than swallowing the event — a disabled
 *   handler must let back mean what it meant before there was one.
 */
@Composable
public fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit) {
    PlatformBackHandler(enabled, onBack)
    val chain = LocalBackChain.current
    if (chain != null) {
        val claim = remember(chain) { BackClaim(enabled, onBack) }
        SideEffect {
            claim.enabled = enabled
            claim.onBack = onBack
        }
        DisposableEffect(chain, claim) {
            chain.add(claim)
            onDispose { chain.remove(claim) }
        }
    }
}

/** What the platform does with [SystemBackHandler], which is nothing where it has no back of its own. */
@Composable
internal expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

/**
 * How many screens of this app stand under the one on top, for the picture a swipe back uncovers.
 * Call it once per level that counts them; levels add up, so a picker inside an editor adds its own.
 * Nothing breaks without it: the swipe then uncovers the frame's backdrop.
 */
@Composable
public fun ScreenSteps(count: Int) {
    val chain = LocalBackChain.current ?: return
    val entry = remember(chain) { StepEntry() }
    // Not a key of the effect below: going from 1 to 2 must not pass through 0 on the way.
    SideEffect { chain.setSteps(entry, count) }
    DisposableEffect(chain, entry) {
        onDispose { chain.removeSteps(entry) }
    }
}

internal class StepEntry

/** The claims of one open app, in the order they were composed: the last enabled one answers. */
internal class BackChain {
    private val claims = mutableStateListOf<BackClaim>()
    private val entries = mutableMapOf<StepEntry, Int>()

    var steps by mutableIntStateOf(0)
        private set

    /** Told in the same frame the steps change, before it is drawn: the last picture is still the old screen. */
    var onSteps: ((old: Int, new: Int) -> Unit)? = null

    fun setSteps(entry: StepEntry, count: Int) {
        entries[entry] = count
        recount()
    }

    fun removeSteps(entry: StepEntry) {
        entries.remove(entry)
        recount()
    }

    private fun recount() {
        val old = steps
        val new = entries.values.sum()
        if (new == old) return
        steps = new
        onSteps?.invoke(old, new)
    }

    /** True while the app has a step of its own to go back to; observable, so the frame can follow it. */
    val active: Boolean get() = claims.any { it.enabled }

    /** @return false when nothing inside the app claims back, and the host decides what back means. */
    fun dispatch(): Boolean {
        val claim = claims.lastOrNull { it.enabled } ?: return false
        claim.onBack()
        return true
    }

    fun add(claim: BackClaim) {
        claims.add(claim)
    }

    fun remove(claim: BackClaim) {
        claims.remove(claim)
    }
}

internal class BackClaim(enabled: Boolean, var onBack: () -> Unit) {
    var enabled by mutableStateOf(enabled)
}

/** Provided around an open app's `Content()` only: the host's own screens and the drawer stay out of it. */
internal val LocalBackChain = staticCompositionLocalOf<BackChain?> { null }
