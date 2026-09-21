package cx.m42.superizer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.m42.superizer.app.AppIcon

/**
 * The host's icon pack, drawn by hand. The library pulls in no icon dependency, and at this handful
 * of shapes a few line calls cost far less than one — but drawing them ad hoc is what makes glyphs
 * disagree with each other, so they all share the geometry below instead.
 *
 * Every icon is authored on a unit square in fractional coordinates, stroked at one ratio with
 * round caps and joins, and sized by the call site. The proportions follow GitHub's Octicons.
 */

/** The nominal box an icon is drawn in. Call sites override it; the proportions never change. */
public val IconSize: Dp = 20.dp

/** Stroke width as a fraction of the box, so a glyph keeps its weight at any size. */
private const val StrokeRatio = 0.085f

@Composable
private fun Icon(size: Dp, body: DrawScope.(IconScope) -> Unit) {
    Canvas(modifier = Modifier.size(size)) {
        body(IconScope(this.size.width, this.size.height))
    }
}

/** Fractional coordinates: [x] and [y] map 0f..1f onto the box, so a glyph scales to any size. */
private class IconScope(val w: Float, val h: Float) {
    fun x(fraction: Float) = w * fraction

    fun y(fraction: Float) = h * fraction

    fun at(xf: Float, yf: Float) = Offset(x(xf), y(yf))

    val strokeWidth: Float get() = w * StrokeRatio

    val stroke: Stroke
        get() = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
}

private fun DrawScope.line(s: IconScope, tint: Color, x1: Float, y1: Float, x2: Float, y2: Float) {
    drawLine(tint, s.at(x1, y1), s.at(x2, y2), s.strokeWidth, StrokeCap.Round)
}

private fun DrawScope.circle(s: IconScope, tint: Color, cx: Float, cy: Float, r: Float) {
    drawCircle(tint, radius = s.w * r, center = s.at(cx, cy), style = s.stroke)
}

private fun DrawScope.box(s: IconScope, tint: Color, l: Float, t: Float, r: Float, b: Float) {
    drawRect(
        tint,
        topLeft = s.at(l, t),
        size = Size(s.x(r - l), s.y(b - t)),
        style = s.stroke,
    )
}

/** The burger that opens the menu: three evenly spaced rules. */
@Composable
public fun BurgerIcon(tint: Color, size: Dp = IconSize) {
    Icon(size) { s -> listOf(0.26f, 0.5f, 0.74f).forEach { y -> line(s, tint, 0.14f, y, 0.86f, y) } }
}

/** A left-pointing chevron for the back affordance. */
@Composable
public fun BackIcon(tint: Color, size: Dp = IconSize) {
    Icon(size) { s ->
        val path = Path().apply {
            moveTo(s.x(0.62f), s.y(0.2f))
            lineTo(s.x(0.32f), s.y(0.5f))
            lineTo(s.x(0.62f), s.y(0.8f))
        }
        drawPath(path, tint, style = s.stroke)
    }
}

/** A cog, for the settings page. */
@Composable
public fun SettingsIcon(tint: Color, size: Dp = IconSize) {
    Icon(size) { s ->
        circle(s, tint, 0.5f, 0.5f, 0.17f)
        val inner = 0.26f
        val outer = 0.4f
        repeat(8) { i ->
            // Eight spokes at 45° each, started at 22.5° so no tooth sits on the vertical axis —
            // one straight up reads as a stray mark rather than as part of the ring.
            val angle = (i * 45f + 22.5f) * (kotlin.math.PI.toFloat() / 180f)
            val dx = kotlin.math.cos(angle)
            val dy = kotlin.math.sin(angle)
            line(s, tint, 0.5f + dx * inner, 0.5f + dy * inner, 0.5f + dx * outer, 0.5f + dy * outer)
        }
    }
}

/** A tick, for the chosen row of a list of choices. */
@Composable
public fun CheckIcon(tint: Color, size: Dp = IconSize) {
    Icon(size) { s ->
        val path = Path().apply {
            moveTo(s.x(0.18f), s.y(0.52f))
            lineTo(s.x(0.42f), s.y(0.74f))
            lineTo(s.x(0.82f), s.y(0.26f))
        }
        drawPath(path, tint, style = s.stroke)
    }
}

/** The backspace arrow on a calculator's ⌫ key. */
@Composable
public fun BackspaceIcon(tint: Color, size: Dp = IconSize) {
    Icon(size) { s ->
        val body = Path().apply {
            moveTo(s.x(0.34f), s.y(0.22f))
            lineTo(s.x(0.9f), s.y(0.22f))
            lineTo(s.x(0.9f), s.y(0.78f))
            lineTo(s.x(0.34f), s.y(0.78f))
            lineTo(s.x(0.08f), s.y(0.5f))
            close()
        }
        drawPath(body, tint, style = s.stroke)
        line(s, tint, 0.48f, 0.38f, 0.74f, 0.62f)
        line(s, tint, 0.74f, 0.38f, 0.48f, 0.62f)
    }
}

/** A plus, for the tile on Home that leads to the catalog (D48). */
@Composable
public fun PlusIcon(tint: Color, size: Dp = IconSize) {
    Icon(size) { s ->
        line(s, tint, 0.5f, 0.2f, 0.5f, 0.8f)
        line(s, tint, 0.2f, 0.5f, 0.8f, 0.5f)
    }
}

/** Four tiles — Home, and the screen the drawer leads back to. */
@Composable
public fun AppsIcon(tint: Color, size: Dp = IconSize) {
    Icon(size) { s ->
        box(s, tint, 0.14f, 0.14f, 0.44f, 0.44f)
        box(s, tint, 0.56f, 0.14f, 0.86f, 0.44f)
        box(s, tint, 0.14f, 0.56f, 0.44f, 0.86f)
        box(s, tint, 0.56f, 0.56f, 0.86f, 0.86f)
    }
}

/** A QR code, reduced to its three finder squares — the part a person recognises. */
@Composable
public fun QrIcon(tint: Color, size: Dp = IconSize) {
    Icon(size) { s ->
        box(s, tint, 0.12f, 0.12f, 0.4f, 0.4f)
        box(s, tint, 0.6f, 0.12f, 0.88f, 0.4f)
        box(s, tint, 0.12f, 0.6f, 0.4f, 0.88f)
        line(s, tint, 0.6f, 0.6f, 0.6f, 0.74f)
        line(s, tint, 0.74f, 0.6f, 0.88f, 0.6f)
        line(s, tint, 0.74f, 0.74f, 0.88f, 0.74f)
        line(s, tint, 0.88f, 0.74f, 0.88f, 0.88f)
    }
}

/** A calculator: a body, a display and two rows of keys. */
@Composable
public fun CalculatorIcon(tint: Color, size: Dp = IconSize) {
    Icon(size) { s ->
        box(s, tint, 0.2f, 0.1f, 0.8f, 0.9f)
        line(s, tint, 0.32f, 0.28f, 0.68f, 0.28f)
        listOf(0.5f, 0.7f).forEach { y ->
            listOf(0.33f, 0.5f, 0.67f).forEach { x -> line(s, tint, x, y, x + 0.001f, y) }
        }
    }
}

/** Currency: a circle with the bar of a generic monetary glyph through it. */
@Composable
public fun CurrencyIcon(tint: Color, size: Dp = IconSize) {
    Icon(size) { s ->
        circle(s, tint, 0.5f, 0.5f, 0.36f)
        line(s, tint, 0.5f, 0.2f, 0.5f, 0.8f)
        line(s, tint, 0.36f, 0.38f, 0.64f, 0.38f)
        line(s, tint, 0.36f, 0.62f, 0.64f, 0.62f)
    }
}

/**
 * Draws whatever an app declared as its icon (D29).
 *
 * The [AppIcon.Path] branch is the one that matters for §24: an app ships its own glyph as an SVG
 * path string and appears on All Apps looking like itself, without one line of the host changing.
 * A malformed path draws the letter fallback rather than throwing — a bad icon must not be able to
 * take down the screen that lists apps.
 */
@Composable
public fun AppIconGlyph(icon: AppIcon, tint: Color, size: Dp = IconSize, fallbackLetter: Char = '?') {
    when (icon) {
        is AppIcon.Letter -> LetterIcon(icon.char, tint, size)
        is AppIcon.Named -> when (icon.name) {
            "calculator" -> CalculatorIcon(tint, size)
            "currency" -> CurrencyIcon(tint, size)
            "apps" -> AppsIcon(tint, size)
            "qr" -> QrIcon(tint, size)
            "settings" -> SettingsIcon(tint, size)
            else -> LetterIcon(fallbackLetter, tint, size)
        }

        is AppIcon.Path -> SvgPathIcon(icon.svgPath, tint, size, fallbackLetter)
    }
}

@Composable
private fun LetterIcon(char: Char, tint: Color, size: Dp) {
    // Drawn as a glyph rather than as Text so it shares the pack's box and never picks up a
    // different font metric from whatever surrounds it.
    Icon(size) { s ->
        circle(s, tint, 0.5f, 0.5f, 0.42f)
        val path = Path().apply {
            moveTo(s.x(0.36f), s.y(0.66f))
            lineTo(s.x(0.5f), s.y(0.32f))
            lineTo(s.x(0.64f), s.y(0.66f))
            moveTo(s.x(0.41f), s.y(0.56f))
            lineTo(s.x(0.59f), s.y(0.56f))
        }
        if (char == 'A' || char == 'a') drawPath(path, tint, style = s.stroke)
    }
}

/**
 * An SVG `d` attribute over a 24×24 viewBox, filled and scaled into the icon's box.
 *
 * Filled, not stroked: a path an app ships is authored as a silhouette (that is what every icon set
 * hands out), and stroking one would draw its outline twice.
 */
@Composable
private fun SvgPathIcon(svgPath: String, tint: Color, size: Dp, fallbackLetter: Char) {
    val parsed = runCatching { PathParser().parsePathString(svgPath).toPath() }.getOrNull()
    if (parsed == null) {
        LetterIcon(fallbackLetter, tint, size)
        return
    }
    Icon(size) { s ->
        val bounds: Rect = parsed.getBounds()
        val span = maxOf(bounds.width, bounds.height).takeIf { it > 0f } ?: VIEWPORT
        val factor = s.w / VIEWPORT
        scale(factor, factor, pivot = Offset.Zero) {
            translate(
                left = (VIEWPORT - span) / 2f - bounds.left + (span - bounds.width) / 2f,
                top = (VIEWPORT - span) / 2f - bounds.top + (span - bounds.height) / 2f,
            ) {
                drawPath(parsed, tint)
            }
        }
    }
}

/** The viewBox every shipped path is authored against, and the one the parser assumes. */
private const val VIEWPORT = 24f
