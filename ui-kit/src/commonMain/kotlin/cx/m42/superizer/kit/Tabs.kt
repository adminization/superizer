package cx.m42.superizer.kit

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * A segmented row of [items], one of them selected. It scrolls sideways when the labels do not fit,
 * so a filter with many states does not wrap or squeeze. What each tab shows is the caller's: this
 * only says which one is chosen.
 */
@Composable
public fun Tabs(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Kit.colors
    Row(
        modifier
            .clip(Kit.shapes.lg)
            .background(c.muted)
            .horizontalScroll(rememberScrollState())
            .padding(3.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val fill by animateColorAsState(if (selected) c.background else Color.Transparent)
            Box(
                Modifier
                    .defaultMinSize(minHeight = 36.dp)
                    .clip(Kit.shapes.md)
                    .background(fill)
                    .selectable(selected, role = Role.Tab, onClick = { onSelect(index) })
                    .padding(horizontal = Kit.spacing.md, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, variant = TextVariant.Small, color = if (selected) c.foreground else c.mutedForeground, maxLines = 1)
            }
        }
    }
}
