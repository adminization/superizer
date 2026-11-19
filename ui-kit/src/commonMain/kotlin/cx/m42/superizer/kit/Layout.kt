package cx.m42.superizer.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide

/**
 * How wide a page's content may grow. The screen's kind picks it, not taste: [Narrow] for settings and
 * a focused flow, [Default] for lists and records, [Wide] for what is wider than a column by nature —
 * a board, a log, a till.
 */
public enum class PageWidth(public val max: Dp) {
    Narrow(720.dp),
    Default(1120.dp),
    Wide(1360.dp),
}

/** The corners of a [Panel]: a step rounder than a field, so a block reads as a block. */
private val PanelShape = RoundedCornerShape(14.dp)

/**
 * A screen's body: the gutters (16, 24 or 32 dp by window class), the content's max [width], centred
 * (a phone's content is the window, whatever the width), and the gap between its blocks. The only place those are set — a screen that pads itself is how two
 * screens end up with two left edges.
 *
 * With [scroll] the page is the pane's one vertical scroller; pass false when a child scrolls itself
 * (a `LazyColumn`, a board), never both.
 */
@Composable
public fun Page(
    width: PageWidth = PageWidth.Default,
    modifier: Modifier = Modifier,
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val gutter = byWindow(16.dp, 24.dp, 32.dp)
    val top = byWindow(12.dp, 24.dp, 28.dp)
    val capped = LocalWindowClass.current != WindowClass.Compact
    Box(
        modifier.fillMaxSize().then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .then(if (capped) Modifier.widthIn(max = width.max + gutter * 2) else Modifier)
                .fillMaxWidth()
                .padding(start = gutter, end = gutter, top = top, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(byWindow(16.dp, 20.dp, 24.dp)),
            content = content,
        )
    }
}

/** One step of the way to a page; the last one, where you are, has no [onClick]. */
public data class Crumb(val label: String, val onClick: (() -> Unit)?)

/**
 * The top of every screen: what this is ([title]), what it is about, where it sits and what can be
 * done here.
 *
 * [crumbs] are drawn wherever the window is wider than a phone: there an adaptive host draws no bar
 * over a wide app, and the crumbs are its way back. On a phone the host's back arrow says the same. [actions] sit beside the title where there is room (Expanded; Medium without [leading]),
 * and drop under it otherwise, so the title is never squeezed to an ellipsis.
 */
@Composable
public fun PageHeader(
    title: String,
    description: String? = null,
    crumbs: List<Crumb> = emptyList(),
    leading: (@Composable () -> Unit)? = null,
    badge: (@Composable () -> Unit)? = null,
    meta: (@Composable RowScope.() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val cls = LocalWindowClass.current
    val below = cls == WindowClass.Compact || (cls == WindowClass.Medium && leading != null)
    Column(verticalArrangement = Arrangement.spacedBy(Kit.spacing.md)) {
        // Wherever the host draws no bar over a wide app (a rail or a sidebar), the crumbs are its way back.
        if (crumbs.isNotEmpty() && cls != WindowClass.Compact) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                crumbs.forEachIndexed { i, crumb ->
                    if (i > 0) Icon(Lucide.ChevronRight, null, size = 14.dp, tint = Kit.colors.mutedForeground)
                    val click = crumb.onClick
                    Text(
                        crumb.label,
                        if (click != null) Modifier.kitClickable(RoundedCornerShape(4.dp), hover = false, onClick = click) else Modifier,
                        variant = TextVariant.Muted,
                        color = if (click == null) Kit.colors.foreground else Kit.colors.mutedForeground,
                        maxLines = 1,
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            leading?.invoke()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Kit.spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        title,
                        Modifier.weight(1f, fill = false),
                        variant = if (cls == WindowClass.Compact) TextVariant.H3 else TextVariant.H2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    badge?.invoke()
                }
                if (description != null) Text(description, variant = TextVariant.Muted)
                if (meta != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Kit.spacing.md), content = meta)
            }
            if (actions != null && !below) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Kit.spacing.sm), content = actions)
        }
        if (actions != null && below) {
            Row(
                if (cls == WindowClass.Compact) Modifier.fillMaxWidth() else Modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Kit.spacing.sm),
                content = actions,
            )
        }
        // Inside an app's sections a wide window shows them as tabs right here, under the title they
        // belong to; on a phone they are the bar at the bottom (`AppSections`).
        LocalAppSections.current?.let { sections ->
            if (cls != WindowClass.Compact) SectionTabs(sections)
        }
    }
}

/** A heading between blocks of a page, with a [hint] after it and an [action] at its right edge. */
@Composable
public fun SectionTitle(title: String, modifier: Modifier = Modifier, hint: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = Kit.spacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Kit.spacing.sm)) {
        Text(title, variant = TextVariant.Large)
        if (hint != null) Text(hint, variant = TextVariant.Muted)
        Box(Modifier.weight(1f))
        action?.invoke()
    }
}

/**
 * The bordered card a page's blocks sit in. With a [title] it gets a header row (an [icon], a
 * [description], an [action] at the right); [flush] drops the body's padding for content that runs
 * edge to edge — a [DataTable], a list of rows with hairlines between them. With [onClick] the whole
 * panel is one target.
 */
@Composable
public fun Panel(
    modifier: Modifier = Modifier,
    title: String? = null,
    description: String? = null,
    icon: ImageVector? = null,
    action: (@Composable RowScope.() -> Unit)? = null,
    flush: Boolean = false,
    onClick: (() -> Unit)? = null,
    gap: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val pad = byWindow(16.dp, 20.dp, 20.dp)
    Column(
        modifier
            .clip(PanelShape)
            .background(Kit.colors.card)
            .border(1.dp, Kit.colors.border, PanelShape)
            .then(if (onClick != null) Modifier.kitClickable(onClick = onClick) else Modifier),
    ) {
        CompositionLocalProvider(LocalContentColor provides Kit.colors.onCard) {
            if (title != null) {
                Row(
                    Modifier.fillMaxWidth().padding(start = pad, end = pad, top = pad - 2.dp, bottom = if (flush) 12.dp else 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (icon != null) Icon(icon, null, size = 16.dp, tint = Kit.colors.mutedForeground)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title, style = Kit.textStyles.label.copy(fontWeight = FontWeight.SemiBold))
                        if (description != null) Text(description, variant = TextVariant.Muted)
                    }
                    action?.invoke(this)
                }
            }
            Column(
                if (flush) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier.fillMaxWidth().padding(start = pad, end = pad, bottom = pad, top = if (title != null) 14.dp else pad)
                },
                verticalArrangement = Arrangement.spacedBy(if (flush) 0.dp else gap),
                content = content,
            )
        }
    }
}

/**
 * Equal columns with one [gap]. The cells of a row share the row's height, so cards side by side line
 * up at the bottom; [spans] lets cell `i` take several columns. The number of columns is the caller's —
 * usually `byWindow(1, 2, 3)`.
 */
@Composable
public fun Grid(columns: Int, modifier: Modifier = Modifier, gap: Dp = 16.dp, spans: List<Int> = emptyList(), content: @Composable () -> Unit) {
    require(columns > 0) { "a grid has at least one column" }
    Layout(content, modifier) { measurables, constraints ->
        val g = gap.roundToPx()
        val cell = ((constraints.maxWidth - g * (columns - 1)) / columns).coerceAtLeast(0)
        fun spanOf(i: Int) = (spans.getOrNull(i) ?: 1).coerceIn(1, columns)
        fun widthOf(i: Int): Int = cell * spanOf(i) + g * (spanOf(i) - 1)
        val rows = mutableListOf<MutableList<Int>>()
        var used = columns
        measurables.indices.forEach { i ->
            if (used + spanOf(i) > columns) {
                rows.add(mutableListOf())
                used = 0
            }
            rows.last().add(i)
            used += spanOf(i)
        }
        val heights = rows.map { row -> row.maxOf { measurables[it].maxIntrinsicHeight(widthOf(it)) } }
        val placeables = rows.mapIndexed { r, row -> row.map { measurables[it].measure(Constraints.fixed(widthOf(it), heights[r])) } }
        val total = heights.sum() + g * (rows.size - 1).coerceAtLeast(0)
        layout(constraints.maxWidth, total) {
            var y = 0
            placeables.forEachIndexed { r, row ->
                var x = 0
                row.forEach {
                    it.place(x, y)
                    x += it.width + g
                }
                y += heights[r] + g
            }
        }
    }
}

/**
 * One column of a [DataTable]: its [title], a share of the width ([weight]) or a fixed [width], and
 * how a row's [cell] is drawn. Numbers go [alignEnd], so their digits line up.
 */
public class TableColumn<T>(
    public val title: String,
    public val weight: Float = 1f,
    public val width: Dp? = null,
    public val alignEnd: Boolean = false,
    public val cell: @Composable (T) -> Unit,
)

/**
 * A table for a wide window: a quiet header row, hairlines between rows, a row that lights up under
 * the pointer and opens on a click. Choose the [columns] explicitly — a table of every field is the
 * first thing that goes wrong. On a phone, draw the rows as cards instead; a table does not fit there.
 */
@Composable
public fun <T> DataTable(
    rows: List<T>,
    columns: List<TableColumn<T>>,
    modifier: Modifier = Modifier,
    rowHeight: Dp = 56.dp,
    onRow: ((T) -> Unit)? = null,
) {
    @Composable
    fun RowScope.cell(column: TableColumn<T>, content: @Composable BoxScope.() -> Unit) {
        Box(
            (if (column.width != null) Modifier.width(column.width) else Modifier.weight(column.weight)).padding(horizontal = 10.dp),
            contentAlignment = if (column.alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
            content = content,
        )
    }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(40.dp).background(Kit.palette.canvas.copy(alpha = 0.6f)).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            columns.forEach { column ->
                cell(column) {
                    Text(column.title, style = Kit.textStyles.caption.copy(fontWeight = FontWeight.Medium), color = Kit.colors.mutedForeground, maxLines = 1)
                }
            }
        }
        rows.forEach { row ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(Kit.colors.border))
            Row(
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = rowHeight)
                    .then(if (onRow != null) Modifier.kitClickable { onRow(row) } else Modifier)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { columns.forEach { column -> cell(column) { column.cell(row) } } }
        }
    }
}

/**
 * The sticky bar at the bottom of a phone's form or flow: the total and the one primary button, above
 * the keyboard and the navigation. On a wider window the same action belongs in the [PageHeader].
 */
@Composable
public fun ActionBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val border = Kit.colors.border
    Row(
        modifier
            .fillMaxWidth()
            .background(Kit.colors.card)
            .drawBehind { drawLine(border, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
            .padding(horizontal = Kit.spacing.lg, vertical = Kit.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Kit.spacing.md),
    ) { CompositionLocalProvider(LocalContentColor provides Kit.colors.onCard) { content() } }
}
