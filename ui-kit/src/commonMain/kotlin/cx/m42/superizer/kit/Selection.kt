package cx.m42.superizer.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Trash2

/**
 * A filter's option in a row of them: the chosen one filled with the foreground colour, the others
 * outlined. A [count] says how many records it would leave; a [dot] marks the option that needs
 * attention («Ошибка» in red) without colouring the whole chip.
 */
@Composable
public fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, count: Int? = null, dot: Tone? = null) {
    val c = Kit.colors
    Row(
        modifier
            .defaultMinSize(minHeight = 32.dp)
            .clip(CircleShape)
            .background(if (selected) c.foreground else c.card)
            .then(if (selected) Modifier else Modifier.border(1.dp, c.border, CircleShape))
            .selectable(selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot != null) Box(Modifier.size(6.dp).clip(CircleShape).background(dot.color()))
        Text(text, variant = TextVariant.Small, color = if (selected) c.background else c.foreground, maxLines = 1)
        if (count != null) {
            Text(
                count.toString(),
                style = Kit.textStyles.caption.copy(fontFeatureSettings = "tnum"),
                color = if (selected) c.background.copy(alpha = 0.7f) else c.mutedForeground,
            )
        }
    }
}

/**
 * Tabs for the sections of one record or one app — «Обзор, События, Логи»: labels on a hairline, the
 * chosen one underlined in the accent. They scroll sideways rather than wrap or squeeze. For a
 * segmented switch between two or three views of the same list, the kit's [Tabs] reads better.
 */
@Composable
public fun LineTabs(items: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val border = Kit.colors.border
    val brand = Kit.palette.brand
    Row(
        modifier
            .fillMaxWidth()
            .drawBehind { drawLine(border, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1.dp.toPx()) }
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Kit.spacing.xs),
    ) {
        items.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .height(44.dp)
                    .drawBehind {
                        if (on) drawRect(brand, Offset(8.dp.toPx(), size.height - 2.dp.toPx()), Size(size.width - 16.dp.toPx(), 2.dp.toPx()))
                    }
                    .selectable(on, role = Role.Tab) { onSelect(i) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = Kit.textStyles.label, color = if (on) Kit.colors.foreground else Kit.colors.mutedForeground, maxLines = 1)
            }
        }
    }
}

/**
 * A tile that is one of several choices — a plan, a payment method, an amount. The chosen one takes
 * the accent's border and soft fill, so the choice reads at a glance without a separate radio.
 */
@Composable
public fun ChoiceTile(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (selected) Kit.palette.brandSoft else Kit.colors.card)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) Kit.palette.brand else Kit.colors.border, shape)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(padding),
    ) { content() }
}

/**
 * Where a person is in a flow of a few steps: numbered dots, done ones ticked, the current one in the
 * accent. On a phone only the current step keeps its label — four labels do not fit beside each other.
 */
@Composable
public fun StepIndicator(steps: List<String>, current: Int, modifier: Modifier = Modifier) {
    val compact = LocalWindowClass.current == WindowClass.Compact
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Kit.spacing.sm)) {
        steps.forEachIndexed { i, label ->
            val done = i < current
            val on = i == current
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(if (done || on) Kit.palette.brand else Kit.colors.secondary),
                contentAlignment = Alignment.Center,
            ) {
                if (done) {
                    Icon(Lucide.Check, null, size = 13.dp, tint = Kit.palette.onBrand)
                } else {
                    Text(
                        (i + 1).toString(),
                        style = Kit.textStyles.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = if (on) Kit.palette.onBrand else Kit.colors.mutedForeground,
                    )
                }
            }
            if (on || !compact) {
                Text(label, style = Kit.textStyles.label, color = if (on) Kit.colors.foreground else Kit.colors.mutedForeground, maxLines = 1)
            }
            if (i < steps.lastIndex) Box(Modifier.width(if (compact) 16.dp else 28.dp).height(1.dp).background(Kit.colors.input))
        }
    }
}

/**
 * «− n +» for a quantity: a line of a receipt, a stock count. Each button is a 44 dp target.
 *
 * At [min] the minus is disabled — unless there is a [removeLabel]: then it becomes a bin with that
 * label and calls [onChange] with `min - 1`, which the caller takes as «remove the line». A value below
 * [min] is never shown; it is only the signal.
 */
@Composable
public fun QtyStepper(
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = 0,
    max: Int = Int.MAX_VALUE,
    removeLabel: String? = null,
) {
    val removes = value <= min && removeLabel != null
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier.clip(shape).border(1.dp, Kit.colors.input, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(
            label = if (removes) removeLabel else "−",
            enabled = value > min || removes,
            onClick = { onChange(if (removes) min - 1 else value - 1) },
        ) {
            Icon(if (removes) Lucide.Trash2 else Lucide.Minus, null, size = 16.dp, tint = if (removes) Kit.colors.destructive else Color.Unspecified)
        }
        Text(
            value.toString(),
            Modifier.width(36.dp),
            style = Kit.textStyles.number.copy(fontWeight = FontWeight.Medium),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        StepButton(label = "+", enabled = value < max, onClick = { onChange(value + 1) }) {
            Icon(Lucide.Plus, null, size = 16.dp)
        }
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .kitClickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalContentColor provides if (enabled) Kit.colors.foreground else Kit.colors.mutedForeground.copy(alpha = 0.5f),
            content = icon,
        )
    }
}
