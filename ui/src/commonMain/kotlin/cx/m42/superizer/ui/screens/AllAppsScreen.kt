package cx.m42.superizer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import cx.m42.superizer.ui.components.AppIconGlyph
import cx.m42.superizer.ui.i18n.hostStrings

/**
 * Home: every app this device may open, as tiles two to a row, grouped by the category each app
 * names for itself.
 *
 * It renders whatever the registry hands it and knows nothing about any particular app — which is
 * the point. A fourth app appears here because it was registered, not because this file learned
 * about it (§24).
 *
 * Hidden apps are filtered out by the caller (D8): what is not unlocked is not listed, and there
 * is no "locked" tile to tap, because a tile that says "you cannot have this" is an advertisement.
 */
@Composable
public fun AllAppsScreen(
    apps: List<SuperizerApp<*>>,
    langTag: String,
    onOpen: (AppId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = AppTheme
    if (apps.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = hostStrings.noApps, style = tokens.body, color = tokens.muted)
        }
        return
    }

    // Grouped in the order the apps were registered, so the host decides what comes first by the
    // order it registers in — no sort key to invent, no priority field to argue about.
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
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Keeps a lone tile on the left rather than stretched across the row.
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun AppTile(
    app: SuperizerApp<*>,
    langTag: String,
    onOpen: (AppId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = AppTheme
    val title = app.metadata.title.resolve(langTag)
    Column(
        modifier = modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(tokens.radius))
            .background(tokens.background)
            .clickable(role = Role.Button) { onOpen(app.id) }
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
