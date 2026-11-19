package cx.m42.superizer.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** One section of an app: what [AppSections] switches between. [id] is what the app saves. */
@Immutable
public data class SectionItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    /** A short mark: «3» open receipts. Null — none. */
    val badge: String? = null,
)

/** The sections an [AppSections] holds, for the [PageHeader] under it to draw as tabs. */
internal class AppSectionsState(val items: List<SectionItem>, val selected: String, val onSelect: (String) -> Unit)

internal val LocalAppSections = staticCompositionLocalOf<AppSectionsState?> { null }

/**
 * The navigation *inside* one app — a till's receipts and catalogue, marketing's summary, offers and
 * codes. The host's own navigation (the drawer, the rail, the sidebar) moves between apps; this moves
 * between the sections of the app that is open, and the host knows nothing about it.
 *
 * On a phone it is a line of tabs at the top of the app's area — the shell's own bar is at the bottom, and
 * two bars one over the other read as one menu of the wrong things (owner, 2026-11-19). In a wider window the
 * sections are tabs under the page's title: the [PageHeader] inside [content] draws them by itself,
 * so a section's page needs nothing but its header. A page without a [PageHeader] gets no tabs in a
 * wide window — every section's page has one. The selected section is the app's state to save, not a
 * route of the host's.
 */
@Composable
public fun AppSections(
    items: List<SectionItem>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAppSections provides AppSectionsState(items, selected, onSelect)) {
        if (LocalWindowClass.current == WindowClass.Compact) {
            Column(modifier.fillMaxSize()) {
                // The tabs are the navigation here; a header deeper down must not draw them as well.
                SectionTopTabs(items, selected, onSelect)
                Box(Modifier.fillMaxWidth().weight(1f)) { content() }
            }
        } else {
            Box(modifier.fillMaxSize()) { content() }
        }
    }
}

/** The tabs of [AppSections] as the page header draws them. */
@Composable
internal fun SectionTabs(state: AppSectionsState) {
    val index = state.items.indexOfFirst { it.id == state.selected }.coerceAtLeast(0)
    LineTabs(
        items = state.items.map { if (it.badge != null) "${it.label} · ${it.badge}" else it.label },
        selected = index,
        onSelect = { state.onSelect(state.items[it].id) },
        modifier = Modifier.testTag("sections:tabs"),
    )
}

/** The sections on a phone: underlined tabs over the page, scrolling sideways when they do not fit. */
@Composable
private fun SectionTopTabs(items: List<SectionItem>, selected: String, onSelect: (String) -> Unit) {
    val border = Kit.colors.border
    val brand = Kit.palette.brand
    Row(
        Modifier
            .fillMaxWidth()
            .background(Kit.colors.background)
            .drawBehind { drawLine(border, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1.dp.toPx()) }
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp)
            .testTag("sections:bar"),
    ) {
        items.forEach { item ->
            val on = item.id == selected
            Row(
                Modifier
                    .height(46.dp)
                    .drawBehind {
                        if (on) drawRect(brand, Offset(8.dp.toPx(), size.height - 2.dp.toPx()), androidx.compose.ui.geometry.Size(size.width - 16.dp.toPx(), 2.dp.toPx()))
                    }
                    .kitClickable(hover = false, role = Role.Tab) { onSelect(item.id) }
                    .padding(horizontal = 12.dp)
                    .testTag("sections:${item.id}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    item.label,
                    style = Kit.textStyles.label.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (on) Kit.colors.foreground else Kit.colors.mutedForeground,
                    maxLines = 1,
                )
                if (item.badge != null) {
                    Box(
                        Modifier.clip(CircleShape).background(if (on) Kit.palette.brand else Kit.colors.muted).padding(horizontal = 6.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            item.badge,
                            style = Kit.textStyles.caption.copy(fontWeight = FontWeight.SemiBold),
                            color = if (on) Kit.palette.onBrand else Kit.colors.mutedForeground,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
