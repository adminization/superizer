package cx.m42.superizer.kit

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A point in the [tone]'s colour. A [Tone.Running] one breathes quickly — something is happening
 * right now; a [Tone.Success] one slowly — it is alive. The halo is drawn inside the dot's own box,
 * so a breathing dot does not push its neighbours.
 */
@Composable
public fun StatusDot(tone: Tone, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    val color = tone.color()
    Box(modifier.size(size + 6.dp), contentAlignment = Alignment.Center) {
        if (tone == Tone.Running || tone == Tone.Success) {
            val t by rememberInfiniteTransition().animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(if (tone == Tone.Running) 1100 else 2400), RepeatMode.Restart),
            )
            Box(Modifier.size(size).scale(1f + t * 0.9f).alpha((1f - t) * 0.5f).clip(Kit.shapes.full).background(color))
        }
        Box(Modifier.size(size).clip(Kit.shapes.full).background(color))
    }
}

/**
 * The one way a status is shown: a soft pill with a dot, the same in a list, on a record and on a
 * board. The app owns the dictionary — the server's value to a [label] and a [tone]; the kit owns how
 * it looks. The solid [Badge] stays for counters and labels: a wall of solid statuses is how a
 * screen starts shouting.
 */
@Composable
public fun StatusBadge(label: String, tone: Tone, modifier: Modifier = Modifier) {
    PillFrame(label, tone, modifier, dot = true, icon = null)
}

/**
 * [StatusBadge]'s shape without the dot, for a label that is not a state: a plan, a scope, «Системная».
 * [Tone.Muted] is drawn as an outline, so «off» reads as absent rather than as another colour.
 */
@Composable
public fun Pill(text: String, tone: Tone = Tone.Neutral, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    PillFrame(text, tone, modifier, dot = false, icon = icon)
}

@Composable
private fun PillFrame(text: String, tone: Tone, modifier: Modifier, dot: Boolean, icon: ImageVector?) {
    val outline = tone == Tone.Muted
    val shape = Kit.shapes.full
    Row(
        modifier
            .height(24.dp)
            .clip(shape)
            .background(if (outline) Color.Transparent else tone.softColor())
            .then(if (outline) Modifier.border(1.dp, Kit.colors.border, shape) else Modifier)
            .padding(start = if (dot) 4.dp else 9.dp, end = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (dot) 2.dp else 5.dp),
    ) {
        if (dot) StatusDot(tone, size = 6.dp)
        if (icon != null) Icon(icon, contentDescription = null, size = 12.dp, tint = tone.textColor())
        Text(text, style = Kit.textStyles.caption.copy(fontWeight = FontWeight.Medium), color = tone.textColor(), maxLines = 1)
    }
}
