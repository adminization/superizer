// Every widget here is deprecated in favour of ui-kit (docs/06-ui-api.md, "Moving to ui-kit"). They keep
// working unchanged until a major release removes them; this file still calls its own.
@file:Suppress("DEPRECATION")

package cx.m42.superizer.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.composeunstyled.Button
import com.composeunstyled.Text
import cx.m42.superizer.theme.AppTheme

/**
 * Visual weight of a button. [Destructive] is a *tint*, not a filled red button: a row's own title
 * has to stay the loudest thing in it.
 */
@Deprecated(
    "Use cx.m42.superizer.kit.ButtonVariant. Default, Secondary, Outline and Ghost are the same; Destructive there is " +
        "filled red, not a tint; Accent has no counterpart — use Default, or a Badge/Alert with the Info variant.",
)
public enum class ButtonVariant { Default, Outline, Secondary, Ghost, Destructive, Accent }

/** [Lg] is the page-sized button; [Sm] is the one that fits inside a row. */
@Deprecated("Use cx.m42.superizer.kit.ButtonSize: Sm, Default and Lg (48 dp). The old Lg was taller; Lg is the nearest.")
public enum class ButtonSize { Sm, Default, Lg }

/**
 * A button on the Compose Unstyled primitive, styled to the host's monochrome look — the one place
 * a button's colours are decided, so no screen has to pick them.
 */
@Deprecated(
    "Use cx.m42.superizer.kit.Button. Its default size is ButtonSize.Default (40 dp), not Lg; it also has loading and leadingIcon.",
    ReplaceWith(
        "Button(text = text, onClick = onClick, modifier = modifier, variant = variant.toKit(), size = size.toKit(), enabled = enabled)",
        "cx.m42.superizer.kit.Button",
        "cx.m42.superizer.ui.components.toKit",
    ),
)
@Composable
public fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: ButtonVariant = ButtonVariant.Default,
    size: ButtonSize = ButtonSize.Lg,
) {
    val tokens = AppTheme
    val background = when (variant) {
        ButtonVariant.Default -> tokens.primary
        ButtonVariant.Secondary -> tokens.surface
        ButtonVariant.Outline, ButtonVariant.Ghost -> tokens.background
        ButtonVariant.Destructive -> tokens.dangerSubtle
        ButtonVariant.Accent -> tokens.accentSubtle
    }
    val foreground = when (variant) {
        ButtonVariant.Default -> tokens.primaryForeground
        ButtonVariant.Secondary, ButtonVariant.Outline, ButtonVariant.Ghost -> tokens.foreground
        ButtonVariant.Destructive -> tokens.danger
        ButtonVariant.Accent -> tokens.accent
    }
    val border = when (variant) {
        ButtonVariant.Outline, ButtonVariant.Secondary -> tokens.border
        else -> Color.Transparent
    }
    val padding = when (size) {
        ButtonSize.Sm -> PaddingValues(horizontal = 12.dp, vertical = 7.dp)
        ButtonSize.Default -> PaddingValues(horizontal = 20.dp, vertical = 12.dp)
        ButtonSize.Lg -> PaddingValues(horizontal = 40.dp, vertical = 18.dp)
    }

    Button(
        // Compose Unstyled's Button has no disabled state of its own, so gate the click here and
        // dim the surface to signal it.
        onClick = { if (enabled) onClick() },
        modifier = modifier,
        shape = RoundedCornerShape(if (size == ButtonSize.Sm) 8.dp else tokens.radius),
        backgroundColor = if (enabled) background else tokens.disabled,
        contentColor = if (enabled) foreground else tokens.primaryForeground,
        borderColor = if (enabled) border else Color.Transparent,
        borderWidth = if (border == Color.Transparent) 0.dp else 1.dp,
        contentPadding = padding,
    ) {
        Text(text = text, style = if (size == ButtonSize.Sm) tokens.label else tokens.buttonLabel)
    }
}

/** A 44.dp round tap target — the platform minimum — wrapping a hand-drawn glyph. */
@Deprecated(
    "Use cx.m42.superizer.kit.IconButton(icon = Lucide.X, contentDescription = label, onClick = onClick, enabled = enabled): " +
        "it takes an ImageVector (Lucide is included) instead of a lambda that draws a glyph.",
)
@Composable
public fun IconButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (Color) -> Unit,
) {
    val tokens = AppTheme
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        icon(if (enabled) tokens.foreground else tokens.disabled)
    }
}

@Deprecated("Use cx.m42.superizer.kit.Separator.", ReplaceWith("Separator(modifier = modifier)", "cx.m42.superizer.kit.Separator"))
@Composable
public fun Divider(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(1.dp).background(AppTheme.border))
}

/** A titled block of rows: the one structural unit of every list-of-unlike-things screen. */
@Deprecated(
    "Use cx.m42.superizer.kit.ItemGroup. It has no outer padding: the screen decides the gaps (Arrangement.spacedBy, Kit.spacing).",
    ReplaceWith("ItemGroup(modifier = modifier, title = title, description = hint) { content() }", "cx.m42.superizer.kit.ItemGroup"),
)
@Composable
public fun Section(
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    content: @Composable () -> Unit,
) {
    val tokens = AppTheme
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        Text(
            text = title,
            style = tokens.header,
            color = tokens.foreground,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        if (hint != null) {
            Text(
                text = hint,
                style = tokens.footnote,
                color = tokens.muted,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(tokens.radius))
                .background(tokens.background),
        ) {
            content()
        }
    }
}

/** One of a set of mutually exclusive choices; the chosen one carries the tick. */
@Deprecated(
    "Use cx.m42.superizer.kit.ChoiceItem.",
    ReplaceWith("ChoiceItem(title = label, selected = selected, onClick = onClick)", "cx.m42.superizer.kit.ChoiceItem"),
)
@Composable
public fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val tokens = AppTheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = tokens.body, color = tokens.foreground, modifier = Modifier.weight(1f))
        if (selected) CheckIcon(tokens.foreground)
    }
}

/** A row carrying a switch, with the whole row as its tap target. */
@Deprecated(
    "Use cx.m42.superizer.kit.SwitchItem.",
    ReplaceWith(
        "SwitchItem(title = label, checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled)",
        "cx.m42.superizer.kit.SwitchItem",
    ),
)
@Composable
public fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** False greys the row out and ignores taps — a switch that cannot be moved on this device. */
    enabled: Boolean = true,
) {
    val tokens = AppTheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = tokens.body,
            color = if (enabled) tokens.foreground else tokens.muted,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, label = label)
    }
}

/**
 * The switch itself: a track and a thumb, drawn rather than imported, for the same reason the icon
 * pack is hand-drawn — the library takes no Material dependency and this is the only control of
 * its kind it needs.
 */
@Deprecated(
    "Use cx.m42.superizer.kit.Switch. With onCheckedChange = null it only shows the state, as this one did; pass a callback " +
        "to make it its own tap target.",
    ReplaceWith("Switch(checked = checked, onCheckedChange = null, contentDescription = label)", "cx.m42.superizer.kit.Switch"),
)
@Composable
public fun Switch(checked: Boolean, label: String) {
    val tokens = AppTheme
    val thumbOffset by animateDpAsState(if (checked) 20.dp else 2.dp)
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(26.dp)
            .clip(CircleShape)
            .background(if (checked) tokens.primary else tokens.border)
            // The row above owns the click; this only has to say what it is showing.
            .semantics { contentDescription = label },
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = thumbOffset)
                .size(22.dp)
                .clip(CircleShape)
                .background(tokens.background),
        )
    }
}

@Deprecated("Use cx.m42.superizer.kit.ItemSeparator.", ReplaceWith("ItemSeparator()", "cx.m42.superizer.kit.ItemSeparator"))
@Composable
public fun RowDivider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(1.dp)
            .background(AppTheme.border),
    )
}

/**
 * A bordered text field with a placeholder.
 *
 * On `BasicTextField` rather than on a Material one for the reason the whole pack exists: there is
 * no Material dependency here, and a field is a border, a cursor and a text style.
 */
@Deprecated(
    "Use cx.m42.superizer.kit.Input, with cx.m42.superizer.kit.Field around it for a visible caption. minHeight has no counterpart: " +
        "use minLines, or Modifier.heightIn. password = true replaces a hand-made secret field.",
    ReplaceWith(
        "Input(value = value, onValueChange = onValueChange, modifier = modifier, placeholder = placeholder, " +
            "label = label, singleLine = singleLine, keyboardOptions = keyboardOptions, textStyle = textStyle)",
        "cx.m42.superizer.kit.Input",
    ),
)
@Composable
public fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    label: String = placeholder,
    singleLine: Boolean = true,
    minHeight: androidx.compose.ui.unit.Dp = 48.dp,
    textStyle: TextStyle? = null,
    /**
     * What the keyboard is told. A field for a secret passes `autoCorrectEnabled = false` so the
     * keyboard neither suggests over it nor learns it into its dictionary (06 §7).
     */
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val tokens = AppTheme
    val style = (textStyle ?: tokens.body).copy(color = tokens.foreground)
    // The caller's modifier goes on the *field*, not on this box: a `testTag` here would name a
    // node with no text-input semantics, and `performTextInput` would fail on it — which is
    // exactly how this comment came to exist.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, tokens.border, RoundedCornerShape(8.dp))
            .background(tokens.background)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) {
            Text(text = placeholder, style = style, color = tokens.muted)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = style,
            keyboardOptions = keyboardOptions,
            cursorBrush = SolidColor(tokens.foreground),
            modifier = modifier.fillMaxWidth().semantics { contentDescription = label },
        )
    }
}

/**
 * The bridge [AppButton]'s replacement goes through, so an IDE quick-fix keeps a button's variant
 * instead of dropping it. Once replaced, write the kit's value directly.
 */
@Deprecated("Write cx.m42.superizer.kit.ButtonVariant.<value> directly.")
public fun ButtonVariant.toKit(): cx.m42.superizer.kit.ButtonVariant = when (this) {
    ButtonVariant.Default -> cx.m42.superizer.kit.ButtonVariant.Default
    ButtonVariant.Outline -> cx.m42.superizer.kit.ButtonVariant.Outline
    ButtonVariant.Secondary -> cx.m42.superizer.kit.ButtonVariant.Secondary
    ButtonVariant.Ghost -> cx.m42.superizer.kit.ButtonVariant.Ghost
    ButtonVariant.Destructive -> cx.m42.superizer.kit.ButtonVariant.Destructive
    ButtonVariant.Accent -> cx.m42.superizer.kit.ButtonVariant.Default
}

/** As [ButtonVariant.toKit], for the size. */
@Deprecated("Write cx.m42.superizer.kit.ButtonSize.<value> directly.")
public fun ButtonSize.toKit(): cx.m42.superizer.kit.ButtonSize = when (this) {
    ButtonSize.Sm -> cx.m42.superizer.kit.ButtonSize.Sm
    ButtonSize.Default -> cx.m42.superizer.kit.ButtonSize.Default
    ButtonSize.Lg -> cx.m42.superizer.kit.ButtonSize.Lg
}
