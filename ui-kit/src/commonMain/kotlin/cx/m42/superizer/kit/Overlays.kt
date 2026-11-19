package cx.m42.superizer.kit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronsUpDown
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import kotlinx.coroutines.delay

private object WholeWindow : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) = IntOffset.Zero
}

/** The shadow of anything that floats: black at low alpha, the same in both themes' terms. */
private val FloatShadow = Color.Black.copy(alpha = 0.12f)

/** A press anywhere on it is consumed, so a tap on a card does not fall through to the scrim under it. */
private fun Modifier.swallowTaps(): Modifier = pointerInput(Unit) { detectTapGestures { } }

/**
 * A dimmed layer over the whole window, above everything the app drew. A tap on the dim, Esc or the
 * system back closes it through [onDismiss]. [content] is given how far it has come in (0 → 1), to
 * animate itself with; the dim fades in on the same value.
 *
 * Built on `Popup` rather than a platform window, so a dialog looks and behaves the same on the four
 * platforms and stays inside the window a desktop app runs in.
 */
@Composable
public fun Modal(onDismiss: () -> Unit, content: @Composable BoxScope.(entered: Float) -> Unit) {
    Popup(popupPositionProvider = WholeWindow, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        val enter = remember { Animatable(0f) }
        LaunchedEffect(Unit) { enter.animateTo(1f, tween(200)) }
        val scrim = Kit.colors.scrim
        Box(
            Modifier
                .fillMaxSize()
                .background(scrim.copy(alpha = scrim.alpha * enter.value))
                .onKeyEvent {
                    if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
                        onDismiss()
                        true
                    } else {
                        false
                    }
                }
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        ) { content(enter.value) }
    }
}

/**
 * A question or a short form over the page: a centred card in a wide window, a sheet from the bottom
 * on a phone — the screen does not choose, the window does. [footer] is usually [DialogActions]; the
 * [content] scrolls when it is taller than the window allows.
 *
 * On a phone there is no close cross: the sheet is closed by the dim, by back, or by the footer's
 * cancel, which is where a thumb already is.
 */
@Composable
public fun Dialog(
    onDismiss: () -> Unit,
    title: String,
    description: String? = null,
    icon: ImageVector? = null,
    tone: Tone = Tone.Brand,
    width: Dp = 460.dp,
    closeLabel: String? = null,
    footer: (@Composable () -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val phone = LocalWindowClass.current == WindowClass.Compact
    Modal(onDismiss) { t ->
        val shape = if (phone) RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp) else RoundedCornerShape(16.dp)
        Column(
            Modifier
                .align(if (phone) Alignment.BottomCenter else Alignment.Center)
                .then(if (phone) Modifier.fillMaxWidth() else Modifier.padding(24.dp).widthIn(max = width).fillMaxWidth())
                .graphicsLayer {
                    if (phone) {
                        translationY = (1f - t) * 80.dp.toPx()
                    } else {
                        scaleX = 0.96f + 0.04f * t
                        scaleY = 0.96f + 0.04f * t
                    }
                    alpha = t
                }
                .shadow(24.dp, shape, ambientColor = FloatShadow, spotColor = FloatShadow)
                .clip(shape)
                .background(Kit.colors.popover)
                .border(1.dp, Kit.colors.border, shape)
                .swallowTaps()
                .padding(if (phone) 20.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CompositionLocalProvider(LocalContentColor provides Kit.colors.onPopover) {
                if (phone) Box(Modifier.align(Alignment.CenterHorizontally).size(36.dp, 4.dp).clip(Kit.shapes.full).background(Kit.colors.input))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (icon != null) ToneTile(icon, tone)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, variant = TextVariant.H4)
                        if (description != null) Text(description, variant = TextVariant.Muted)
                    }
                    if (!phone && closeLabel != null) {
                        Box(Modifier.kitClickable(RoundedCornerShape(6.dp), onClick = onDismiss).semantics { contentDescription = closeLabel }.padding(4.dp)) {
                            Icon(Lucide.X, contentDescription = null, size = 16.dp, tint = Kit.colors.mutedForeground)
                        }
                    }
                }
                if (content != null) {
                    Column(
                        Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        content = content,
                    )
                }
                footer?.invoke()
            }
        }
    }
}

/** The icon of a dialog on its tone's soft fill. */
@Composable
private fun ToneTile(icon: ImageVector, tone: Tone) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(tone.softColor()), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, size = 20.dp, tint = if (tone == Tone.Neutral || tone == Tone.Muted) Kit.colors.mutedForeground else tone.textColor())
    }
}

/**
 * The buttons of a [Dialog]: the answer and the way out. Stacked full-width on a phone, the answer on
 * top where the thumb is; side by side at the right in a wide window, the answer last as a desktop
 * reads it. [destructive] fills the answer red — only for what cannot be undone.
 */
@Composable
public fun DialogActions(
    confirm: String,
    onConfirm: () -> Unit,
    cancel: String,
    onCancel: () -> Unit,
    destructive: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val variant = if (destructive) ButtonVariant.Destructive else ButtonVariant.Default
    if (LocalWindowClass.current == WindowClass.Compact) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(confirm, onConfirm, Modifier.fillMaxWidth(), variant = variant, size = ButtonSize.Lg, enabled = enabled, loading = loading)
            Button(cancel, onCancel, Modifier.fillMaxWidth(), variant = ButtonVariant.Ghost, size = ButtonSize.Lg)
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            Button(cancel, onCancel, variant = ButtonVariant.Outline)
            Button(confirm, onConfirm, variant = variant, enabled = enabled, loading = loading)
        }
    }
}

/**
 * Asks before something that cannot be undone. The [title] names the object («Удалить «Пицца
 * Север»?»), the [text] says what will be lost. With [typeToConfirm] the person types that name before
 * the answer unlocks — for what loses a whole server, not a row; [typeToConfirmLabel] is the caption of
 * that field.
 */
@Composable
public fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    cancel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    icon: ImageVector? = null,
    typeToConfirm: String? = null,
    typeToConfirmLabel: String? = null,
) {
    var typed by remember { mutableStateOf("") }
    Dialog(
        onDismiss = onDismiss,
        title = title,
        description = text,
        icon = icon,
        tone = if (destructive) Tone.Danger else Tone.Brand,
        footer = {
            DialogActions(
                confirm = confirm,
                onConfirm = onConfirm,
                cancel = cancel,
                onCancel = onDismiss,
                destructive = destructive,
                enabled = typeToConfirm == null || typed.trim() == typeToConfirm,
            )
        },
        content = if (typeToConfirm == null) {
            null
        } else {
            { Field(typeToConfirmLabel ?: typeToConfirm) { Input(typed, { typed = it }, placeholder = typeToConfirm) } }
        },
    )
}

/**
 * A panel for a summary or a longer form beside the page: from the right edge, 440 dp, in a wide
 * window — the page stays visible to the left; from the bottom on a phone. [closeLabel] is what a
 * screen reader says for its cross.
 */
@Composable
public fun SideSheet(
    onDismiss: () -> Unit,
    title: String,
    closeLabel: String,
    description: String? = null,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val phone = LocalWindowClass.current == WindowClass.Compact
    Modal(onDismiss) { t ->
        val shape = if (phone) RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp) else RoundedCornerShape(0.dp)
        Column(
            Modifier
                .align(if (phone) Alignment.BottomCenter else Alignment.CenterEnd)
                .then(if (phone) Modifier.fillMaxWidth().heightIn(max = 640.dp) else Modifier.width(440.dp).fillMaxHeight())
                .graphicsLayer {
                    if (phone) translationY = (1f - t) * 120.dp.toPx() else translationX = (1f - t) * 120.dp.toPx()
                    alpha = t
                }
                .clip(shape)
                .background(Kit.colors.popover)
                .swallowTaps(),
        ) {
            CompositionLocalProvider(LocalContentColor provides Kit.colors.onPopover) {
                Row(Modifier.padding(start = 24.dp, end = 16.dp, top = 20.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title, variant = TextVariant.H4)
                        if (description != null) Text(description, variant = TextVariant.Muted)
                    }
                    Box(Modifier.kitClickable(RoundedCornerShape(8.dp), onClick = onDismiss).semantics { contentDescription = closeLabel }.padding(8.dp)) {
                        Icon(Lucide.X, contentDescription = null, size = 18.dp, tint = Kit.colors.mutedForeground)
                    }
                }
                Column(
                    Modifier.weight(1f, fill = !phone).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = content,
                )
                if (footer != null) Box(Modifier.padding(24.dp)) { footer() } else Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ───────────────────────────── menus ─────────────────────────────

/** Under the anchor, its start or end edge aligned; above it when the window has no room below. */
private class UnderAnchor(val alignEnd: Boolean, val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = (if (alignEnd) anchorBounds.right - popupContentSize.width else anchorBounds.left)
            .coerceIn(8, (windowSize.width - popupContentSize.width - 8).coerceAtLeast(8))
        val below = anchorBounds.bottom + gap
        val y = if (below + popupContentSize.height > windowSize.height - 8) {
            (anchorBounds.top - gap - popupContentSize.height).coerceAtLeast(8)
        } else {
            below
        }
        return IntOffset(x, y)
    }
}

/**
 * A floating list of actions under its trigger — the `⋯` of a row or a page. Put it in the same `Box`
 * as the trigger, which is what it is anchored to:
 * `Box { IconButton(…) { open = true }; Menu(open, { open = false }) { MenuItem(…) } }`.
 * [alignEnd] lines its right edge with the trigger's, which is right for a `⋯` at the end of a row.
 */
@Composable
public fun Menu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    alignEnd: Boolean = true,
    width: Dp = 220.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    val gap = with(LocalDensity.current) { 6.dp.roundToPx() }
    Popup(
        popupPositionProvider = remember(alignEnd, gap) { UnderAnchor(alignEnd, gap) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        val enter = remember { Animatable(0f) }
        LaunchedEffect(Unit) { enter.animateTo(1f, tween(120)) }
        val shape = RoundedCornerShape(12.dp)
        Column(
            Modifier
                .graphicsLayer {
                    alpha = enter.value
                    translationY = (1f - enter.value) * -6.dp.toPx()
                }
                .shadow(16.dp, shape, ambientColor = FloatShadow, spotColor = FloatShadow)
                .width(width)
                .clip(shape)
                .background(Kit.colors.popover)
                .border(1.dp, Kit.colors.border, shape)
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState())
                .padding(5.dp),
        ) { CompositionLocalProvider(LocalContentColor provides Kit.colors.onPopover) { content() } }
    }
}

/**
 * One line of a [Menu]. [destructive] makes the text red without a fill — the fill is for the
 * confirmation that follows. [selected] puts a tick at the end, for a menu that is a choice; [hint] is a
 * second, quieter line.
 */
@Composable
public fun MenuItem(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    destructive: Boolean = false,
    selected: Boolean = false,
    hint: String? = null,
    enabled: Boolean = true,
) {
    val color = if (destructive) Kit.colors.destructive else Kit.colors.foreground
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 36.dp)
            .kitClickable(RoundedCornerShape(8.dp), enabled = enabled, role = if (selected) Role.RadioButton else Role.Button, onClick = onClick)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, size = 16.dp, tint = if (destructive) color else Kit.colors.mutedForeground)
        Column(Modifier.weight(1f)) {
            Text(text, style = Kit.textStyles.label.copy(fontWeight = FontWeight.Normal), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (hint != null) Text(hint, style = Kit.textStyles.caption, color = Kit.colors.mutedForeground)
        }
        if (selected) Icon(Lucide.Check, contentDescription = null, size = 16.dp, tint = Kit.palette.onBrandSoft)
    }
}

/** A hairline between groups of a [Menu]. */
@Composable
public fun MenuSeparator() {
    Box(Modifier.padding(vertical = 5.dp).fillMaxWidth().height(1.dp).background(Kit.colors.border))
}

/** The quiet title over a group of a [Menu]: «Серверы», an account's address. */
@Composable
public fun MenuLabel(text: String) {
    Text(text, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = Kit.textStyles.caption, color = Kit.colors.mutedForeground)
}

// ───────────────────────────── select ─────────────────────────────

/** One choice of a [Select]: the [value] stored, the [label] shown, an optional quieter [hint]. */
public data class SelectOption(val value: String, val label: String, val hint: String? = null)

/**
 * One choice out of a list, in a field: a dropdown under it in a wide window, a sheet titled [title] on
 * a phone, where a dropdown would be a target too small and too close to the edge. The caller does not
 * choose between the two. [small] is the 36 dp field of a toolbar.
 */
@Composable
public fun Select(
    value: String,
    options: List<SelectOption>,
    onSelect: (String) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    small: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    var widthPx by remember { mutableStateOf(0) }
    val shape = Kit.shapes.md
    Box(modifier.onSizeChanged { widthPx = it.width }) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(if (small) 36.dp else 44.dp)
                .clip(shape)
                .background(Kit.colors.background)
                .border(if (open) 2.dp else 1.dp, if (open) Kit.colors.ring else Kit.colors.input, shape)
                .kitClickable(hover = false) { open = true }
                .semantics { contentDescription = title }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (leadingIcon != null) Icon(leadingIcon, contentDescription = null, size = 16.dp, tint = Kit.colors.mutedForeground)
            Text(
                options.firstOrNull { it.value == value }?.label ?: value,
                Modifier.weight(1f),
                style = if (small) Kit.textStyles.label else Kit.typography.p,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(Lucide.ChevronsUpDown, contentDescription = null, size = 14.dp, tint = Kit.colors.mutedForeground)
        }
        val items: @Composable ColumnScope.() -> Unit = {
            options.forEach { option ->
                MenuItem(option.label, { onSelect(option.value); open = false }, selected = option.value == value, hint = option.hint)
            }
        }
        if (LocalWindowClass.current == WindowClass.Compact) {
            if (open) Dialog(onDismiss = { open = false }, title = title, content = items)
        } else {
            val w = with(LocalDensity.current) { widthPx.toDp() }
            Menu(open, { open = false }, alignEnd = false, width = maxOf(w, 180.dp), content = items)
        }
    }
}

// ───────────────────────────── toasts ─────────────────────────────

/** One confirmation in a [ToastState]. [id] keys it; two equal texts are still two toasts. */
public data class Toast(val id: Long, val title: String, val detail: String? = null, val tone: Tone = Tone.Success)

/**
 * The short confirmations of what the person just did — «Сохранено», «Токен отозван». They leave by
 * themselves after [durationMillis]; a message that must stay until its cause is gone is an [Alert],
 * not a toast. A host keeps one, [rememberToastState], and draws it with [ToastHost] over everything.
 */
@Stable
public class ToastState(public val durationMillis: Long = 4_000) {
    private var next = 0L
    private val _toasts = mutableStateListOf<Toast>()

    /** On screen now, oldest first. */
    public val toasts: List<Toast> get() = _toasts

    /** Shows one more; it leaves by itself. */
    public fun show(title: String, detail: String? = null, tone: Tone = Tone.Success) {
        _toasts.add(Toast(next++, title, detail, tone))
    }

    /** Takes [toast] away before its time. */
    public fun dismiss(toast: Toast) {
        _toasts.remove(toast)
    }
}

/** A [ToastState] that lives as long as the composition that remembers it. */
@Composable
public fun rememberToastState(durationMillis: Long = 4_000): ToastState = remember(durationMillis) { ToastState(durationMillis) }

/**
 * Draws [state]'s toasts: at the top on a phone, where the keyboard and the bottom bar are not; at the
 * bottom right in a wide window, out of the content's way. Put it last in the root `Box`, over
 * everything else.
 */
@Composable
public fun BoxScope.ToastHost(state: ToastState, modifier: Modifier = Modifier) {
    val phone = LocalWindowClass.current == WindowClass.Compact
    Column(
        modifier.align(if (phone) Alignment.TopCenter else Alignment.BottomEnd).padding(if (phone) 12.dp else 24.dp).widthIn(max = 380.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.toasts.forEach { toast ->
            key(toast.id) {
                LaunchedEffect(toast.id) {
                    delay(state.durationMillis)
                    state.dismiss(toast)
                }
                val enter = remember { Animatable(0f) }
                LaunchedEffect(Unit) { enter.animateTo(1f, tween(180)) }
                val shape = RoundedCornerShape(12.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = enter.value
                            translationY = (1f - enter.value) * (if (phone) -12 else 12).dp.toPx()
                        }
                        .shadow(16.dp, shape, ambientColor = FloatShadow, spotColor = FloatShadow)
                        .clip(shape)
                        .background(Kit.colors.popover)
                        .border(1.dp, Kit.colors.border, shape)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val icon = when (toast.tone) {
                        Tone.Danger, Tone.Warn -> Lucide.CircleAlert
                        Tone.Success -> Lucide.CircleCheck
                        else -> Lucide.Info
                    }
                    Icon(icon, contentDescription = null, modifier = Modifier.padding(top = 1.dp), size = 18.dp, tint = toast.tone.color())
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(toast.title, style = Kit.textStyles.label, color = Kit.colors.onPopover)
                        if (toast.detail != null) Text(toast.detail, variant = TextVariant.Muted)
                    }
                }
            }
        }
    }
}
