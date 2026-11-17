package cx.m42.superizer.kit

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Which step of [KitTypography] a [Text] is set in. */
public enum class TextVariant { H1, H2, H3, H4, P, Lead, Large, Small, Muted, Mono }

/**
 * Text in the kit's type scale.
 *
 * The colour is, in order: [color] when given; the muted foreground for [TextVariant.Muted] and
 * [TextVariant.Lead]; otherwise the content colour of the component it sits in. A muted caption
 * stays muted inside a card or a row — the variant is a decision, not a default to be overridden.
 */
@Composable
public fun Text(
    text: String,
    modifier: Modifier = Modifier,
    variant: TextVariant = TextVariant.P,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    style: TextStyle? = null,
) {
    val typography = Kit.typography
    val base = style ?: when (variant) {
        TextVariant.H1 -> typography.h1
        TextVariant.H2 -> typography.h2
        TextVariant.H3 -> typography.h3
        TextVariant.H4 -> typography.h4
        TextVariant.P -> typography.p
        TextVariant.Lead -> typography.lead
        TextVariant.Large -> typography.large
        TextVariant.Small -> typography.small
        TextVariant.Muted -> typography.muted
        TextVariant.Mono -> typography.mono
    }
    val resolved = when {
        color != Color.Unspecified -> color
        variant == TextVariant.Muted || variant == TextVariant.Lead -> Kit.colors.mutedForeground
        else -> currentContentColor
    }
    BasicText(
        text = text,
        modifier = modifier,
        style = base.copy(color = resolved, textAlign = textAlign ?: base.textAlign),
        maxLines = maxLines,
        overflow = overflow,
    )
}

/** The size of an icon beside 14–16 sp text. */
public val KitIconSize: Dp = 20.dp

/**
 * An [ImageVector] in the content colour. Pass `contentDescription = null` for an icon that only
 * decorates a label beside it; a lone icon must say what it means.
 */
@Composable
public fun Icon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = KitIconSize,
    tint: Color = Color.Unspecified,
) {
    val color = if (tint != Color.Unspecified) tint else currentContentColor
    Image(
        imageVector = imageVector,
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        modifier = modifier
            .size(size)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics {
                        this.contentDescription = contentDescription
                        role = Role.Image
                    }
                } else {
                    Modifier
                },
            ),
    )
}
