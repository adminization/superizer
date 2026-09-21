package cx.m42.superizer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import cx.m42.superizer.app.AppId
import cx.m42.superizer.app.SuperizerApp
import cx.m42.superizer.theme.AppTheme
import cx.m42.superizer.ui.components.AppIconGlyph
import cx.m42.superizer.ui.components.CheckIcon
import cx.m42.superizer.ui.components.RowDivider
import cx.m42.superizer.ui.components.Section
import cx.m42.superizer.ui.i18n.hostStrings

/**
 * All apps: everything this device may open, one row each, with a tick on the ones already on
 * Home (D48). A tap toggles — this is the one place an app is added, and the second place it is
 * taken away.
 *
 * Hidden apps are filtered out by the caller (D8) until an activation unlocks them; from then on
 * they are rows like any other, because being unlocked is what "available" means.
 */
@Composable
public fun CatalogScreen(
    apps: List<SuperizerApp<*>>,
    onHome: Set<AppId>,
    langTag: String,
    onToggle: (AppId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = AppTheme
    if (apps.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = hostStrings.noApps, style = tokens.body, color = tokens.muted)
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.pageBackground)
            .verticalScroll(rememberScrollState())
            .testTag("catalog:root")
            .padding(vertical = 16.dp),
    ) {
        Text(
            text = hostStrings.catalogHint,
            style = tokens.footnote,
            color = tokens.muted,
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp),
        )
        // Registration order, grouped the way Home groups: the host decides what comes first.
        apps.groupBy { it.metadata.category }.forEach { (category, inGroup) ->
            Section(title = category ?: hostStrings.ungrouped) {
                inGroup.forEachIndexed { index, app ->
                    if (index > 0) RowDivider()
                    CatalogRow(
                        app = app,
                        langTag = langTag,
                        selected = app.id in onHome,
                        onClick = { onToggle(app.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogRow(
    app: SuperizerApp<*>,
    langTag: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = AppTheme
    val title = app.metadata.title.resolve(langTag)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics { this.selected = selected }
            .testTag("catalog:row-${app.id.value}")
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconGlyph(
            icon = app.metadata.icon,
            tint = tokens.foreground,
            size = 28.dp,
            fallbackLetter = title.firstOrNull() ?: '?',
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = tokens.body, color = tokens.foreground)
            app.metadata.description?.resolve(langTag)?.takeIf { it.isNotBlank() }?.let { description ->
                Text(text = description, style = tokens.footnote, color = tokens.muted)
            }
            if (selected) Text(text = hostStrings.onHome, style = tokens.footnote, color = tokens.muted)
        }
        if (selected) CheckIcon(tokens.foreground)
    }
}
