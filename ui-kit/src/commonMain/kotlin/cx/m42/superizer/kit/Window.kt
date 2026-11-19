package cx.m42.superizer.kit

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How wide the window is, in three steps a layout chooses by — never the platform: a desktop window
 * can be dragged to a phone's width and a tablet can be in split view.
 *
 * [Compact] is a phone, [Medium] a tablet in portrait or a small window, [Expanded] a tablet in
 * landscape or a desktop window. The boundaries are [WindowClass.MediumFrom] and
 * [WindowClass.ExpandedFrom].
 */
public enum class WindowClass {
    Compact,
    Medium,
    Expanded,
    ;

    public companion object {
        /** Below this a window is [Compact]. */
        public val MediumFrom: Dp = 600.dp

        /**
         * From this on a window is [Expanded]. 1000 dp rather than Material's 840: a 264 dp sidebar
         * leaves 576 dp of content at 840, which is a phone's layout with a sidebar glued on.
         */
        public val ExpandedFrom: Dp = 1000.dp

        /** The class of a window [width] wide. */
        public fun of(width: Dp): WindowClass = when {
            width < MediumFrom -> Compact
            width < ExpandedFrom -> Medium
            else -> Expanded
        }
    }
}

/**
 * The class of the window the composition is in. [Compact] outside any [WindowClassProvider], so a
 * screen under a test without one is laid out as on a phone.
 */
public val LocalWindowClass: ProvidableCompositionLocal<WindowClass> = staticCompositionLocalOf { WindowClass.Compact }

/**
 * Measures the space it is given and provides its [WindowClass] to [content]. A host calls it once,
 * around the area an app is drawn in; a screen reads [LocalWindowClass] and never measures widths of
 * its own.
 *
 * Measured after layout rather than with `BoxWithConstraints`: a subcomposition around a whole app
 * recomposes it with every measure, and that is not free (a shell's idle clock never ran out under
 * one). The first frame takes the window's width as its guess.
 */
@Composable
public fun WindowClassProvider(modifier: Modifier = Modifier.fillMaxSize(), content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val window = LocalWindowInfo.current
    var width by remember { mutableStateOf(with(density) { window.containerSize.width.toDp() }) }
    Box(modifier.onSizeChanged { width = with(density) { it.width.toDp() } }) {
        CompositionLocalProvider(LocalWindowClass provides WindowClass.of(width), content = content)
    }
}

/** One value per window class, picked for the window the composition is in: `byWindow(1, 2, 3)` columns. */
@Composable
@ReadOnlyComposable
public fun <T> byWindow(compact: T, medium: T, expanded: T): T = when (LocalWindowClass.current) {
    WindowClass.Compact -> compact
    WindowClass.Medium -> medium
    WindowClass.Expanded -> expanded
}
