package cx.m42.superizer.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.delay

/**
 * An icon on a rounded square of its [tone]'s soft fill: the leading mark of a row, a step, an empty
 * state. Neutral and muted tiles keep the icon grey, so only meaningful tiles carry colour.
 */
@Composable
public fun IconTile(icon: ImageVector, tone: Tone = Tone.Brand, size: Dp = 36.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(tone.softColor()),
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (tone == Tone.Neutral || tone == Tone.Muted) Kit.colors.mutedForeground else tone.textColor()
        Icon(icon, contentDescription = null, size = size * 0.5f, tint = tint)
    }
}

/**
 * The face of a thing without a picture — a server, a restaurant: its initial on a gradient picked
 * from the name, so each one is recognisable in a list at a glance and stays the same everywhere.
 * [Avatar] is for people; this is for things. [dimmed] — the thing is off.
 */
@Composable
public fun NameMark(name: String, size: Dp = 40.dp, modifier: Modifier = Modifier, dimmed: Boolean = false) {
    val chart = Kit.palette.chart
    val base = chart[nameMarkIndex(name, chart.size)]
    val fill = if (dimmed) {
        Brush.linearGradient(listOf(Kit.colors.muted, Kit.colors.muted))
    } else {
        Brush.linearGradient(listOf(lerp(base, Color.White, 0.18f), lerp(base, Color.Black, 0.22f)))
    }
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(fill),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().firstOrNull()?.uppercase() ?: "?",
            style = Kit.typography.small.copy(fontWeight = FontWeight.SemiBold, fontSize = TextUnit(size.value * 0.42f, TextUnitType.Sp)),
            color = if (dimmed) Kit.colors.mutedForeground else Color.White,
            maxLines = 1,
        )
    }
}

/** Which series colour a name gets: stable for the name, spread over the palette. */
internal fun nameMarkIndex(name: String, colours: Int): Int =
    if (colours <= 0) 0 else (name.sumOf { it.code } % minOf(colours, 5)).coerceAtLeast(0)

/** A key of the keyboard, for a shortcut beside the thing it opens: «⌘K». */
@Composable
public fun Kbd(text: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(5.dp)
    Box(
        modifier
            .defaultMinSize(minWidth = 20.dp)
            .height(20.dp)
            .clip(shape)
            .background(Kit.colors.background)
            .border(1.dp, Kit.colors.border, shape)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Kit.textStyles.caption.copy(fontWeight = FontWeight.Medium), color = Kit.colors.mutedForeground, maxLines = 1)
    }
}

/**
 * A value a person copies — an address, an id, a token — with the button that does it, which turns
 * into a tick for a moment so the tap is answered. The text is put on the clipboard by [onCopy]; the
 * kit has no clipboard of its own (it differs per platform), so without [onCopy] only the tick shows.
 * [copyLabel] and [copiedLabel] are what a screen reader says, in the app's language.
 */
@Composable
public fun CopyValue(
    value: String,
    modifier: Modifier = Modifier,
    mono: Boolean = true,
    copyLabel: String = "Copy",
    copiedLabel: String = "Copied",
    color: Color = Color.Unspecified,
    onCopy: ((String) -> Unit)? = null,
) {
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1400)
            copied = false
        }
    }
    Row(
        modifier
            .kitClickable(RoundedCornerShape(6.dp), hover = false) {
                onCopy?.invoke(value)
                copied = true
            }
            .semantics {
                contentDescription = copyLabel
                stateDescription = if (copied) copiedLabel else ""
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            value,
            Modifier.weight(1f, fill = false),
            variant = if (mono) TextVariant.Mono else TextVariant.P,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            if (copied) Lucide.Check else Lucide.Copy,
            contentDescription = null,
            size = 14.dp,
            tint = if (copied) Kit.colors.success else Kit.colors.mutedForeground,
        )
    }
}

/**
 * «Label ........ value» — what a person quotes when asking about a record: its plan, its version,
 * its id. The label stays left and muted, the value right and in tabular figures; [mono] for ids and
 * addresses, [copyable] when it is quoted by pasting ([CopyValue]); [trailing] for a badge instead of
 * text. A null [value] with no [trailing] leaves only the label. [copyLabel]/[copiedLabel] — the copy
 * button's words in the app's language.
 */
@Composable
public fun FactRow(
    label: String,
    value: String? = null,
    modifier: Modifier = Modifier,
    mono: Boolean = false,
    copyable: Boolean = false,
    copyLabel: String = "Copy",
    copiedLabel: String = "Copied",
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Kit.spacing.md),
    ) {
        Text(label, variant = TextVariant.Muted, maxLines = 1)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            when {
                trailing != null -> trailing()
                copyable && value != null -> CopyValue(value, mono = mono, copyLabel = copyLabel, copiedLabel = copiedLabel)
                value != null -> Text(
                    value,
                    style = if (mono) Kit.typography.mono else Kit.textStyles.number,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

/**
 * The place where a list would be, when it is empty. Two meanings, two texts — and the caller writes
 * both: «nothing yet» (what it is, why it is empty, an [action] to create the first) and «nothing
 * matches» (say so, offer to reset). The dashed frame tells it apart from content at a glance.
 */
@Composable
public fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val border = Kit.colors.input
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    border,
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
                )
            }
            .padding(horizontal = Kit.spacing.xl, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconTile(icon, Tone.Brand, 48.dp)
        Spacer(Modifier.height(Kit.spacing.sm))
        Text(title, variant = TextVariant.Large, textAlign = TextAlign.Center)
        Text(description, Modifier.widthIn(max = 380.dp), variant = TextVariant.Muted, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(10.dp))
            action()
        }
    }
}

/**
 * A coloured block at the top of a page: something is wrong or waiting, said in words, with the one
 * thing to do about it beside it («Перезапустить сейчас»). [Alert] stays for a message without an
 * action, on the card's fill; a notice is tinted in its [tone] so it is seen before the content, and
 * carries its [action] — on a phone under the text, on a wider window at the right.
 */
@Composable
public fun Notice(
    tone: Tone,
    icon: ImageVector,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val phone = LocalWindowClass.current == WindowClass.Compact
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Kit.colors.card)
            .background(tone.softColor())
            .border(1.dp, tone.color().copy(alpha = 0.28f), shape)
            .padding(Kit.spacing.lg),
        verticalAlignment = if (phone) Alignment.Top else Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(top = if (phone) 2.dp else 0.dp), size = 20.dp, tint = tone.textColor())
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = Kit.textStyles.label.copy(fontWeight = FontWeight.SemiBold))
            Text(text, variant = TextVariant.Muted)
            if (phone && action != null) {
                Spacer(Modifier.height(6.dp))
                action()
            }
        }
        if (!phone && action != null) action()
    }
}

/**
 * A number with its name: a tile of a dashboard. The [hint] under it says what the number means
 * («хватит на 42 дня»); a non-neutral [tone] colours only the hint, never the number — a 0 is not an
 * alarm. With [onClick] the tile opens what it counts. [extra] — a sparkline or a meter under it.
 */
@Composable
public fun Stat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    icon: ImageVector? = null,
    tone: Tone = Tone.Neutral,
    onClick: (() -> Unit)? = null,
    extra: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    val pad = byWindow(Kit.spacing.lg, 20.dp, 20.dp)
    Column(
        modifier
            .clip(shape)
            .background(Kit.colors.card)
            .border(1.dp, Kit.colors.border, shape)
            .then(if (onClick != null) Modifier.kitClickable(onClick = onClick) else Modifier)
            .padding(pad),
    ) {
        CompositionLocalProvider(LocalContentColor provides Kit.colors.onCard) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Kit.spacing.sm)) {
                if (icon != null) Icon(icon, contentDescription = null, size = 15.dp, tint = Kit.colors.mutedForeground)
                Text(label, Modifier.weight(1f), variant = TextVariant.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(10.dp))
            Text(value, style = Kit.textStyles.metric, maxLines = 1)
            if (hint != null) {
                Spacer(Modifier.height(Kit.spacing.xs))
                Text(
                    hint,
                    style = Kit.textStyles.caption,
                    color = if (tone == Tone.Neutral) Kit.colors.mutedForeground else tone.textColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (extra != null) {
                Spacer(Modifier.height(Kit.spacing.md))
                extra()
            }
        }
    }
}

// The terminal is dark in both themes: logs read as logs, and a light one would glare in the dark set.
private val TerminalFill: Color = lerp(DarkKitColors.background, Color.Black, 0.35f)
private val TerminalBorder: Color = DarkKitColors.border
private val TerminalTitle: Color = DarkKitColors.mutedForeground

/**
 * The always-dark block logs, a request example or a JSON are read in, with a title bar. Its content
 * draws in a light content colour; with [height] it scrolls inside its own frame, so a long log never
 * makes the page scroll forever. [live] puts a breathing dot and [liveLabel] at the right of the bar.
 */
@Composable
public fun Terminal(
    title: String,
    modifier: Modifier = Modifier,
    live: Boolean = false,
    liveLabel: String? = null,
    height: Dp? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(TerminalFill).border(1.dp, TerminalBorder, shape)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(3) { Box(Modifier.size(9.dp).clip(Kit.shapes.full).background(TerminalBorder)) }
            Spacer(Modifier.size(6.dp))
            Text(title, Modifier.weight(1f), style = Kit.textStyles.monoSmall, color = TerminalTitle, maxLines = 1)
            if (live) {
                StatusDot(Tone.Success, size = 6.dp)
                if (liveLabel != null) Text(liveLabel, style = Kit.textStyles.caption, color = TerminalTitle)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(TerminalBorder))
        Box(
            (if (height != null) Modifier.height(height).verticalScroll(rememberScrollState()) else Modifier)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = Kit.spacing.md),
        ) {
            CompositionLocalProvider(LocalContentColor provides DarkKitColors.foreground, content = content)
        }
    }
}
