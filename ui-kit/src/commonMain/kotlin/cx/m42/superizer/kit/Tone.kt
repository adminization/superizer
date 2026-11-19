package cx.m42.superizer.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role

/**
 * What a state means, in a closed set: the colour of a status, a dot, a notice comes from its tone and
 * from nowhere else. An app keeps its own dictionary from the server's value to a label and a tone;
 * the kit only knows the tones.
 */
public enum class Tone { Neutral, Running, Success, Warn, Danger, Muted, Brand }

/** The hue itself: a dot, an icon, a chart's mark. */
@Composable
@ReadOnlyComposable
public fun Tone.color(): Color = when (this) {
    Tone.Neutral, Tone.Muted -> Kit.colors.mutedForeground
    Tone.Running -> Kit.colors.info
    Tone.Success -> Kit.colors.success
    Tone.Warn -> Kit.colors.warning
    Tone.Danger -> Kit.colors.destructive
    Tone.Brand -> Kit.palette.brand
}

/** The hue made readable as small text: darker in the light set, lighter in the dark one. */
@Composable
@ReadOnlyComposable
public fun Tone.textColor(): Color = when (this) {
    Tone.Neutral -> Kit.colors.foreground
    Tone.Muted -> Kit.colors.mutedForeground
    Tone.Brand -> Kit.palette.onBrandSoft
    else -> if (Kit.colors.isDark) lerp(color(), Color.White, 0.25f) else lerp(color(), Color.Black, 0.28f)
}

/** A quiet fill in the tone: a badge, a notice, an icon tile. */
@Composable
@ReadOnlyComposable
public fun Tone.softColor(): Color = when (this) {
    Tone.Neutral, Tone.Muted -> Kit.colors.secondary
    Tone.Brand -> Kit.palette.brandSoft
    else -> color().copy(alpha = if (Kit.colors.isDark) 0.16f else 0.11f)
}

/**
 * A click with the kit's feedback instead of a ripple: the accent fill under a mouse or a finger and,
 * where there is a pointer, the hand cursor. [hover] off for a target that has its own fill.
 */
public fun Modifier.kitClickable(
    shape: Shape? = null,
    enabled: Boolean = true,
    hover: Boolean = true,
    role: Role = Role.Button,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val fill = if (enabled && ((hover && hovered) || pressed)) Kit.colors.accent else Color.Transparent
    this
        .then(if (shape != null) Modifier.clip(shape) else Modifier)
        .background(fill)
        .hoverable(interaction, enabled)
        .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
        .clickable(interaction, indication = null, enabled = enabled, role = role, onClick = onClick)
}
