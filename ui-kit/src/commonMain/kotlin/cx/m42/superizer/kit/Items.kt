package cx.m42.superizer.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide

/**
 * A titled group of rows: the structural unit of a settings screen. The rows sit in one bordered
 * block; put an [ItemSeparator] between them.
 */
@Composable
public fun ItemGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Kit.spacing.sm)) {
        if (title != null || description != null) {
            Column(Modifier.padding(horizontal = Kit.spacing.xs), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (title != null) Text(title, variant = TextVariant.H4)
                if (description != null) Text(description, variant = TextVariant.Muted)
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(Kit.shapes.xl)
                .background(Kit.colors.card)
                .border(1.dp, Kit.colors.border, Kit.shapes.xl),
            content = content,
        )
    }
}

/** The line between two rows of an [ItemGroup], indented to the rows' text. */
@Composable
public fun ItemSeparator(modifier: Modifier = Modifier) {
    Separator(modifier, inset = Kit.spacing.lg)
}

/**
 * One row: an optional [leading] (an icon, an avatar), a [title] with a [description] under it, and
 * an optional [trailing] (a value, a switch, a chevron). With [onClick] the whole row is the target.
 */
@Composable
public fun Item(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    ItemRow(
        modifier.then(
            if (onClick != null) Modifier.pressable(enabled, Role.Button, onClick) else Modifier,
        ),
        title, description, enabled, leading, trailing,
    )
}

/** A row carrying a [Switch], with the whole row as its tap target. */
@Composable
public fun SwitchItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    ItemRow(
        modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        title, description, enabled, leading = null,
        trailing = { Switch(checked, onCheckedChange = null, enabled = enabled) },
    )
}

/** One of a set of mutually exclusive choices; the chosen one carries the tick. */
@Composable
public fun ChoiceItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    ItemRow(
        modifier.selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        title, description, enabled, leading = null,
        trailing = { if (selected) Icon(Lucide.Check, contentDescription = null) },
    )
}

@Composable
private fun ItemRow(
    modifier: Modifier,
    title: String,
    description: String?,
    enabled: Boolean,
    leading: (@Composable () -> Unit)?,
    trailing: (@Composable RowScope.() -> Unit)?,
) {
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = Kit.spacing.lg, vertical = Kit.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Kit.spacing.md),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title)
            if (description != null) Text(description, variant = TextVariant.Muted)
        }
        trailing?.invoke(this)
    }
}

/** A click with the kit's pressed fill instead of a ripple. */
@Composable
internal fun Modifier.pressable(enabled: Boolean, role: Role, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    return this
        .background(if (pressed) Kit.colors.accent else androidx.compose.ui.graphics.Color.Transparent)
        .clickable(interaction, indication = null, enabled = enabled, role = role, onClick = onClick)
}
