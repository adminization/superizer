package cx.m42.superizer.kit

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide

/**
 * A one-line (or, with `singleLine = false`, growing) text field.
 *
 * [modifier] goes on the editable node itself, not on a box around it: a `testTag` must name the node
 * that has text-input semantics, or `performTextInput` fails on it. [label] is what a screen reader
 * says for the field; the visible caption is [Field]'s job.
 *
 * [password] hides the text and turns the keyboard's suggestions and learning off, so a secret ends
 * up neither on the screen nor in the keyboard's dictionary.
 */
@Composable
public fun Input(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    label: String = placeholder,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isInvalid: Boolean = false,
    password: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    textStyle: TextStyle? = null,
) {
    val c = Kit.colors
    val shape = Kit.shapes.md
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        when {
            isInvalid -> c.destructive
            focused -> c.ring
            else -> c.input
        },
    )
    val style = (textStyle ?: Kit.typography.p).copy(color = c.foreground)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .semantics {
                if (label.isNotEmpty()) contentDescription = label
                if (isInvalid) error(label.ifEmpty { "invalid" })
            },
        enabled = enabled,
        readOnly = readOnly,
        textStyle = style,
        singleLine = singleLine,
        minLines = minLines,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = if (password) keyboardOptions.copy(autoCorrectEnabled = false) else keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interaction,
        cursorBrush = SolidColor(c.foreground),
        decorationBox = { field ->
            Row(
                Modifier
                    .defaultMinSize(minHeight = 44.dp)
                    .clip(shape)
                    .background(c.background)
                    .border(if (focused) 2.dp else 1.dp, borderColor, shape)
                    .padding(horizontal = Kit.spacing.md, vertical = 10.dp),
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(Kit.spacing.sm),
            ) {
                if (leadingIcon != null) Icon(leadingIcon, contentDescription = null, size = 16.dp, tint = c.mutedForeground)
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        // Out of the semantics: the field's text is what was typed, and a screen reader has [label].
                        Text(
                            placeholder,
                            style = style,
                            color = c.mutedForeground,
                            maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                    }
                    field()
                }
                if (trailing != null) CompositionLocalProvider(LocalContentColor provides c.mutedForeground) { trailing() }
            }
        },
    )
}

/**
 * A form field's frame: a visible [label] above the control, a [description] under it, and the
 * [error] in its place when there is one.
 */
@Composable
public fun Field(
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    error: String? = null,
    control: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, variant = TextVariant.Small)
        control()
        when {
            error != null -> Text(error, variant = TextVariant.Muted, color = Kit.colors.destructive)
            description != null -> Text(description, variant = TextVariant.Muted)
        }
    }
}

/**
 * An on/off switch. With [onCheckedChange] it is its own tap target; with `null` it only shows the
 * state, for a row that owns the tap (see [SwitchItem]).
 */
@Composable
public fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val c = Kit.colors
    val track by animateColorAsState(if (checked) c.primary else c.input)
    val offset by animateDpAsState(if (checked) 20.dp else 2.dp)
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.5f)
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                } else {
                    Modifier
                },
            )
            .semantics {
                if (contentDescription != null) this.contentDescription = contentDescription
                if (onCheckedChange == null) stateDescription = if (checked) "on" else "off"
            }
            .width(44.dp)
            .height(26.dp)
            .clip(Kit.shapes.full)
            .background(track),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = offset)
                .size(22.dp)
                .clip(Kit.shapes.full)
                .background(c.background),
        )
    }
}

/** A checkbox; like [Switch], `null` for [onCheckedChange] leaves the tap to the row around it. */
@Composable
public fun Checkbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val c = Kit.colors
    val shape = Kit.shapes.sm
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.5f)
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
                } else {
                    Modifier
                },
            )
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription }
            .size(20.dp)
            .clip(shape)
            .background(if (checked) c.primary else c.background)
            .border(1.dp, if (checked) c.primary else c.input, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Lucide.Check, contentDescription = null, size = 14.dp, tint = c.onPrimary)
    }
}
