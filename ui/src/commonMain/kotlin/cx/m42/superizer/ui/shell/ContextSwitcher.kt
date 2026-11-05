package cx.m42.superizer.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import cx.m42.superizer.ContextPort
import cx.m42.superizer.HostContext
import cx.m42.superizer.HostScreen
import cx.m42.superizer.theme.AppTheme
import cx.m42.superizer.ui.components.CheckIcon
import cx.m42.superizer.ui.components.Divider
import cx.m42.superizer.ui.components.Monogram
import cx.m42.superizer.ui.components.PlusIcon
import cx.m42.superizer.ui.components.SettingsIcon
import cx.m42.superizer.ui.platform.SystemBackHandler

/*
 * The context switcher (RestoApp D6): a row at the top of the drawer saying where you are, and a
 * sheet from the bottom listing where else you could be — ChatGPT's workspaces, GitHub's accounts.
 *
 * The row opens the sheet over the drawer rather than closing the drawer first: looking at the list
 * and changing your mind should leave you where you were. Picking something closes both.
 */

/** The drawer's first row: the current context, a tap to see the others, a long press to go back. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ContextRow(
    port: ContextPort,
    current: HostContext?,
    langTag: String,
    onOpen: () -> Unit,
    onBack: (HostContext) -> Unit,
) {
    val tokens = AppTheme
    val title = current?.title ?: port.title(langTag)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(tokens.radius))
            .combinedClickable(
                role = Role.Button,
                onClick = onOpen,
                onLongClick = { port.previous()?.let(onBack) },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("context:row"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Monogram(title = title, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = tokens.body.copy(fontWeight = FontWeight.SemiBold),
                color = tokens.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            current?.subtitle?.let {
                Text(
                    text = it,
                    style = tokens.label,
                    color = tokens.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        UpDownIcon(tokens.muted)
    }
}

/**
 * The list of contexts, from the bottom, over a scrim: a tap on one switches, the "+" row adds, the
 * last row manages. Back, Escape and a tap on the scrim close it and change nothing.
 */
@Composable
internal fun BoxScope.ContextSheet(
    visible: Boolean,
    port: ContextPort,
    contexts: List<HostContext>,
    current: HostContext?,
    langTag: String,
    onDismiss: () -> Unit,
    onPick: (HostContext) -> Unit,
    onOpen: (HostScreen) -> Unit,
) {
    val tokens = AppTheme

    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(tokens.foreground.copy(alpha = 0.35f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
    }

    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
    ) {
        SystemBackHandler(enabled = visible, onBack = onDismiss)
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(tokens.background)
                // Swallows taps on the panel itself, which would otherwise reach the scrim.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                        onDismiss()
                        true
                    } else {
                        false
                    }
                }
                .focusRequester(focus)
                .focusable()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 12.dp)
                .testTag("context:sheet"),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(tokens.border),
            )
            Text(
                text = port.title(langTag),
                style = tokens.header,
                color = tokens.foreground,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )

            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                contexts.forEach { context ->
                    ContextItem(
                        context = context,
                        selected = context.id == current?.id,
                        onClick = { onPick(context) },
                    )
                }
            }

            val add = port.addScreen
            val manage = port.manageScreen
            if (add != null || manage != null) {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
            }
            add?.let { screen ->
                ActionRow(label = screen.title(langTag), tag = "context:add", onClick = { onOpen(screen) }) { tint ->
                    PlusIcon(tint)
                }
            }
            manage?.let { screen ->
                ActionRow(label = screen.title(langTag), tag = "context:manage", onClick = { onOpen(screen) }) { tint ->
                    SettingsIcon(tint)
                }
            }
        }
    }
}

@Composable
private fun ContextItem(context: HostContext, selected: Boolean, onClick: () -> Unit) {
    val tokens = AppTheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(tokens.radius))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("context:item-${context.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Monogram(title = context.title, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = context.title,
                style = if (selected) tokens.body.copy(fontWeight = FontWeight.SemiBold) else tokens.body,
                color = tokens.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            context.subtitle?.let {
                Text(
                    text = it,
                    style = tokens.label,
                    color = tokens.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            CheckIcon(tokens.foreground)
        }
    }
}

@Composable
private fun ActionRow(label: String, tag: String, onClick: () -> Unit, icon: @Composable (Color) -> Unit) {
    val tokens = AppTheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(tokens.radius))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) { icon(tokens.foreground) }
        Spacer(Modifier.width(12.dp))
        Text(text = label, style = tokens.body, color = tokens.foreground)
    }
}

/** ⌃ over ⌄: "there is a list behind this", the way a workspace picker says it. */
@Composable
private fun UpDownIcon(tint: Color) {
    Canvas(modifier = Modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()
        fun line(a: Offset, b: Offset) = drawLine(tint, a, b, strokeWidth = stroke, cap = StrokeCap.Round)
        line(Offset(w * 0.28f, h * 0.40f), Offset(w * 0.5f, h * 0.18f))
        line(Offset(w * 0.5f, h * 0.18f), Offset(w * 0.72f, h * 0.40f))
        line(Offset(w * 0.28f, h * 0.60f), Offset(w * 0.5f, h * 0.82f))
        line(Offset(w * 0.5f, h * 0.82f), Offset(w * 0.72f, h * 0.60f))
    }
}
