package cx.m42.superizer.kit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Charts of a dashboard: the shape of a number over time, how full a limit is, who spent what. No
 * axes library, no interaction — a chart here supports a number written next to it, it does not
 * replace it. Each takes a `contentDescription`, because a picture alone says nothing to a screen
 * reader: say what the chart shows in words («Расход за 30 дней, растёт»).
 */

/** 0..1, with anything outside — a broken percentage, NaN — held at the edge. */
internal fun clampFraction(value: Float): Float = if (value.isNaN()) 0f else value.coerceIn(0f, 1f)

/** Grows from 0 to 1 once when [key] first appears, so a chart draws itself in rather than popping. */
@Composable
private fun entrance(key: Any?): Float {
    val a = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { a.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    return a.value
}

private fun Modifier.described(text: String?): Modifier =
    if (text == null) this else semantics { contentDescription = text }

/** A smooth path through [values] (0..1, 1 at the top) in a box [w]×[h]. */
private fun smooth(values: List<Float>, w: Float, h: Float, pad: Float): Path {
    val path = Path()
    if (values.isEmpty()) return path
    val step = if (values.size > 1) w / (values.size - 1) else w
    fun y(v: Float) = pad + (1f - v) * (h - 2 * pad)
    path.moveTo(0f, y(values[0]))
    for (i in 1 until values.size) {
        val x0 = (i - 1) * step
        val x1 = i * step
        val mid = (x0 + x1) / 2
        path.cubicTo(mid, y(values[i - 1]), mid, y(values[i]), x1, y(values[i]))
    }
    return path
}

/**
 * A line with a fading fill under it and no axes: the shape of a metric next to its number. Values
 * are relative — the highest one touches the top. Fewer than two values draw a dashed baseline: there
 * is no shape yet, and an empty box would read as broken.
 */
@Composable
public fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = Kit.palette.brand,
    fill: Boolean = true,
    contentDescription: String? = null,
) {
    val t = entrance(values.size)
    Canvas(modifier.described(contentDescription)) {
        if (values.size < 2) {
            val y = size.height - 2.dp.toPx()
            drawLine(
                color.copy(alpha = 0.35f), Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
            return@Canvas
        }
        val max = values.max().coerceAtLeast(0.01f)
        val norm = values.map { (it / max).coerceIn(0f, 1f) * 0.9f * t }
        val line = smooth(norm, size.width, size.height, 2.dp.toPx())
        if (fill) {
            val area = Path().apply {
                addPath(line)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0f))))
        }
        drawPath(line, color, style = Stroke(1.75.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * Bars over days (or hours) with a light grid, a dashed line at the average and the last bar — «now»
 * — in full colour, the rest softened. [labels] go under the bars, three of them on a phone and six
 * on a wider window, spread evenly; [unit] follows the numbers of the scale («k»).
 */
@Composable
public fun BarChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    color: Color = Kit.palette.brand,
    unit: String = "",
    contentDescription: String? = null,
) {
    val t = entrance(values.size)
    val grid = Kit.colors.border
    val muted = Kit.colors.mutedForeground
    // Three even steps of a round size over the highest bar: 3 · 2 · 1, 6 · 4 · 2, 15 · 10 · 5 — never 5 · 3 · 1.
    val step = niceStep((values.maxOrNull() ?: 0f) / 3f)
    val max = step * 3
    val ticks = listOf(max, step * 2, step)
    val caption = Kit.textStyles.caption.copy(fontFeatureSettings = "tnum")
    Column(modifier.described(contentDescription), verticalArrangement = Arrangement.spacedBy(Kit.spacing.sm)) {
        Row(Modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.height(height), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
                ticks.forEach { Text(it.toInt().toString() + unit, style = caption, color = muted) }
                Text("0", style = caption, color = muted)
            }
            Canvas(Modifier.weight(1f).height(height)) {
                val top = 8.dp.toPx()
                val bottom = size.height - 8.dp.toPx()
                for (i in 0..3) {
                    val y = top + (bottom - top) * i / 3
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
                if (values.isEmpty()) return@Canvas
                val slot = size.width / values.size
                val bar = (slot * 0.62f).coerceAtMost(22.dp.toPx())
                values.forEachIndexed { i, v ->
                    val h = (bottom - top) * (v.coerceAtLeast(0f) / max) * t
                    drawRoundRect(
                        if (i == values.lastIndex) color else color.copy(alpha = 0.38f),
                        Offset(slot * i + (slot - bar) / 2, bottom - h), Size(bar, h), CornerRadius(3.dp.toPx()),
                    )
                }
                val avg = values.average().toFloat()
                val ay = bottom - (bottom - top) * (avg / max)
                drawLine(
                    muted.copy(alpha = 0.6f), Offset(0f, ay), Offset(size.width, ay), 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                )
            }
        }
        if (labels.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // The scale's widest number, invisible: the labels start where the bars do.
                Text(ticks.first().toInt().toString() + unit, style = caption, color = Color.Transparent)
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                    val n = minOf(byWindow(3, 6, 6), labels.size)
                    val shown = if (n <= 1) listOf(labels.last()) else List(n) { labels[it * labels.lastIndex / (n - 1)] }
                    shown.forEach { Text(it, style = caption, color = muted, maxLines = 1) }
                }
            }
        }
    }
}

/**
 * A ring filled to [value] (0..1) with something in its middle — a percentage, an icon: how much of a
 * limit is used. A value outside 0..1 is held at the edge; the fraction is told to a screen reader.
 */
@Composable
public fun Ring(
    value: Float,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    stroke: Dp = 7.dp,
    color: Color = Kit.palette.brand,
    contentDescription: String? = null,
    center: @Composable () -> Unit = {},
) {
    val t = entrance(Unit)
    val track = Kit.colors.muted
    val fraction = clampFraction(value)
    Box(
        modifier.size(size).semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            if (contentDescription != null) this.contentDescription = contentDescription
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val inset = Offset(s / 2, s / 2)
            val box = Size(this.size.width - s, this.size.height - s)
            drawArc(track, 0f, 360f, false, inset, box, style = Stroke(s))
            drawArc(color, -90f, 360f * fraction * t, false, inset, box, style = Stroke(s, cap = StrokeCap.Round))
        }
        center()
    }
}

/** The tone a [Meter] takes by itself: the accent, then a warning from 75 %, danger from 90 %. */
public fun meterTone(value: Float): Tone = when {
    value >= 0.9f -> Tone.Danger
    value >= 0.75f -> Tone.Warn
    else -> Tone.Brand
}

/**
 * A thin bar of how full something is: memory, a disk, the days the balance lasts. Without a [color]
 * it warns by itself as the limit comes close ([meterTone]) — the colour carries the alarm so the
 * number next to it does not have to be red. The kit's [Progress] is for a task's progress; this is
 * for a level.
 */
@Composable
public fun Meter(
    value: Float,
    modifier: Modifier = Modifier,
    color: Color? = null,
    height: Dp = 6.dp,
    contentDescription: String? = null,
) {
    val t = entrance(Unit)
    val track = Kit.colors.muted
    val fraction = clampFraction(value)
    val fill = color ?: meterTone(fraction).color()
    Canvas(
        modifier.fillMaxWidth().height(height).semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            if (contentDescription != null) this.contentDescription = contentDescription
        },
    ) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        drawRoundRect(fill, size = Size(size.width * fraction * t, size.height), cornerRadius = r)
    }
}

/**
 * One bar split into coloured shares, in the order of [shares] and the colours of the palette's
 * series: «who spent what» above a legend the caller writes in the same order. Negative shares count
 * as nothing; all zeros draw an empty track.
 */
@Composable
public fun ShareBar(
    shares: List<Float>,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    contentDescription: String? = null,
) {
    val t = entrance(shares.size)
    val colors = Kit.palette.chart
    val track = Kit.colors.muted
    Canvas(modifier.fillMaxWidth().height(height).described(contentDescription)) {
        val parts = shares.map { it.coerceAtLeast(0f) }
        val total = parts.sum()
        val r = CornerRadius(3.dp.toPx())
        if (total <= 0f) {
            drawRoundRect(track, cornerRadius = r)
            return@Canvas
        }
        val g = 2.dp.toPx()
        var x = 0f
        parts.forEachIndexed { i, s ->
            val w = (size.width - g * (parts.size - 1)) * (s / total) * t
            drawRoundRect(colors[i % colors.size], Offset(x, 0f), Size(w, size.height), r)
            x += w + g
        }
    }
}

/** The smallest of 1, 2, 2.5, 5 times a power of ten (1, 2, 3, 5 below ten: whole ticks) at least [raw], and at least 1. */
internal fun niceStep(raw: Float): Float {
    if (raw <= 1f) return 1f
    var power = 1f
    while (power * 10 <= raw) power *= 10
    val steps = if (power < 10f) listOf(1f, 2f, 3f, 5f, 10f) else listOf(1f, 2f, 2.5f, 5f, 10f)
    return steps.map { it * power }.first { it >= raw }
}
