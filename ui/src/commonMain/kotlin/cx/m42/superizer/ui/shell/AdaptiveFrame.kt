package cx.m42.superizer.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.ChevronsUpDown
import com.composables.icons.lucide.Lucide
import cx.m42.superizer.app.AppIcon
import cx.m42.superizer.kit.Icon
import cx.m42.superizer.kit.Kit
import cx.m42.superizer.kit.LocalWindowClass
import cx.m42.superizer.kit.Text
import cx.m42.superizer.kit.TextVariant
import cx.m42.superizer.kit.WindowClass
import cx.m42.superizer.kit.kitClickable
import cx.m42.superizer.kit.palette
import cx.m42.superizer.kit.textStyles
import cx.m42.superizer.ui.components.AppIconGlyph
import cx.m42.superizer.ui.components.AppScaffold
import cx.m42.superizer.ui.components.LocalDrawerDismiss
import cx.m42.superizer.ui.components.MenuEntry
import cx.m42.superizer.ui.i18n.hostStrings

/**
 * How [SuperizerShell] draws its navigation.
 *
 * [Drawer] is the shell as it always was: a top bar with a burger, a drawer under the screen, every
 * app in a phone's column — in a window of any width. [Adaptive] keeps exactly that on a phone, and
 * in a wider window lays the same menu out as a rail (a tablet) or a sidebar (a desktop), and lets an
 * app that says it can (`AppLayout.width`) have the whole width. The window class decides, never the
 * platform: a desktop window dragged narrow is a phone.
 */
public enum class ShellLayout { Drawer, Adaptive }

/** One destination of the menu as the rail and the sidebar draw it: the drawer's entry and more. */
internal class NavItem(
    val label: String,
    val icon: AppIcon?,
    val group: String?,
    val badge: String?,
    val selected: Boolean,
    val enabled: Boolean,
    val tag: String,
    val onClick: () -> Unit,
)

/** What the shell hands its frames: the layout, the menu as items, and the context's mark for the rail. */
internal class ShellNav(
    val layout: ShellLayout,
    val items: List<NavItem>,
    /** The context switcher as the rail draws it — a mark that opens the picker. Null: no contexts. */
    val railHeader: (@Composable () -> Unit)?,
    /** The host's own chrome and what its slots may do; null — the shell's own drawer, rail and sidebar. */
    val chrome: ShellChrome? = null,
    val scope: ShellScope? = null,
    /** «Ещё» — where the items that did not fit the bar or the rail are. */
    val more: NavItem? = null,
    /** The context in force, for the phone's bar: its name, or the host's when there are no contexts. */
    val contextTitle: String = "",
)

/** What a slot of [ShellChrome] may ask the shell for. */
public class ShellScope internal constructor(
    public val openHome: () -> Unit,
    public val openApp: (cx.m42.superizer.app.AppId) -> Unit,
    public val openSettings: () -> Unit,
    public val openScreen: (cx.m42.superizer.HostScreen) -> Unit,
    /** The list of contexts — the same sheet the context row opens. */
    public val openContexts: () -> Unit,
    public val openCatalog: () -> Unit,
)

/**
 * A host's own chrome over [ShellLayout.Adaptive]: a console rather than a launcher. On a phone the
 * burger and the drawer give way to a bottom bar — Home, the first [barItems] − 1 apps and «Ещё» — and
 * a top bar that names the context; a tablet's rail takes [railItems] and «Ещё»; a desktop's sidebar
 * lists everything. The slots are the host's: what stands under the context row, at the foot of the
 * sidebar and of the rail, at the end of the phone's bar, under the apps of «Ещё» — and Home itself.
 * Every slot is optional; the apps, their order, their marks and the contexts stay Superizer's.
 */
public class ShellChrome(
    public val barItems: Int = 4,
    public val railItems: Int = 6,
    /** Home instead of the tiles: the whole width, its own page header on a desktop. */
    public val home: (@Composable ShellScope.() -> Unit)? = null,
    /** The sidebar, between the context row and the list: a point selector, a search field. */
    public val navHeader: (@Composable ShellScope.() -> Unit)? = null,
    /** The foot of the sidebar instead of the shell's own (settings, activation, the build). */
    public val navFooter: (@Composable ShellScope.() -> Unit)? = null,
    /** The foot of the rail. */
    public val railFooter: (@Composable ShellScope.() -> Unit)? = null,
    /** The end of the phone's top bar; `root` — the bar names the context, not a way back. */
    public val barTrailing: (@Composable ShellScope.(root: Boolean) -> Unit)? = null,
    /** «Ещё», under the apps that did not fit. */
    public val more: (@Composable ShellScope.() -> Unit)? = null,
    public val moreTitle: (langTag: String) -> String = { if (it.startsWith("ru")) "Ещё" else "More" },
    /** What Home is called in the menu when the host's Home is not «Главная» — an account's «Обзор». */
    public val homeTitle: ((langTag: String) -> String)? = null,
    /** And its icon; the house without it. */
    public val homeIcon: cx.m42.superizer.app.AppIcon? = null,
    /**
     * A strip over every screen of the context, under the phone's top bar and over the pane wider — «money runs
     * out». It draws itself only when it has something to say; nothing — no strip.
     */
    public val notice: (@Composable ShellScope.() -> Unit)? = null,
)

internal val LocalShellNav = staticCompositionLocalOf { ShellNav(ShellLayout.Drawer, emptyList(), null) }

/**
 * Every screen of the shell sits in this: [AppScaffold] where the drawer is the navigation (a phone,
 * or a shell that is not adaptive), the rail or the sidebar otherwise. [bar] false — on a desktop an
 * adaptive app heads its own page, and a second title above it would be one too many.
 */
@Composable
internal fun ShellFrame(
    title: String,
    menu: List<MenuEntry>,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    onSwipeBack: (() -> Unit)? = null,
    onHaptic: () -> Unit = {},
    footer: @Composable () -> Unit = {},
    header: (@Composable () -> Unit)? = null,
    fullscreen: Boolean = false,
    bar: Boolean = true,
    upTitle: String? = null,
    /** A destination of the bar itself (Home, an app at its first screen, «Ещё»): the phone's bar names the context. */
    root: Boolean = false,
    content: @Composable () -> Unit,
) {
    val nav = LocalShellNav.current
    val window = LocalWindowClass.current
    val chrome = nav.chrome.takeIf { nav.layout == ShellLayout.Adaptive && !fullscreen && nav.scope != null }
    if (chrome != null) {
        val scope = nav.scope!!
        if (window == WindowClass.Compact) {
            cx.m42.superizer.ui.components.SwipeBackFrame(onSwipeBack) {
                Column(Modifier.fillMaxSize().background(Kit.palette.canvas).windowInsetsPadding(WindowInsets.safeDrawing)) {
                    PhoneBar(nav, chrome, scope, title, onBack.takeIf { !root }, upTitle)
                    chrome.notice?.let { scope.it() }
                    Box(Modifier.fillMaxWidth().weight(1f)) { content() }
                    BottomBar(barOf(nav, chrome.barItems))
                }
            }
            return
        }
        Row(Modifier.fillMaxSize().background(Kit.palette.canvas).windowInsetsPadding(WindowInsets.safeDrawing)) {
            if (window == WindowClass.Medium) {
                Rail(ShellNav(nav.layout, barOf(nav, chrome.railItems), nav.railHeader), footer, chrome.railFooter?.let { slot -> { scope.slot() } })
            } else {
                Sidebar(nav, header, chrome.navFooter?.let { slot -> { scope.slot() } } ?: footer, chrome.navHeader?.let { slot -> { scope.slot() } }, line = chrome.navFooter == null)
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                chrome.notice?.let { scope.it() }
                if (bar) PaneBar(title, subtitle, onBack.takeIf { !root }, upTitle)
                Box(Modifier.fillMaxWidth().weight(1f)) { content() }
            }
        }
        return
    }
    if (nav.layout == ShellLayout.Drawer || fullscreen) {
        AppScaffold(
            title = title, menu = menu, subtitle = subtitle, onBack = onBack, onSwipeBack = onSwipeBack,
            onHaptic = onHaptic, footer = footer, header = header, fullscreen = fullscreen,
        ) { content() }
        return
    }
    if (window == WindowClass.Compact) {
        // The drawer of an adaptive shell lists the same items as the rail and the sidebar — icons,
        // groups and marks — drawn in the header slot; `MenuEntry` stays what it was for other hosts.
        AppScaffold(
            title = title, menu = emptyList(), subtitle = subtitle, onBack = onBack, onSwipeBack = onSwipeBack,
            onHaptic = onHaptic, footer = footer, fullscreen = false,
            header = {
                Column {
                    header?.invoke()
                    DrawerItems(nav.items)
                }
            },
        ) { content() }
        return
    }
    Row(Modifier.fillMaxSize().background(Kit.palette.canvas).windowInsetsPadding(WindowInsets.safeDrawing)) {
        if (window == WindowClass.Medium) Rail(nav, footer) else Sidebar(nav, header, footer)
        Column(Modifier.weight(1f).fillMaxHeight()) {
            if (bar) PaneBar(title, subtitle, onBack, upTitle)
            Box(Modifier.fillMaxWidth().weight(1f)) { content() }
        }
    }
}

private fun Modifier.endLine(color: Color) = drawBehind {
    val w = 1.dp.toPx()
    drawLine(color, Offset(size.width - w / 2, 0f), Offset(size.width - w / 2, size.height), w)
}

private fun Modifier.bottomLine(color: Color) = drawBehind {
    val w = 1.dp.toPx()
    drawLine(color, Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), w)
}

/** The bar of a pane: back with where it leads, then the title. No burger — the menu is on screen. */
@Composable
private fun PaneBar(title: String, subtitle: String?, onBack: (() -> Unit)?, upTitle: String?) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).background(Kit.palette.canvas).bottomLine(Kit.colors.border).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onBack != null) {
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).kitClickable(onClick = onBack).padding(horizontal = 8.dp, vertical = 8.dp).testTag("shell:back"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Lucide.ChevronLeft, contentDescription = hostStrings.backAction, size = 18.dp)
                if (upTitle != null) Text(upTitle, style = Kit.textStyles.label, maxLines = 1)
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(title, variant = TextVariant.Large, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, variant = TextVariant.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A desktop's navigation: the drawer's menu standing open, with icons, groups and marks. */
@Composable
private fun Sidebar(nav: ShellNav, header: (@Composable () -> Unit)?, footer: @Composable () -> Unit, under: (@Composable () -> Unit)? = null, line: Boolean = true) {
    Column(Modifier.width(264.dp).fillMaxHeight().background(Kit.palette.sidebar).endLine(Kit.colors.border).testTag("shell:sidebar")) {
        CompositionLocalProvider(LocalDrawerDismiss provides {}) {
            if (header != null) header()
            if (under != null) under()
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                var group: String? = null
                nav.items.forEach { item ->
                    if (item.group != group) {
                        group = item.group
                        if (group != null) Text(group.uppercase(), Modifier.padding(start = 10.dp, top = 14.dp, bottom = 4.dp), style = Kit.textStyles.overline, color = Kit.colors.mutedForeground)
                    }
                    SideItem(item)
                }
            }
            if (line) Box(Modifier.fillMaxWidth().height(1.dp).background(Kit.colors.border))
            footer()
        }
    }
}

/** The sidebar's list inside the phone's drawer: a tap closes the drawer before it navigates. */
@Composable
private fun DrawerItems(items: List<NavItem>) {
    val dismiss = LocalDrawerDismiss.current
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        var group: String? = null
        items.forEach { item ->
            if (item.group != group) {
                group = item.group
                if (group != null) Text(group.uppercase(), Modifier.padding(start = 10.dp, top = 14.dp, bottom = 4.dp), style = Kit.textStyles.overline, color = Kit.colors.mutedForeground)
            }
            SideItem(NavItem(item.label, item.icon, item.group, item.badge, item.selected, item.enabled, item.tag) {
                dismiss()
                item.onClick()
            })
        }
    }
}

@Composable
private fun SideItem(item: NavItem) {
    val on = item.selected
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (on) Kit.palette.brandSoft else Color.Transparent)
            .kitClickable(enabled = item.enabled || on, hover = !on, onClick = item.onClick)
            .padding(horizontal = 10.dp)
            .testTag(item.tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val tint = if (on) Kit.palette.onBrandSoft else Kit.colors.mutedForeground
        if (item.icon != null) AppIconGlyph(item.icon, tint, size = 18.dp)
        Text(
            item.label,
            Modifier.weight(1f),
            style = Kit.textStyles.label.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
            color = if (on) Kit.palette.onBrandSoft else Kit.colors.foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.badge != null) Mark(item.badge, on)
    }
}

@Composable
private fun Mark(text: String, strong: Boolean) {
    Box(
        Modifier.height(20.dp).clip(CircleShape).background(if (strong) Kit.palette.brand else Kit.colors.secondary).padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Kit.textStyles.caption.copy(fontWeight = FontWeight.SemiBold), color = if (strong) Kit.palette.onBrand else Kit.colors.foreground, maxLines = 1)
    }
}

/** A tablet's navigation: the same items as icons over short labels, the context's mark on top. */
@Composable
private fun Rail(nav: ShellNav, footer: @Composable () -> Unit, foot: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.width(84.dp).fillMaxHeight().background(Kit.palette.sidebar).endLine(Kit.colors.border).padding(vertical = 12.dp).testTag("shell:rail"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        nav.railHeader?.let {
            it()
            Spacer(Modifier.height(8.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            nav.items.forEach { RailItem(it) }
        }
        if (foot != null) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) { foot() } else RailFooter(footer)
    }
}

/** The first [count] items and «Ещё» when there are more; everything when one more would fit anyway. */
private fun barOf(nav: ShellNav, count: Int): List<NavItem> {
    val more = nav.more ?: return nav.items
    if (nav.items.size <= count + 1) return nav.items
    val shown = nav.items.take(count)
    // «Ещё» is lit for whatever is behind it: its own page, or a destination that did not fit.
    val behind = more.selected || nav.items.drop(count).any { it.selected }
    return shown + NavItem(more.label, more.icon, null, null, behind, more.enabled || !more.selected, more.tag, more.onClick)
}

/** The items that are not in the bar (a phone) or the rail (a tablet) — what «Ещё» lists. */
internal fun ShellNav.overflow(window: WindowClass): List<NavItem> {
    val count = when (window) {
        WindowClass.Compact -> chrome?.barItems
        WindowClass.Medium -> chrome?.railItems
        else -> null
    } ?: return emptyList()
    return if (items.size <= count + 1) emptyList() else items.drop(count)
}

/** A phone's top bar under a chrome: the context and its list, or the way back with where it leads. */
@Composable
private fun PhoneBar(nav: ShellNav, chrome: ShellChrome, scope: ShellScope, title: String, onBack: (() -> Unit)?, upTitle: String?) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(start = if (onBack != null) 4.dp else 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The name takes what the end of the bar leaves: the point and the person are never pushed out by it.
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
        if (onBack != null) {
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).kitClickable(onClick = onBack).padding(horizontal = 10.dp, vertical = 10.dp).testTag("shell:back"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Lucide.ArrowLeft, contentDescription = hostStrings.backAction, size = 20.dp)
                Text(upTitle ?: title, style = Kit.textStyles.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).kitClickable(onClick = scope.openContexts).padding(4.dp).testTag("context:row"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ContextMark(nav.contextTitle, 30.dp)
                Text(nav.contextTitle, Modifier.weight(1f, fill = false), variant = TextVariant.Large, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Lucide.ChevronsUpDown, contentDescription = null, size = 14.dp, tint = Kit.colors.mutedForeground)
            }
        }
        }
        chrome.barTrailing?.let { slot -> scope.slot(onBack == null) }
    }
}

/** A phone's navigation under a chrome: the bar's destinations, each a pill with its mark over a short name. */
@Composable
private fun BottomBar(items: List<NavItem>) {
    val line = Kit.colors.border
    Row(
        Modifier.fillMaxWidth().background(Kit.colors.card)
            .drawBehind { drawLine(line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
            .padding(horizontal = 6.dp, vertical = 6.dp).testTag("shell:bar"),
    ) {
        items.forEach { item ->
            val on = item.selected
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).kitClickable(enabled = item.enabled || on, hover = false, onClick = item.onClick).padding(vertical = 5.dp).testTag(item.tag),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Box(Modifier.size(52.dp, 28.dp).clip(CircleShape).background(if (on) Kit.palette.brandSoft else Color.Transparent), contentAlignment = Alignment.Center) {
                    AppIconGlyph(item.icon ?: AppIcon.Letter(item.label.firstOrNull() ?: '?'), if (on) Kit.palette.onBrandSoft else Kit.colors.mutedForeground, size = 19.dp)
                    if (item.badge != null) {
                        Box(Modifier.align(Alignment.TopEnd).padding(end = 4.dp).height(16.dp).clip(CircleShape).background(Kit.palette.brand).padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
                            Text(item.badge, style = Kit.textStyles.caption.copy(fontSize = Kit.textStyles.caption.fontSize * 0.85f, fontWeight = FontWeight.SemiBold), color = Kit.palette.onBrand, maxLines = 1)
                        }
                    }
                }
                Text(
                    item.label,
                    style = Kit.textStyles.caption.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (on) Kit.palette.onBrandSoft else Kit.colors.mutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** «Ещё»: the apps that did not fit, grouped as the sidebar groups them, then the host's own rows. */
@Composable
internal fun MoreScreen(title: String, subtitle: String?) {
    val nav = LocalShellNav.current
    val rest = nav.overflow(LocalWindowClass.current)
    cx.m42.superizer.kit.Page(cx.m42.superizer.kit.PageWidth.Narrow, Modifier.testTag("more:root")) {
        cx.m42.superizer.kit.PageHeader(title, description = subtitle)
        rest.groupBy { it.group }.forEach { (group, items) ->
            cx.m42.superizer.kit.Panel(title = group, flush = true) {
                items.forEachIndexed { i, item ->
                    if (i > 0 || group != null) Box(Modifier.fillMaxWidth().height(1.dp).background(Kit.colors.border))
                    Row(
                        Modifier.fillMaxWidth().kitClickable(onClick = item.onClick).padding(horizontal = 16.dp, vertical = 12.dp).testTag("more:${item.tag.substringAfter("shell:nav-")}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Kit.palette.brandSoft), contentAlignment = Alignment.Center) {
                            AppIconGlyph(item.icon ?: AppIcon.Letter(item.label.firstOrNull() ?: '?'), Kit.palette.onBrandSoft, size = 17.dp)
                        }
                        Text(item.label, Modifier.weight(1f), style = Kit.textStyles.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (item.badge != null) Mark(item.badge, false)
                        Icon(Lucide.ChevronRight, contentDescription = null, size = 16.dp, tint = Kit.colors.mutedForeground)
                    }
                }
            }
        }
        val scope = nav.scope
        val more = nav.chrome?.more
        if (scope != null && more != null) scope.more()
    }
}

@Composable
private fun RailItem(item: NavItem) {
    val on = item.selected
    Column(
        Modifier.width(76.dp).clip(RoundedCornerShape(12.dp)).kitClickable(enabled = item.enabled || on, hover = false, onClick = item.onClick).padding(vertical = 5.dp).testTag(item.tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(Modifier.size(48.dp, 30.dp).clip(CircleShape).background(if (on) Kit.palette.brandSoft else Color.Transparent), contentAlignment = Alignment.Center) {
            AppIconGlyph(item.icon ?: AppIcon.Letter(item.label.firstOrNull() ?: '?'), if (on) Kit.palette.onBrandSoft else Kit.colors.mutedForeground, size = 19.dp)
            if (item.badge != null) Box(Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 6.dp).size(8.dp).clip(CircleShape).background(Kit.palette.brand))
        }
        Text(
            item.label,
            style = Kit.textStyles.caption.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
            color = if (on) Kit.palette.onBrandSoft else Kit.colors.mutedForeground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/** The footer's actions in a rail are too wide for it; it gets them stacked, without the build stamp. */
@Composable
private fun RailFooter(footer: @Composable () -> Unit) {
    // The shell's footer is a row of icon buttons and a stamp; a narrow column clips the stamp, which
    // is the right thing to lose here — it is one tap away in Settings.
    Box(Modifier.width(84.dp)) {
        CompositionLocalProvider(LocalRailFooter provides true) { footer() }
    }
}

/** True while the shell's footer is drawn in the rail, so it lays its buttons out in a column. */
internal val LocalRailFooter = staticCompositionLocalOf { false }
