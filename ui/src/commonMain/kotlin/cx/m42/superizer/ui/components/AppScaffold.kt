package cx.m42.superizer.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import cx.m42.superizer.theme.AppTheme
import cx.m42.superizer.ui.i18n.hostStrings
import cx.m42.superizer.ui.platform.SystemBackHandler
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** One row of the slide-in menu. [danger] marks a destructive entry. */
public data class MenuEntry(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val danger: Boolean = false,
)

/** How far in from the left edge a drag may start while the drawer is closed. */
private val EdgeSwipeWidth = 24.dp

/**
 * How far the screen travels — and therefore how much menu is uncovered. A fixed width, not a
 * measured one: the menu is not composed while the drawer is closed, so the first frame of an edge
 * swipe needs a travel distance that already exists.
 */
private val DrawerWidth = 280.dp

/**
 * How far the pushed-back screen shrinks, which is what makes it read as a card lifted off the
 * menu. Kept shallow: the inset this leaves grows twice as fast as the number suggests, and
 * anything deeper reads as the screen falling away rather than sliding aside.
 */
private const val PushedScale = 0.94f

private val PushedCorner = 24.dp
private val ShadowElevation = 16.dp

/**
 * How far the pushed-back screen washes out. Fading it *towards white* rather than dimming towards
 * black keeps the card reading as the lit surface it is while draining the contrast out of its
 * text, so the menu — untouched — is the only thing left worth looking at.
 */
private const val PushedFade = 0.4f

/** Fling speed, in px/s, past which the drawer opens or closes regardless of how far it travelled. */
private const val FlingVelocity = 400f

@Composable
private fun rememberDrawerState(onHaptic: () -> Unit): DrawerState {
    val scope = rememberCoroutineScope()
    val widthPx = with(LocalDensity.current) { DrawerWidth.toPx() }
    // Seeded at construction so the width is right on the first frame — a burger tap that lands
    // before any effect has run must still animate the full distance.
    val state = remember(scope) { DrawerState(scope, widthPx, onHaptic) }
    // Only a later *change* is an effect: writing state straight from composition would mutate a
    // snapshot that may yet be discarded.
    SideEffect { state.onWidthChanged(widthPx) }
    return state
}

/**
 * How far the screen has been pushed aside, in pixels, from fully closed (0f) to fully open.
 *
 * This offset is the single source of truth for the whole effect — the card's travel, its scale,
 * its corners and the menu's fade all derive from it. Dragging writes to it directly so the screen
 * tracks the finger 1:1, and the open/close animations write to the same value, so a gesture can
 * interrupt an animation mid-flight without the two fighting over the position.
 */
private class DrawerState(
    private val scope: kotlinx.coroutines.CoroutineScope,
    initialWidth: Float,
    private val onHaptic: () -> Unit,
) {
    /**
     * Plain state, not an `Animatable`: a drag has to write the new position on the very frame the
     * pointer event arrives, and `Animatable.snapTo` is suspending.
     */
    var offsetPx by mutableFloatStateOf(0f)
        private set

    var width by mutableFloatStateOf(initialWidth)
        private set

    /** 0f closed, 1f fully open. Drives the scale, the corners, the fade and who owns a gesture. */
    val progress: Float get() = if (width <= 0f) 0f else (offsetPx / width).coerceIn(0f, 1f)

    private var settleJob: Job? = null

    /**
     * The pixel width can change under the drawer when the window moves to a display of a different
     * density. Rescaling keeps an open drawer open across that change instead of jumping.
     */
    fun onWidthChanged(newWidth: Float) {
        if (newWidth <= 0f || newWidth == width) return
        val previous = width
        if (previous > 0f) offsetPx = offsetPx / previous * newWidth
        width = newWidth
    }

    fun open() = animateTo(width)

    fun close() = animateTo(0f)

    private fun animateTo(target: Float) {
        settleJob?.cancel()
        if (width <= 0f && target != 0f) return
        // Already there — a burger tap on an open drawer. Animating zero distance would still fire
        // a tick for a panel that never moved.
        if (offsetPx == target) return
        val from = offsetPx
        settleJob = scope.launch {
            animate(
                initialValue = from,
                targetValue = target,
                animationSpec = tween(if (target > from) 220 else 180),
            ) { value, _ -> offsetPx = value }
            // Only on a run that reached its target: a gesture grabbing the panel mid-slide cancels
            // this coroutine, and the cancellation skips the tick. That is what makes the haptic
            // mean "the drawer has settled" rather than "an animation stopped existing".
            onHaptic()
        }
    }

    /** Takes over from whatever animation was running, so a mid-slide grab goes to the finger. */
    fun onDragStarted() {
        settleJob?.cancel()
        settleJob = null
    }

    fun drag(delta: Float) {
        if (width <= 0f) return
        offsetPx = (offsetPx + delta).coerceIn(0f, width)
    }

    /** A decisive flick wins outright; otherwise the panel settles to whichever end it is nearer. */
    fun settle(velocity: Float) {
        val target = when {
            velocity > FlingVelocity -> width
            velocity < -FlingVelocity -> 0f
            progress > 0.5f -> width
            else -> 0f
        }
        // A finger that carried the panel the whole way has nothing left to animate, but the drawer
        // did just come to rest at an end — which is exactly what the haptic marks.
        if (offsetPx == target) {
            onHaptic()
            return
        }
        animateTo(target)
    }
}

/**
 * The frame every host screen and every app sits in: a fixed top bar with a burger on the left, and
 * the content below it. Navigation lives in the drawer the burger opens, so no screen — and no app
 * — spends its header on it (§14).
 *
 * The drawer does not slide over the screen; it lies *underneath* it. Opening pushes the whole
 * screen right and scales it down to reveal the menu already sitting there, so the two feel like
 * one stack of cards rather than a panel flying in.
 *
 * @param footer what sits under the menu's divider — the host's own actions and build stamp. A slot
 *   rather than a `BuildInfo` read, because the frame is library code and the stamp is the host's.
 */
@Composable
public fun AppScaffold(
    title: String,
    menu: List<MenuEntry>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    onHaptic: () -> Unit = {},
    footer: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val tokens = AppTheme
    val drawer = rememberDrawerState(onHaptic)

    // An open drawer is the innermost thing back can dismiss, and it is registered here — deeper
    // in the tree than the shell's own handler — so it answers first (D12).
    SystemBackHandler(enabled = drawer.progress > 0f) { drawer.close() }

    Box(
        modifier = modifier
            .fillMaxSize()
            // The backdrop the menu sits on. It shows through the rounded corners of the
            // pushed-back screen, so it is a shade darker than the card itself.
            .background(tokens.menuBackground),
    ) {
        // Drawn first, so it is genuinely behind the screen rather than over it.
        MenuPanel(drawer = drawer, entries = menu, footer = footer)

        ContentSheet(drawer = drawer) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopBar(title = title, subtitle = subtitle, onBack = onBack, onOpenMenu = { drawer.open() })
                // weight, not fillMaxSize: the content takes the height the bar leaves, which keeps
                // a scrolling list from running under it.
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) { content() }
            }
        }

        // Last, so it sits above the card it drives — and outside it, so the card's scale cannot
        // distort the finger deltas.
        DrawerGestureSurface(drawer = drawer)
    }
}

@Composable
private fun ContentSheet(drawer: DrawerState, content: @Composable () -> Unit) {
    // Read in composition and captured: the veil below is a draw-phase lambda, and a
    // CompositionLocal cannot be read from one.
    val background = AppTheme.background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = drawer.progress
                translationX = drawer.offsetPx
                // Shrink towards the left edge so the card pivots around the point the finger pulls
                // from; scaling about the centre would make it drift away from the touch.
                val scale = 1f - (1f - PushedScale) * p
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0.5f)
                // Corners round off only as the card lifts: a full-screen sheet with rounded
                // corners would show slivers of backdrop at rest.
                shape = RoundedCornerShape(PushedCorner * p)
                clip = true
                shadowElevation = ShadowElevation.toPx() * p
            }
            .background(background)
            .drawWithContent {
                drawContent()
                val fade = PushedFade * drawer.progress
                if (fade > 0f) drawRect(color = background, alpha = fade)
            },
    ) {
        // Padded here, not on the frame: the card is what the user reads, and it is the thing that
        // has to clear the status bar and the home indicator.
        Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            content()
        }
    }
}

/**
 * The invisible surface that owns every drawer gesture: a strip down the left edge while closed,
 * the whole screen once open.
 *
 * Deliberately a sibling of the card rather than a child. `draggable` reports deltas in its own
 * layer's coordinate space, so a grab area inside the scaled card would report movement divided by
 * that scale — at 0.94 the drawer would run ahead of the finger instead of tracking it.
 */
@Composable
private fun BoxScope.DrawerGestureSurface(drawer: DrawerState) {
    val open = drawer.progress > 0f
    Box(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .fillMaxHeight()
            // Tracks the card, so the strip of menu uncovered to its left keeps its own taps.
            .offset { IntOffset(drawer.offsetPx.roundToInt(), 0) }
            .then(if (open) Modifier.fillMaxWidth(PushedScale) else Modifier.width(EdgeSwipeWidth))
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta -> drawer.drag(delta) },
                onDragStarted = { drawer.onDragStarted() },
                onDragStopped = { velocity -> drawer.settle(velocity) },
            )
            // Once open this also swallows taps meant for the pushed-aside screen and turns them
            // into a dismissal — the card doubles as its own scrim.
            .then(
                if (open) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { drawer.close() },
                    )
                } else {
                    Modifier
                },
            ),
    )
}

@Composable
private fun TopBar(title: String, subtitle: String?, onBack: (() -> Unit)?, onOpenMenu: () -> Unit) {
    val tokens = AppTheme
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(tokens.background)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onOpenMenu, label = hostStrings.openMenu) { tint -> BurgerIcon(tint) }

            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(
                    text = title,
                    style = tokens.subtitle,
                    color = tokens.foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = tokens.label,
                        color = tokens.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Back is the screen's own affordance, not the menu's, so it stays on the bar — but
            // only where there is somewhere to go back to.
            if (onBack != null) {
                IconButton(onClick = onBack, label = hostStrings.backAction) { tint -> BackIcon(tint) }
            }
        }
        Divider()
    }
}

/**
 * The menu, lying still underneath the screen. Unlike a conventional drawer it never moves: the
 * screen is what slides off it, so the entries stay put while the card uncovers them.
 *
 * A fully closed menu is not composed at all — its rows would otherwise be read out by a screen
 * reader and matched by `onNodeWithText` while nobody can see them.
 */
@Composable
private fun BoxScope.MenuPanel(
    drawer: DrawerState,
    entries: List<MenuEntry>,
    footer: @Composable () -> Unit,
) {
    if (drawer.progress <= 0f) return
    val tokens = AppTheme

    Column(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .fillMaxHeight()
            // Only ever as wide as the screen travels, so no entry can hide under the card.
            .width(DrawerWidth)
            // Fades the last few pixels into place, so the entries do not appear fully formed the
            // instant the card starts to move.
            .graphicsLayer { alpha = drawer.progress }
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                // Its own scroll: a menu that grows past a short window must not clip its last row.
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            // No close button: the pushed-aside screen is always on show and always dismisses on
            // tap, so a second way out would only crowd the header.
            Text(
                text = hostStrings.menu,
                style = tokens.title,
                color = tokens.foreground,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            Spacer(Modifier.height(8.dp))

            entries.forEach { entry -> MenuRow(entry = entry, onDismiss = { drawer.close() }) }
        }

        // Pinned to the bottom of the panel rather than to the end of the scrolling list: a footer
        // that scrolls away is one you have to hunt for.
        Divider()
        Box(modifier = Modifier.fillMaxWidth()) {
            DismissingFooter(onDismiss = { drawer.close() }, footer = footer)
        }
    }
}

/**
 * Closes the drawer before whatever the footer does, exactly as the rows above do: a drawer left
 * standing over the screen it just opened has to be dismissed by hand.
 */
@Composable
private fun DismissingFooter(onDismiss: () -> Unit, footer: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalDrawerDismiss provides onDismiss) {
        footer()
    }
}

/** How a footer action tells the drawer to get out of the way. Provided by the scaffold. */
public val LocalDrawerDismiss: androidx.compose.runtime.ProvidableCompositionLocal<() -> Unit> =
    androidx.compose.runtime.staticCompositionLocalOf { {} }

@Composable
private fun MenuRow(entry: MenuEntry, onDismiss: () -> Unit) {
    val tokens = AppTheme
    val color = when {
        !entry.enabled -> tokens.disabled
        entry.danger -> tokens.danger
        else -> tokens.foreground
    }
    Text(
        text = entry.label,
        style = tokens.body,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(tokens.radius))
            .clickable(enabled = entry.enabled, role = Role.Button) {
                // Closing before acting: every entry navigates, and a drawer still standing over
                // the new screen would have to be dismissed by hand.
                onDismiss()
                entry.onClick()
            }
            .padding(horizontal = 12.dp, vertical = 14.dp),
    )
}
