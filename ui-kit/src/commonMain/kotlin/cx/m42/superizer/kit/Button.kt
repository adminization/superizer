package cx.m42.superizer.kit

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** What a button means. [Destructive] is filled red: it is for the action that cannot be undone. */
public enum class ButtonVariant { Default, Secondary, Outline, Ghost, Destructive, Link }

/** [Lg] is 48 dp, a page's main action and the platform's touch minimum; [Sm] fits inside a row. */
public enum class ButtonSize(internal val height: Dp, internal val horizontal: Dp) {
    Sm(32.dp, 12.dp),
    Default(40.dp, 16.dp),
    Lg(48.dp, 24.dp),
}

internal class ButtonLook(val fill: Color, val content: Color, val border: Color?)

@Composable
internal fun ButtonVariant.look(): ButtonLook {
    val c = Kit.colors
    return when (this) {
        ButtonVariant.Default -> ButtonLook(c.primary, c.onPrimary, null)
        ButtonVariant.Secondary -> ButtonLook(c.secondary, c.onSecondary, null)
        ButtonVariant.Outline -> ButtonLook(c.background, c.foreground, c.input)
        ButtonVariant.Ghost -> ButtonLook(Color.Transparent, c.foreground, null)
        ButtonVariant.Destructive -> ButtonLook(c.destructive, c.onDestructive, null)
        ButtonVariant.Link -> ButtonLook(Color.Transparent, c.primary, null)
    }
}

/**
 * A button with a free content row: text, an icon, a counter. Inside it [Text] and [Icon] take the
 * variant's colour without being told.
 *
 * Disabled and loading both ignore taps; loading also shows a [Spinner] in front of the content and
 * keeps the button's width, so the layout does not jump.
 */
@Composable
public fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Default,
    size: ButtonSize = ButtonSize.Default,
    enabled: Boolean = true,
    loading: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val look = variant.look()
    val shape = Kit.shapes.md
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val active = enabled && !loading
    val scale by animateFloatAsState(if (pressed && active) 0.97f else 1f)

    Row(
        modifier = modifier
            .scale(scale)
            .alpha(if (enabled) 1f else 0.5f)
            .defaultMinSize(minHeight = size.height)
            .clip(shape)
            .background(if (pressed && active && variant != ButtonVariant.Link) look.fill.pressed(look.content) else look.fill)
            .then(if (look.border != null) Modifier.border(1.dp, look.border, shape) else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = active,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(PaddingValues(horizontal = if (variant == ButtonVariant.Link) 0.dp else size.horizontal, vertical = 6.dp)),
        horizontalArrangement = Arrangement.spacedBy(Kit.spacing.sm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides look.content) {
            if (loading) Spinner(size = 16.dp)
            content()
        }
    }
}

/** The common button: a label and, optionally, an icon in front of it. */
@Composable
public fun Button(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Default,
    size: ButtonSize = ButtonSize.Default,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    Button(onClick, modifier, variant, size, enabled, loading) {
        if (leadingIcon != null && !loading) Icon(leadingIcon, contentDescription = null, size = 16.dp)
        Text(
            text,
            style = if (size == ButtonSize.Lg) Kit.typography.p.copy(fontWeight = Kit.typography.small.fontWeight) else Kit.typography.small,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A square button holding one icon. Ghost by default: in a top bar or a row it should not compete
 * with the content. [contentDescription] is required, because the icon is all a screen reader gets.
 */
@Composable
public fun IconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Ghost,
    size: ButtonSize = ButtonSize.Default,
    enabled: Boolean = true,
) {
    IconButton(contentDescription, onClick, modifier, variant, size, enabled) {
        Icon(icon, contentDescription = null)
    }
}

/**
 * [IconButton] for a glyph that is not an [ImageVector] — one drawn by hand, an app's own mark. The
 * [content] draws in [LocalContentColor], which here is the variant's colour.
 */
@Composable
public fun IconButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Ghost,
    size: ButtonSize = ButtonSize.Default,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val look = variant.look()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // 44 dp at least, whatever the visual size: the platform's minimum touch target.
    val box = maxOf(size.height, 44.dp)
    Row(
        modifier = modifier
            .size(box)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(Kit.shapes.md)
            .background(if (pressed && enabled) look.fill.pressed(look.content) else look.fill)
            .then(if (look.border != null) Modifier.border(1.dp, look.border, Kit.shapes.md) else Modifier)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides look.content, content = content)
    }
}

/** A fill a touch darker (or, on a dark fill, lighter): the pressed state, without a ripple. */
internal fun Color.pressed(content: Color): Color {
    val base = if (this == Color.Transparent) content.copy(alpha = 0f) else this
    return androidx.compose.ui.graphics.lerp(base, content, 0.08f).copy(alpha = maxOf(base.alpha, 0.08f))
}
