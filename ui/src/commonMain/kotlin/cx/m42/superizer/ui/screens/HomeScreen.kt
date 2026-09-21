package cx.m42.superizer.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import cx.m42.superizer.app.AppId
import cx.m42.superizer.app.SuperizerApp
import cx.m42.superizer.theme.AppTheme
import cx.m42.superizer.ui.components.AppButton
import cx.m42.superizer.ui.components.AppIconGlyph
import cx.m42.superizer.ui.components.ButtonSize
import cx.m42.superizer.ui.components.ButtonVariant
import cx.m42.superizer.ui.components.PlusIcon
import cx.m42.superizer.ui.i18n.hostStrings

/**
 * Home: the apps the user chose, as tiles two to a row, grouped by the category each app names
 * for itself, and one more tile that leads to the catalog (D48).
 *
 * It renders whatever the shell hands it and knows nothing about any particular app — which is the
 * point. A fourth app appears here because the user added it from the catalog, not because this
 * file learned about it (§24).
 *
 * Every tile answers a long press with [onRemove], and it is the same gesture for every tile
 * because every tile got here by a choice: the catalog, or an activation. No confirmation — the
 * way back is one tap in the catalog, and a dialog guarding a reversible action is noise.
 *
 * Hidden apps that are not unlocked never reach [apps] (D8): a tile that says "you cannot have
 * this" is an advertisement.
 */
@Composable
public fun HomeScreen(
    apps: List<SuperizerApp<*>>,
    langTag: String,
    onOpen: (AppId) -> Unit,
    onRemove: (AppId) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = AppTheme
    if (apps.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize().background(tokens.pageBackground).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = hostStrings.homeEmpty,
                style = tokens.body,
                color = tokens.muted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            AppButton(
                text = hostStrings.addApp,
                onClick = onAdd,
                variant = ButtonVariant.Default,
                size = ButtonSize.Default,
                modifier = Modifier.testTag("home:add"),
            )
        }
        return
    }

    // Grouped in the order the apps were added, so the user decides what comes first by the order
    // they add in — no sort key to invent, no priority field to argue about.
    val groups = apps.groupBy { it.metadata.category }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.pageBackground)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
    ) {
        groups.forEach { (category, inGroup) ->
            Text(
                text = category ?: hostStrings.ungrouped,
                style = tokens.header,
                color = tokens.foreground,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            // Chunked rather than a lazy grid: the whole list is short and already inside a
            // vertical scroll, and nesting a lazy grid in one is a measurement error waiting to
            // happen.
            inGroup.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    row.forEach { app ->
                        AppTile(
                            app = app,
                            langTag = langTag,
                            onOpen = onOpen,
                            onRemove = onRemove,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Keeps a lone tile on the left rather than stretched across the row.
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        // The catalog's tile, last and on its own row, so it reads as a door rather than as an app.
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            AddTile(onAdd = onAdd, modifier = Modifier.weight(1f))
            Spacer(Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(
    app: SuperizerApp<*>,
    langTag: String,
    onOpen: (AppId) -> Unit,
    onRemove: (AppId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = AppTheme
    val title = app.metadata.title.resolve(langTag)
    Column(
        modifier = modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(tokens.radius))
            .background(tokens.background)
            .combinedClickable(
                role = Role.Button,
                onLongClickLabel = hostStrings.removeFromHome,
                onLongClick = { onRemove(app.id) },
                onClick = { onOpen(app.id) },
            )
            .testTag("home:tile-${app.id.value}")
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1.6f), contentAlignment = Alignment.Center) {
            AppIconGlyph(
                icon = app.metadata.icon,
                tint = tokens.foreground,
                size = 40.dp,
                fallbackLetter = title.firstOrNull() ?: '?',
            )
        }
        Text(
            text = title,
            style = tokens.label,
            color = tokens.foreground,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Outlined where the app tiles are filled: it is the one tile that is not an app. */
@Composable
private fun AddTile(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = AppTheme
    Column(
        modifier = modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(tokens.radius))
            .border(1.dp, tokens.border, RoundedCornerShape(tokens.radius))
            .clickable(role = Role.Button, onClick = onAdd)
            .testTag("home:add")
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1.6f), contentAlignment = Alignment.Center) {
            PlusIcon(tokens.muted, size = 40.dp)
        }
        Text(
            text = hostStrings.addApp,
            style = tokens.label,
            color = tokens.muted,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
