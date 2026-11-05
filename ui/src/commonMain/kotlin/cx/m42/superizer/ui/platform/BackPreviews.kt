package cx.m42.superizer.ui.platform

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * Pictures of the screens under the one on top, for a swipe back to uncover. Only the top screen is
 * composed, so a picture taken as the app stepped forward is all there is of the one below it.
 *
 * Kept for the last few levels only: each is a whole window of pixels.
 */
internal class BackPreviews(private val chain: BackChain, private val scope: CoroutineScope) {
    private val pictures = mutableStateMapOf<Int, ImageBitmap>()

    /** Where the frame records what it draws; still holds the old screen when the steps change. */
    var layer: GraphicsLayer? = null

    val uncovered: ImageBitmap? get() = pictures[chain.steps - 1]

    init {
        chain.onSteps = { old, new ->
            pictures.keys.filter { it >= new }.forEach(pictures::remove)
            if (new > old) take(new - 1)
        }
    }

    private fun take(step: Int) {
        val source = layer ?: return
        // Undispatched: past this frame's draw the layer holds the new screen.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val picture = runCatching { source.toImageBitmap() }.getOrNull() ?: return@launch
            if (step < chain.steps) pictures[step] = picture
            while (pictures.size > KEPT) pictures.remove(pictures.keys.min())
        }
    }

    private companion object {
        const val KEPT = 4
    }
}

/** Provided around the frame of an app that may be pictured: a secret one is left out, and has no pictures. */
internal val LocalBackPreviews = staticCompositionLocalOf<BackPreviews?> { null }
