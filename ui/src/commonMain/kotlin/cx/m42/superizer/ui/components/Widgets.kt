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
public enum class ButtonVariant { Default, Outline, Secondary, Ghost, Destructive, Accent }

/** [Lg] is the page-sized button; [Sm] is the one that fits inside a row. */
public enum class ButtonSize { Sm, Default, Lg }

/**
 * A button on the Compose Unstyled primitive, styled to the host's monochrome look — the one place
 * a button's colours are decided, so no screen has to pick them.
 */
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

@Composable
public fun Divider(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(1.dp).background(AppTheme.border))
}

/** A titled block of rows: the one structural unit of every list-of-unlike-things screen. */
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
@Composable
public fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val tokens = AppTheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = tokens.body, color = tokens.foreground, modifier = Modifier.weight(1f))
        Switch(checked = checked, label = label)
    }
}

/**
 * The switch itself: a track and a thumb, drawn rather than imported, for the same reason the icon
 * pack is hand-drawn — the library takes no Material dependency and this is the only control of
 * its kind it needs.
 */
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
            cursorBrush = SolidColor(tokens.foreground),
            modifier = modifier.fillMaxWidth().semantics { contentDescription = label },
        )
    }
}
