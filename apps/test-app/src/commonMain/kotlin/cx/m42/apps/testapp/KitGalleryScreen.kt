@file:OptIn(ExperimentalLayoutApi::class)

package cx.m42.apps.testapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowRight
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.ClipboardList
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Ellipsis
import com.composables.icons.lucide.Inbox
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.LayoutDashboard
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mail
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Receipt
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.SearchX
import com.composables.icons.lucide.Server
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Store
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.TriangleAlert
import com.composables.icons.lucide.Wallet
import com.composables.icons.lucide.Zap
import cx.m42.superizer.kit.ActionBar
import cx.m42.superizer.kit.Alert
import cx.m42.superizer.kit.AlertVariant
import cx.m42.superizer.kit.AppSections
import cx.m42.superizer.kit.Avatar
import cx.m42.superizer.kit.AvatarShape
import cx.m42.superizer.kit.Badge
import cx.m42.superizer.kit.BadgeVariant
import cx.m42.superizer.kit.BarChart
import cx.m42.superizer.kit.Button
import cx.m42.superizer.kit.ButtonSize
import cx.m42.superizer.kit.ButtonVariant
import cx.m42.superizer.kit.Card
import cx.m42.superizer.kit.Checkbox
import cx.m42.superizer.kit.Chip
import cx.m42.superizer.kit.ChoiceItem
import cx.m42.superizer.kit.ChoiceTile
import cx.m42.superizer.kit.ConfirmDialog
import cx.m42.superizer.kit.CopyValue
import cx.m42.superizer.kit.Crumb
import cx.m42.superizer.kit.DataTable
import cx.m42.superizer.kit.Dialog
import cx.m42.superizer.kit.DialogActions
import cx.m42.superizer.kit.EmptyState
import cx.m42.superizer.kit.FactRow
import cx.m42.superizer.kit.Field
import cx.m42.superizer.kit.Grid
import cx.m42.superizer.kit.Icon
import cx.m42.superizer.kit.IconButton
import cx.m42.superizer.kit.IconTile
import cx.m42.superizer.kit.Input
import cx.m42.superizer.kit.Item
import cx.m42.superizer.kit.ItemGroup
import cx.m42.superizer.kit.ItemSeparator
import cx.m42.superizer.kit.Kbd
import cx.m42.superizer.kit.Kit
import cx.m42.superizer.kit.KitAccent
import cx.m42.superizer.kit.KitTheme
import cx.m42.superizer.kit.LineTabs
import cx.m42.superizer.kit.LocalWindowClass
import cx.m42.superizer.kit.Menu
import cx.m42.superizer.kit.MenuItem
import cx.m42.superizer.kit.MenuLabel
import cx.m42.superizer.kit.MenuSeparator
import cx.m42.superizer.kit.Meter
import cx.m42.superizer.kit.NameMark
import cx.m42.superizer.kit.Notice
import cx.m42.superizer.kit.Page
import cx.m42.superizer.kit.PageHeader
import cx.m42.superizer.kit.Panel
import cx.m42.superizer.kit.Pill
import cx.m42.superizer.kit.Progress
import cx.m42.superizer.kit.QtyStepper
import cx.m42.superizer.kit.Ring
import cx.m42.superizer.kit.SectionItem
import cx.m42.superizer.kit.SectionTitle
import cx.m42.superizer.kit.Select
import cx.m42.superizer.kit.SelectOption
import cx.m42.superizer.kit.Separator
import cx.m42.superizer.kit.SeparatorOrientation
import cx.m42.superizer.kit.ShareBar
import cx.m42.superizer.kit.SideSheet
import cx.m42.superizer.kit.Skeleton
import cx.m42.superizer.kit.Sparkline
import cx.m42.superizer.kit.Spinner
import cx.m42.superizer.kit.Stat
import cx.m42.superizer.kit.StatusBadge
import cx.m42.superizer.kit.StatusDot
import cx.m42.superizer.kit.StepIndicator
import cx.m42.superizer.kit.Switch
import cx.m42.superizer.kit.SwitchItem
import cx.m42.superizer.kit.TableColumn
import cx.m42.superizer.kit.Tabs
import cx.m42.superizer.kit.Terminal
import cx.m42.superizer.kit.Text
import cx.m42.superizer.kit.TextVariant
import cx.m42.superizer.kit.ToastHost
import cx.m42.superizer.kit.Tone
import cx.m42.superizer.kit.WindowClass
import cx.m42.superizer.kit.byWindow
import cx.m42.superizer.kit.color
import cx.m42.superizer.kit.meterTone
import cx.m42.superizer.kit.palette
import cx.m42.superizer.kit.rememberToastState
import cx.m42.superizer.kit.textStyles

/** One row of the table demo: a venue with a status, a count and a sum. */
private class Venue(val name: String, val status: String, val tone: Tone, val orders: Int, val revenue: String, val on: Boolean = true)

/**
 * The gallery itself: one [Page], one [Panel] per family, each component in its states. Overlays open
 * from the Overlays panel and from the header's `⋯`; their tags are `kit:open-*`.
 */
@Composable
internal fun KitGalleryScreen(s: KitStrings) {
    val toasts = rememberToastState()
    var dialog by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Page(modifier = Modifier.testTag("kit:page")) {
            PageHeader(
                title = s.title,
                description = s.description,
                crumbs = listOf(Crumb(s.crumbService, onClick = {}), Crumb(s.title, null)),
                badge = { Pill(LocalWindowClass.current.name, Tone.Brand) },
                meta = {
                    StatusBadge(if (Kit.colors.isDark) s.dark else s.light, Tone.Neutral)
                    Text(s.metaHint, variant = TextVariant.Muted, maxLines = 1)
                },
                actions = {
                    Button(s.newThing, { toasts.show(s.toastSaved, s.toastSavedDetail) }, leadingIcon = Lucide.Plus, modifier = Modifier.testTag("kit:toast"))
                    Box {
                        IconButton(Lucide.Ellipsis, s.more, { menu = true }, variant = ButtonVariant.Outline, modifier = Modifier.testTag("kit:open-menu"))
                        Menu(menu, { menu = false }) {
                            MenuLabel(s.menuLabel)
                            MenuItem(s.edit, { menu = false }, icon = Lucide.Pencil)
                            MenuItem(s.copy, { menu = false }, icon = Lucide.Copy, hint = "⌘C")
                            MenuItem(s.selected, { menu = false }, selected = true)
                            MenuItem(s.disabled, {}, enabled = false)
                            MenuSeparator()
                            MenuItem(s.delete, { menu = false; delete = true }, icon = Lucide.Trash2, destructive = true)
                        }
                    }
                },
            )

            AccentsPanel(s)
            ButtonsPanel(s)
            FormsPanel(s)
            SelectionPanel(s)
            StatusPanel(s)
            FeedbackPanel(s)
            SurfacesPanel(s)
            ChartsPanel(s)
            LayoutPanel(s)

            Panel(title = s.overlays, description = s.overlaysHint) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(s.openDialog, { dialog = true }, variant = ButtonVariant.Outline, modifier = Modifier.testTag("kit:open-dialog"))
                    Button(s.openConfirm, { confirm = true }, variant = ButtonVariant.Outline, modifier = Modifier.testTag("kit:open-confirm"))
                    Button(s.openDelete, { delete = true }, variant = ButtonVariant.Destructive, modifier = Modifier.testTag("kit:open-delete"))
                    Button(s.openSheet, { sheet = true }, variant = ButtonVariant.Outline, modifier = Modifier.testTag("kit:open-sheet"))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(s.toastSuccess, { toasts.show(s.toastSaved, s.toastSavedDetail, Tone.Success) }, variant = ButtonVariant.Secondary)
                    Button(s.toastWarn, { toasts.show(s.toastWarnTitle, tone = Tone.Warn) }, variant = ButtonVariant.Secondary)
                    Button(s.toastDanger, { toasts.show(s.toastDangerTitle, s.toastDangerDetail, Tone.Danger) }, variant = ButtonVariant.Secondary)
                }
            }

            Terminal(s.terminalTitle, live = true, liveLabel = s.live) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    s.logLines.forEach { Text(it, variant = TextVariant.Mono) }
                }
            }
        }

        ToastHost(toasts)
    }

    if (dialog) {
        var name by remember { mutableStateOf("") }
        Dialog(
            onDismiss = { dialog = false },
            title = s.dialogTitle,
            description = s.dialogText,
            icon = Lucide.Store,
            closeLabel = s.close,
            footer = { DialogActions(s.save, { dialog = false }, s.cancel, { dialog = false }, enabled = name.isNotBlank()) },
        ) {
            Field(s.nameLabel, description = s.nameHint) { Input(name, { name = it }, placeholder = s.namePlaceholder) }
        }
    }
    if (confirm) {
        ConfirmDialog(s.confirmTitle, s.confirmText, s.confirmAction, s.cancel, { confirm = false }, { confirm = false }, icon = Lucide.Info)
    }
    if (delete) {
        ConfirmDialog(
            s.deleteTitle,
            s.deleteText,
            s.delete,
            s.cancel,
            { delete = false },
            { delete = false },
            destructive = true,
            icon = Lucide.Trash2,
            typeToConfirm = s.venues[0],
            typeToConfirmLabel = s.typeToConfirm,
        )
    }
    if (sheet) {
        SideSheet(
            { sheet = false },
            s.sheetTitle,
            s.close,
            description = s.sheetText,
            footer = { DialogActions(s.save, { sheet = false }, s.cancel, { sheet = false }) },
        ) {
            FactRow(s.factPlan, s.planStandard)
            FactRow(s.factId, "svc_pizzanorth_7f3a", mono = true, copyable = true)
            Field(s.nameLabel) { Input(s.venues[0], {}) }
            SwitchItem(s.switchTitle, true, {})
        }
    }
}

// ───────────────────────────── accents and palette ─────────────────────────────

@Composable
private fun AccentsPanel(s: KitStrings) {
    Panel(title = s.accents, description = s.accentsHint) {
        val p = Kit.palette
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Swatch(p.brand, "brand")
            Swatch(p.onBrand, "onBrand")
            Swatch(p.brandSoft, "brandSoft")
            Swatch(p.onBrandSoft, "onBrandSoft")
            Swatch(p.brandBorder, "brandBorder")
            Swatch(p.canvas, "canvas")
            Swatch(p.sidebar, "sidebar")
            Swatch(Kit.colors.primary, "primary")
            Swatch(Kit.colors.ring, "ring")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("chart", variant = TextVariant.Muted)
            p.chart.forEach { Box(Modifier.size(20.dp).clip(Kit.shapes.full).background(it)) }
        }
        val dark = Kit.colors.isDark
        val typography = Kit.typography
        Grid(byWindow(2, 2, 3), gap = 12.dp) {
            KitAccent.entries.forEach { accent ->
                // The same sample under each accent, in the current light/dark set.
                KitTheme(dark = dark, accent = accent, typography = typography) {
                    Panel(title = accent.name, gap = 12.dp, action = { Box(Modifier.size(16.dp).clip(Kit.shapes.full).background(accent.swatch(dark))) }) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            Button(s.primary, {}, size = ButtonSize.Sm)
                            Switch(true, {}, contentDescription = accent.name)
                            Checkbox(true, {}, contentDescription = accent.name)
                            Chip(s.chipAll, true, {}, count = 3)
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            StatusBadge(s.statusActive, Tone.Brand)
                            Pill(s.planPro, Tone.Brand)
                            IconTile(Lucide.Zap, size = 28.dp)
                        }
                        Meter(0.6f)
                    }
                }
            }
        }
    }
}

@Composable
private fun Swatch(color: Color, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(40.dp).clip(Kit.shapes.md).background(color).border(1.dp, Kit.colors.border, Kit.shapes.md))
        Text(label, style = Kit.textStyles.caption, color = Kit.colors.mutedForeground)
    }
}

// ───────────────────────────── buttons ─────────────────────────────

@Composable
private fun ButtonsPanel(s: KitStrings) {
    Panel(title = s.buttons, description = s.buttonsHint) {
        Sub(s.variants) {
            ButtonVariant.entries.forEach { Button(it.name, {}, variant = it) }
        }
        Sub(s.sizes) {
            ButtonSize.entries.forEach { Button(it.name, {}, size = it, leadingIcon = Lucide.Plus) }
        }
        Sub(s.disabled) {
            ButtonVariant.entries.forEach { Button(it.name, {}, variant = it, enabled = false) }
        }
        Sub(s.loading) {
            Button(s.save, {}, loading = true)
            Button(s.save, {}, variant = ButtonVariant.Outline, loading = true)
            Button(s.delete, {}, variant = ButtonVariant.Destructive, loading = true)
        }
        Sub(s.iconButtons) {
            IconButton(Lucide.Settings, s.settings, {})
            IconButton(Lucide.Pencil, s.edit, {}, variant = ButtonVariant.Outline)
            IconButton(Lucide.Plus, s.newThing, {}, variant = ButtonVariant.Default)
            IconButton(Lucide.Trash2, s.delete, {}, variant = ButtonVariant.Destructive)
            IconButton(Lucide.Search, s.search, {}, variant = ButtonVariant.Secondary, size = ButtonSize.Sm)
            IconButton(Lucide.Settings, s.disabled, {}, variant = ButtonVariant.Outline, enabled = false)
            Button({}, variant = ButtonVariant.Secondary) {
                Icon(Lucide.Mail, null, size = 16.dp)
                Text(s.customContent, variant = TextVariant.Small)
            }
        }
    }
}

/** A caption and a wrapping row of samples under it. */
@Composable
private fun Sub(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = Kit.textStyles.overline, color = Kit.colors.mutedForeground)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) { content() }
    }
}

// ───────────────────────────── forms ─────────────────────────────

@Composable
private fun FormsPanel(s: KitStrings) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("anna@example") }
    var on by remember { mutableStateOf(true) }
    var checked by remember { mutableStateOf(true) }
    var choice by remember { mutableStateOf(0) }
    Panel(title = s.forms, description = s.formsHint) {
        Grid(byWindow(1, 2, 3), gap = 16.dp) {
            Field(s.nameLabel, description = s.nameHint) { Input(name, { name = it }, placeholder = s.namePlaceholder) }
            Field(s.emailLabel) { Input(email, { email = it }, leadingIcon = Lucide.Mail) }
            Field(s.emailLabel, error = s.emailError) { Input("anna@", {}, isInvalid = true, leadingIcon = Lucide.Mail) }
            Field(s.disabled) { Input(s.venues[0], {}, enabled = false) }
            Field(s.readOnly) { Input("svc_pizzanorth_7f3a", {}, readOnly = true, trailing = { Kbd("⌘K") }) }
            Field(s.password) { Input("secret-2026", {}, password = true) }
        }
        Field(s.comment, description = s.commentHint) { Input("", {}, placeholder = s.commentPlaceholder, singleLine = false, minLines = 3) }
        Sub(s.toggles) {
            Switch(on, { on = it }, contentDescription = s.switchTitle)
            Switch(!on, { on = !it }, contentDescription = s.switchTitle)
            Switch(true, null, enabled = false, contentDescription = s.disabled)
            Switch(false, null, enabled = false, contentDescription = s.disabled)
            Separator(Modifier.height(24.dp), orientation = SeparatorOrientation.Vertical)
            Checkbox(checked, { checked = it }, contentDescription = s.switchTitle)
            Checkbox(!checked, { checked = !it }, contentDescription = s.switchTitle)
            Checkbox(true, null, enabled = false, contentDescription = s.disabled)
            Checkbox(false, null, enabled = false, contentDescription = s.disabled)
        }
        ItemGroup(title = s.items, description = s.itemsHint) {
            SwitchItem(s.switchTitle, on, { on = it }, description = s.switchHint)
            ItemSeparator()
            SwitchItem(s.disabled, true, {}, enabled = false)
            ItemSeparator()
            s.choices.forEachIndexed { i, label ->
                ChoiceItem(label, choice == i, { choice = i }, description = if (i == 0) s.choiceHint else null)
                ItemSeparator()
            }
            Item(s.openRow, onClick = {}, leading = { Icon(Lucide.Store, null) }, trailing = { Icon(Lucide.ChevronRight, null, size = 16.dp) })
            ItemSeparator()
            Item(s.disabled, description = s.disabledHint, enabled = false, onClick = {})
        }
    }
}

// ───────────────────────────── selection ─────────────────────────────

@Composable
private fun SelectionPanel(s: KitStrings) {
    var chip by remember { mutableStateOf(0) }
    var line by remember { mutableStateOf(0) }
    var tab by remember { mutableStateOf(1) }
    var plan by remember { mutableStateOf("standard") }
    var sort by remember { mutableStateOf("new") }
    var tile by remember { mutableStateOf(1) }
    var qty by remember { mutableStateOf(2) }
    val plans = listOf(
        SelectOption("start", s.planStart, s.planStartHint),
        SelectOption("standard", s.planStandard, s.planStandardHint),
        SelectOption("pro", s.planPro, s.planProHint),
    )
    Panel(title = s.selection, description = s.selectionHint) {
        Sub(s.chips) {
            s.chipLabels.forEachIndexed { i, label ->
                Chip(label, chip == i, { chip = i }, count = 6 - i * 2, dot = listOf(null, Tone.Success, Tone.Danger, Tone.Muted)[i])
            }
        }
        LineTabs(s.tabLabels, line, { line = it })
        Tabs(s.tabLabels.take(3), tab, { tab = it })
        Grid(byWindow(1, 2, 2)) {
            Field(s.plan) { Select(plan, plans, { plan = it }, title = s.plan, modifier = Modifier.testTag("kit:select")) }
            Field(s.sort) {
                Select(sort, listOf(SelectOption("new", s.sortNew), SelectOption("old", s.sortOld)), { sort = it }, title = s.sort, leadingIcon = Lucide.ArrowRight, small = true)
            }
        }
        Grid(byWindow(1, 3, 3), gap = 12.dp) {
            plans.forEachIndexed { i, option ->
                ChoiceTile(tile == i, { tile = i }) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(option.label, variant = TextVariant.Large)
                        Text(option.hint.orEmpty(), variant = TextVariant.Muted)
                    }
                }
            }
        }
        Sub(s.steps) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StepIndicator(s.stepLabels, 0)
                StepIndicator(s.stepLabels, 1)
                StepIndicator(s.stepLabels, s.stepLabels.size)
            }
        }
        Sub(s.quantity) {
            QtyStepper(qty, { qty = it }, min = 0, max = 5, removeLabel = s.delete)
            QtyStepper(0, {}, min = 0, removeLabel = s.delete)
            QtyStepper(5, {}, min = 0, max = 5)
        }
    }
}

// ───────────────────────────── status ─────────────────────────────

@Composable
private fun StatusPanel(s: KitStrings) {
    Panel(title = s.status, description = s.statusHint) {
        Sub("StatusBadge") { Tone.entries.forEach { StatusBadge(it.name, it) } }
        Sub("Pill") {
            Tone.entries.forEach { Pill(it.name, it) }
            Pill(s.planPro, Tone.Brand, icon = Lucide.Zap)
        }
        Sub("StatusDot") {
            Tone.entries.forEach { tone ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(tone)
                    Text(tone.name, variant = TextVariant.Muted)
                }
            }
        }
        Sub("Badge") {
            BadgeVariant.entries.forEach { Badge(it.name, variant = it) }
            Badge("8", variant = BadgeVariant.Destructive)
            Badge(s.statusActive, variant = BadgeVariant.Success, dot = true)
        }
        Sub(s.marks) {
            Avatar("Anna Petrova")
            Avatar("Ivan Sidorov", shape = AvatarShape.Square)
            Avatar("Kim", size = 32.dp)
            Separator(Modifier.height(32.dp), orientation = SeparatorOrientation.Vertical)
            s.venues.forEach { NameMark(it, size = 36.dp) }
            NameMark(s.venues[0], size = 36.dp, dimmed = true)
            Separator(Modifier.height(32.dp), orientation = SeparatorOrientation.Vertical)
            Tone.entries.forEach { IconTile(Lucide.Server, it) }
        }
    }
}

// ───────────────────────────── feedback ─────────────────────────────

@Composable
private fun FeedbackPanel(s: KitStrings) {
    Panel(title = s.feedback, description = s.feedbackHint) {
        Grid(byWindow(1, 2, 2), gap = 12.dp) {
            AlertVariant.entries.forEach { variant ->
                Alert(variant.name, description = s.alertText, variant = variant, icon = if (variant == AlertVariant.Default) Lucide.Info else Lucide.TriangleAlert)
            }
            Alert(s.alertNoIcon, description = s.alertText)
        }
        Grid(byWindow(1, 1, 2), gap = 12.dp) {
            Notice(Tone.Brand, Lucide.Zap, s.noticeTitle, s.noticeText, action = { Button(s.noticeAction, {}, size = ButtonSize.Sm) })
            Notice(Tone.Warn, Lucide.TriangleAlert, s.noticeWarnTitle, s.noticeWarnText, action = { Button(s.noticeAction, {}, size = ButtonSize.Sm, variant = ButtonVariant.Outline) })
            Notice(Tone.Danger, Lucide.TriangleAlert, s.noticeDangerTitle, s.noticeDangerText)
            Notice(Tone.Success, Lucide.Info, s.noticeOkTitle, s.noticeOkText)
        }
        Sub(s.loading) {
            Spinner(size = 16.dp)
            Spinner()
            Spinner(size = 32.dp, color = Kit.palette.brand)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Progress(0f)
            Progress(0.35f)
            Progress(0.8f, color = Kit.palette.brand)
            Progress(1f)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Skeleton(Modifier.size(40.dp), shape = Kit.shapes.full)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Skeleton(Modifier.fillMaxWidth(0.6f).height(14.dp))
                Skeleton(Modifier.fillMaxWidth(0.35f).height(12.dp))
            }
            Skeleton(Modifier.size(64.dp, 24.dp), shape = Kit.shapes.full)
        }
    }
}

// ───────────────────────────── surfaces ─────────────────────────────

@Composable
private fun SurfacesPanel(s: KitStrings) {
    SectionTitle(s.surfaces, hint = s.surfacesHint, action = { Button(s.all, {}, variant = ButtonVariant.Link) })
    Grid(byWindow(2, 4, 4)) {
        Stat(s.statBalance, s.statBalanceValue, icon = Lucide.Wallet, hint = s.statBalanceHint)
        Stat(s.statRunning, s.statRunningValue, icon = Lucide.Server, hint = s.statRunningHint, tone = Tone.Danger, onClick = {})
        Stat(s.statOrders, "37", hint = s.statOrdersHint, tone = Tone.Success, extra = { Sparkline(Series, Modifier.fillMaxWidth().height(32.dp)) })
        Stat(s.statDisk, "83%", tone = Tone.Warn, hint = s.statDiskHint, extra = { Meter(0.83f) })
    }
    Grid(byWindow(1, 2, 2)) {
        Panel(title = s.facts, icon = Lucide.ClipboardList, gap = 10.dp) {
            FactRow(s.factPlan, s.planStandard)
            FactRow(s.factId, "svc_pizzanorth_7f3a", mono = true, copyable = true)
            FactRow(s.factStatus, trailing = { StatusBadge(s.statusActive, Tone.Success) })
            FactRow(s.factEmpty)
            CopyValue("https://pizza-north.example/admin", copyLabel = s.copy, copiedLabel = s.copied)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(s.shortcut, variant = TextVariant.Muted)
                Kbd("⌘")
                Kbd("K")
                Kbd("Esc")
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card {
                Text(s.cardTitle, variant = TextVariant.Large)
                Text(s.cardText, variant = TextVariant.Muted)
            }
            Card(onClick = {}) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    NameMark(s.venues[1])
                    Column(Modifier.weight(1f)) {
                        Text(s.venues[1], variant = TextVariant.Large)
                        Text(s.cardClickable, variant = TextVariant.Muted)
                    }
                    Icon(Lucide.ChevronRight, null, size = 16.dp)
                }
            }
            Panel(title = s.panelTitle, description = s.panelText, icon = Lucide.Store, action = { Button(s.edit, {}, size = ButtonSize.Sm, variant = ButtonVariant.Outline) }) {
                Separator()
                Text(s.cardText, variant = TextVariant.Muted)
            }
        }
    }
    Panel(title = s.type) {
        TextVariant.entries.forEach { Text("${it.name} — ${s.typeSample}", variant = it, maxLines = 1) }
        Separator()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val roles = Kit.textStyles
            listOf(
                "caption" to roles.caption, "overline" to roles.overline, "label" to roles.label, "number" to roles.number,
                "metric" to roles.metric, "display" to roles.display, "monoSmall" to roles.monoSmall,
            ).forEach { (name, style) -> Text(name, style = style) }
        }
    }
}

// ───────────────────────────── charts ─────────────────────────────

private val Series = listOf(4f, 6f, 5f, 8f, 7f, 9f, 12f, 10f, 13f, 12f, 15f, 17f)
private val Daily = listOf(1240f, 980f, 1520f, 1710f, 1330f, 2210f, 1890f)

@Composable
private fun ChartsPanel(s: KitStrings) {
    Grid(byWindow(1, 2, 2)) {
        Panel(title = s.charts, description = s.chartsHint) {
            BarChart(Daily, s.days, height = 140.dp, unit = " ₽")
            Sparkline(Series, Modifier.fillMaxWidth().height(48.dp))
            Sparkline(Series.reversed(), Modifier.fillMaxWidth().height(32.dp), color = Tone.Danger.color(), fill = false)
        }
        Panel(title = s.limits, description = s.limitsHint) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(0.42f, 0.8f, 0.95f).forEach { v ->
                    Ring(v, color = meterTone(v).color()) { Text("${(v * 100).toInt()}%", variant = TextVariant.Small) }
                }
                Ring(0.61f, size = 44.dp, stroke = 5.dp)
            }
            listOf(0.4f, 0.8f, 0.96f).forEach { v ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Meter(v, Modifier.weight(1f))
                    Text("${(v * 100).toInt()}% · ${meterTone(v).name}", variant = TextVariant.Muted)
                }
            }
            ShareBar(listOf(5f, 3f, 2f, 1f, 1f, 0.5f))
        }
    }
}

// ───────────────────────────── layout ─────────────────────────────

@Composable
private fun LayoutPanel(s: KitStrings) {
    val venues = listOf(
        Venue(s.venues[0], s.statusActive, Tone.Success, 37, "48 320 ₽"),
        Venue(s.venues[1], s.statusCooking, Tone.Running, 12, "15 900 ₽"),
        Venue(s.venues[2], s.statusLow, Tone.Warn, 8, "9 140 ₽"),
        Venue(s.venues[3], s.statusOff, Tone.Muted, 0, "0 ₽", on = false),
    )
    Panel(title = s.table, description = s.tableHint, flush = true) {
        if (LocalWindowClass.current == WindowClass.Compact) {
            // On a phone the same records are rows: a table does not fit there.
            venues.forEachIndexed { i, v ->
                if (i > 0) ItemSeparator()
                Item(
                    v.name,
                    description = "${v.orders} · ${v.revenue}",
                    onClick = {},
                    leading = { NameMark(v.name, size = 32.dp, dimmed = !v.on) },
                    trailing = { StatusBadge(v.status, v.tone) },
                )
            }
        } else {
            DataTable(
                venues,
                listOf(
                    TableColumn(s.colName, weight = 2f) { v ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            NameMark(v.name, size = 28.dp, dimmed = !v.on)
                            Text(v.name, variant = TextVariant.Small, maxLines = 1)
                        }
                    },
                    TableColumn(s.colStatus, weight = 1.4f) { v -> StatusBadge(v.status, v.tone) },
                    TableColumn(s.colOrders, alignEnd = true) { v -> Text(v.orders.toString(), style = Kit.textStyles.number) },
                    TableColumn(s.colRevenue, weight = 1.2f, alignEnd = true) { v -> Text(v.revenue, style = Kit.textStyles.number) },
                ),
                onRow = {},
            )
        }
    }

    // EmptyState draws its own dashed frame: straight on the page, not inside a panel.
    Grid(byWindow(1, 2, 2)) {
        EmptyState(Lucide.Inbox, s.emptyTitle, s.emptyText, action = { Button(s.newThing, {}, leadingIcon = Lucide.Plus) })
        EmptyState(Lucide.SearchX, s.noMatchTitle, s.noMatchText, action = { Button(s.reset, {}, variant = ButtonVariant.Outline) })
    }

    Panel(title = s.sections, description = s.sectionsHint, flush = true) {
        var section by remember { mutableStateOf("board") }
        val items = listOf(
            SectionItem("summary", s.sectionSummary, Lucide.LayoutDashboard),
            SectionItem("board", s.sectionBoard, Lucide.ClipboardList, badge = "8"),
            SectionItem("receipts", s.sectionReceipts, Lucide.Receipt),
            SectionItem("money", s.sectionMoney, Lucide.Wallet),
        )
        Box(Modifier.fillMaxWidth().height(byWindow(240.dp, 220.dp, 220.dp)).background(Kit.palette.canvas)) {
            AppSections(items, section, { section = it }) {
                Page(scroll = false) {
                    PageHeader(items.first { it.id == section }.label, s.sectionsPageHint)
                }
            }
        }
        ActionBar {
            Column(Modifier.weight(1f)) {
                Text(s.total, variant = TextVariant.Muted)
                Text("1 306 ₽", style = Kit.textStyles.number)
            }
            Button(s.pay, {}, size = ButtonSize.Lg)
        }
    }
}
