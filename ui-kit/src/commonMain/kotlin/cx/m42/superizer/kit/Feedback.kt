package cx.m42.superizer.kit

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** An endless arc in the content colour: something is on its way and there is no telling how long. */
@Composable
public fun Spinner(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    color: Color = Color.Unspecified,
) {
    val tint = if (color != Color.Unspecified) color else currentContentColor
    val turn by rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
    )
    Canvas(
        modifier
            .size(size)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
    ) {
        val stroke = this.size.minDimension * 0.12f
        drawArc(
            color = tint,
            startAngle = turn,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
            topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
            size = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke),
        )
    }
}

/** How much of something is done, 0 to 1. Values outside are clamped. */
@Composable
public fun Progress(
    value: Float,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val fraction by animateFloatAsState(value.coerceIn(0f, 1f))
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(Kit.shapes.full)
            .background(Kit.colors.muted)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(0f, 1f), 0f..1f) },
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .clip(Kit.shapes.full)
                .background(if (color != Color.Unspecified) color else Kit.colors.primary),
        )
    }
}

/** A placeholder of the shape of what is loading. Size it with [modifier]. */
@Composable
public fun Skeleton(
    modifier: Modifier = Modifier,
    shape: Shape = Kit.shapes.md,
) {
    val pulse by rememberInfiniteTransition().animateFloat(
        initialValue = 1f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
    )
    Box(modifier.alpha(pulse).clip(shape).background(Kit.colors.muted))
}

/** [Info] is the blue of "this needs a person". */
public enum class AlertVariant { Default, Destructive, Success, Warning, Info }

/**
 * A message that stays on the screen until its cause is gone: a bordered block with a title, an
 * optional description and an optional icon. Not a toast — nothing here disappears on its own.
 */
@Composable
public fun Alert(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    variant: AlertVariant = AlertVariant.Default,
    icon: ImageVector? = null,
) {
    val c = Kit.colors
    val accent = when (variant) {
        AlertVariant.Default -> c.foreground
        AlertVariant.Destructive -> c.destructive
        AlertVariant.Success -> c.success
        AlertVariant.Warning -> c.warning
        AlertVariant.Info -> c.info
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(Kit.shapes.lg)
            .background(c.card)
            .border(1.dp, if (variant == AlertVariant.Default) c.border else accent.copy(alpha = 0.5f), Kit.shapes.lg)
            .padding(horizontal = Kit.spacing.lg, vertical = Kit.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Kit.spacing.md),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, size = 18.dp, tint = accent, modifier = Modifier.padding(top = 1.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            CompositionLocalProvider(LocalContentColor provides if (variant == AlertVariant.Default) c.onCard else accent) {
                Text(title, variant = TextVariant.Small)
            }
            if (description != null) Text(description, variant = TextVariant.Muted)
        }
    }
}
