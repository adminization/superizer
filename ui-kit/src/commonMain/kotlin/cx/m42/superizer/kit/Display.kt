package cx.m42.superizer.kit

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A bordered block with the theme's padding. With [onClick] the whole card is one tap target and
 * gives way a little under the finger.
 */
@Composable
public fun Card(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = Kit.spacing.lg,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(Kit.spacing.sm),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Kit.colors
    val shape = Kit.shapes.xl
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(shape)
            .background(if (pressed) c.card.pressed(c.onCard) else c.card)
            .border(1.dp, c.border, shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
    ) {
        CompositionLocalProvider(LocalContentColor provides c.onCard) { content() }
    }
}

/**
 * A short label of state. [Success], [Warning] and [Info] exist because a status is what a badge is
 * for most of the time, and each app inventing its own colours for them is how one screen ends up
 * with three greens.
 */
public enum class BadgeVariant { Default, Secondary, Outline, Destructive, Success, Warning, Info }

/** A pill with a label and, with [dot], a coloured point in front of it. */
@Composable
public fun Badge(
    text: String,
    modifier: Modifier = Modifier,
    variant: BadgeVariant = BadgeVariant.Default,
    dot: Boolean = false,
) {
    val c = Kit.colors
    val (fill, content, border) = when (variant) {
        BadgeVariant.Default -> Triple(c.primary, c.onPrimary, null)
        BadgeVariant.Secondary -> Triple(c.secondary, c.onSecondary, null)
        BadgeVariant.Outline -> Triple(Color.Transparent, c.foreground, c.border)
        BadgeVariant.Destructive -> Triple(c.destructive, c.onDestructive, null)
        BadgeVariant.Success -> Triple(c.success, c.onSuccess, null)
        BadgeVariant.Warning -> Triple(c.warning, c.onWarning, null)
        BadgeVariant.Info -> Triple(c.info, c.onInfo, null)
    }
    Row(
        modifier
            .clip(Kit.shapes.full)
            .background(fill)
            .then(if (border != null) Modifier.border(1.dp, border, Kit.shapes.full) else Modifier)
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot) Box(Modifier.size(6.dp).clip(Kit.shapes.full).background(content))
        Text(text, style = Kit.typography.small.copy(fontWeight = FontWeight.SemiBold), color = content, maxLines = 1)
    }
}

public enum class SeparatorOrientation { Horizontal, Vertical }

/** A hairline in the border colour. [inset] leaves room at the start, under a row's leading icon. */
@Composable
public fun Separator(
    modifier: Modifier = Modifier,
    orientation: SeparatorOrientation = SeparatorOrientation.Horizontal,
    inset: Dp = 0.dp,
) {
    val color = Kit.colors.border
    when (orientation) {
        SeparatorOrientation.Horizontal ->
            Box(modifier.fillMaxWidth().padding(start = inset).height(1.dp).background(color))
        SeparatorOrientation.Vertical ->
            Box(modifier.fillMaxHeight().padding(top = inset).width(1.dp).background(color))
    }
}

/** [Square] is for things (a server, a workspace), [Circle] for people. */
public enum class AvatarShape { Circle, Square }

/**
 * What a person or a place looks like when it has no picture: one or two initials of [name] on the
 * secondary fill. «Демо-ресторан» → «ДР», «Kitchen» → «K», «» → «?».
 */
@Composable
public fun Avatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    shape: AvatarShape = AvatarShape.Circle,
    fill: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
) {
    val c = Kit.colors
    val clip = if (shape == AvatarShape.Circle) Kit.shapes.full else RoundedCornerShape(size * 0.28f)
    Box(
        modifier.size(size).clip(clip).background(if (fill != Color.Unspecified) fill else c.secondary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initialsOf(name),
            style = Kit.typography.small.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = androidx.compose.ui.unit.TextUnit(size.value * 0.38f, androidx.compose.ui.unit.TextUnitType.Sp),
            ),
            color = if (contentColor != Color.Unspecified) contentColor else c.onSecondary,
            maxLines = 1,
        )
    }
}

/** The first letters of the first two words, upper-cased; «?» when there are none. */
public fun initialsOf(name: String): String =
    name.split(' ', '-', '_', '.', '/')
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }
