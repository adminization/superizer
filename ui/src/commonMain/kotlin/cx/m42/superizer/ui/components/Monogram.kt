package cx.m42.superizer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.Text
import cx.m42.superizer.theme.AppTheme

/**
 * What a server or an account looks like when it has no picture: one or two letters of its name on
 * a square, as ChatGPT's workspaces and GitHub's organisations are drawn.
 *
 * In the chrome's one colour rather than a colour per name: colour is reserved for state (D-tokens),
 * and a red square would read as "something is wrong with this server".
 */
@Deprecated(
    "Use cx.m42.superizer.kit.Avatar with AvatarShape.Square. The fill below keeps the old look; drop it for the kit's quieter one.",
    ReplaceWith(
        "Avatar(name = title, modifier = modifier, size = size, shape = AvatarShape.Square, fill = Kit.colors.primary, contentColor = Kit.colors.onPrimary)",
        "cx.m42.superizer.kit.Avatar",
        "cx.m42.superizer.kit.AvatarShape",
        "cx.m42.superizer.kit.Kit",
    ),
)
@Composable
public fun Monogram(title: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val tokens = AppTheme
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(tokens.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = monogramOf(title),
            style = tokens.label.copy(fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.38f).sp),
            color = tokens.primaryForeground,
            maxLines = 1,
        )
    }
}

/** "Демо-ресторан" → "ДР", "Kitchen" → "K", "" → "?". */
internal fun monogramOf(title: String): String =
    title.split(' ', '-', '_', '.', '/')
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }
