# Building UI with `ui-kit`

The one page to read before drawing a screen — in an app, in a host's own screen, or in a project
that is not a Superizer app at all. [06](06-ui-api.md#0-the-kit-cxm42superizerkit) is the reference
(every component and parameter); this page is the *how* and the *why*: where the kit comes from,
how a screen is put together from it, what stays out of it, and how a new component gets in.

Module `cx.m42.superizer:ui-kit`, package `cx.m42.superizer.kit`, since 0.10.0.
Source: [`ui-kit/src/commonMain/kotlin/cx/m42/superizer/kit/`](../ui-kit/src/commonMain/kotlin/cx/m42/superizer/kit/).

---

## 1. Where the kit comes from

The kit is [shadcn/ui](https://ui.shadcn.com)'s design language written for Compose Multiplatform:
its component names (`Button`, `Badge`, `Alert`, `Field`, `Item`, `Tabs`, `Skeleton`…), its variants
(`Default`, `Secondary`, `Outline`, `Ghost`, `Destructive`, `Link`), its colour pairs
(`card`/`onCard`, `muted`/`mutedForeground`), its radius scale derived from one `--radius`, and
Tailwind's spacing steps (4, 8, 12, 16, 24, 32 dp).

Two Compose ports of shadcn/ui were tried side by side on the same five screens before the kit was
written (the prototype lives next to restoapp, `prototype-rikka/`, modules `:composeApp` and
`:shadcnApp`):

| Upstream | Version tried | License | What the kit took from it |
|---|---|---|---|
| [RikkaUI](https://github.com/rainxchzed/RikkaUi) (`dev.rikkaui`) | 0.3.0 | Apache-2.0 | the base: `compose.foundation` only, the component set and their look |
| [shadcn-multiplatform](https://github.com/jershell/shadcn-multiplatform) (`com.github.jershell`) | 1.0.4-dev | MIT | details of the look where it was closer to shadcn/ui |
| [Lucide](https://lucide.dev) via [`com.composables:icons-lucide-cmp`](https://github.com/composablehorizons/compose-icons) | — | ISC | the icon set, exported as plain `ImageVector`s |

Neither library is a dependency. The code was brought in and rewritten to Superizer's rules, for
three reasons:

- **No third-party type in the public API.** A host pins Superizer's ABI (`ui-kit/api/`); it cannot
  pin someone else's 0.x library.
- **One Compose.** RikkaUI and shadcn-multiplatform each pull their own stack (shadcn-multiplatform
  stands on compose-unstyled); the kit stands on `compose.foundation` and `compose.ui`, which every
  host already has.
- **Superizer's greys.** `LightKitColors` uses `LightTokens`' own values, so a kit screen sits next
  to the shell without a seam.

When you need a component the kit does not have, look in that order: shadcn/ui (what it should be
called and look like), then RikkaUI and shadcn-multiplatform (how someone already did it in
Compose). §5 says how to bring it in.

## 2. Getting it

An app or a host that depends on `cx.m42.superizer:ui` already has the kit: `ui` exports it. A
project that wants only the components — no shell, no contract — takes the kit alone; it depends
on nothing of Superizer's:

```toml
# gradle/libs.versions.toml
superizer-ui-kit = { module = "cx.m42.superizer:ui-kit", version.ref = "superizer" }
```

```kotlin
import cx.m42.superizer.kit.*
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus     // one import per icon
```

Repository and credentials: [Getting started](01-getting-started.md).

## 3. The theme

| Where you draw | What gives the theme |
|---|---|
| inside an app (`AppInstance.Content()`), a Settings section, any host screen the shell shows | nothing to do: `SuperizerShell` wraps everything in `HostTheme(mode)` — the person's light/dark choice from Settings goes to both `AppTheme` and the kit |
| a host's own screen outside `SuperizerShell` (a login before the shell, a splash) | wrap it in `HostTheme(mode)` (`cx.m42.superizer.ui.shell`), so it never disagrees with the shell |
| a project without Superizer's `ui` | `KitTheme(dark = isSystemInDarkTheme()) { … }` once at the root; without it the kit draws light with the default font |

Inside, read the theme, never invent it:

```kotlin
Kit.colors.mutedForeground     // a colour, light or dark as the theme says
Kit.palette.brandSoft          // the accent's soft fill (0.11): a selected item, a chosen option
Kit.spacing.lg                 // 16 dp
Kit.shapes.xl                  // the radius a card uses
Kit.typography.h4              // a TextStyle, when Text's variants are not enough
Kit.textStyles.caption         // a role the ten steps do not name (0.11): caption, label, metric…
LocalWindowClass.current       // Compact / Medium / Expanded (0.11)
```

- **Colour is for state, not chrome.** The only hues are `destructive`, `success`, `warning` and
  `info` — and `info` means "this needs a person" — plus **the accent**.
- **The accent is the person's** (0.11). `KitAccent` — Graphite (the kit's near-black, the look
  before accents), Violet, Blue, Green, Orange, Pink — is picked in the host's Settings when the host
  offers it (`Superizer.build { accent("violet") }`), and becomes `primary` (the main button, a switch
  that is on) and `Kit.palette` (`brand`, `brandSoft`, `onBrandSoft`, `brandBorder`, `chart`, and the
  `canvas`/`sidebar` surfaces). An app never names a hue: it uses the palette, and looks right in all six.
- **Tones, not colours, for state** (0.11): `Tone` (`Neutral`, `Running`, `Success`, `Warn`, `Danger`,
  `Muted`, `Brand`) with `color()`, `textColor()`, `softColor()`. A status is `StatusBadge(label, tone)`;
  the app keeps its own dictionary from the server's value to a label and a tone.
- **No `Color(0xFF…)` in an app.** If a colour is missing, it is missing from the kit: add it (§5),
  with a light and a dark value.
- **On the web** Compose has no system fonts: pass a family with Cyrillic to
  `kitTypography(fontFamily)`. Inside the shell it reaches the apps through
  `SuperizerShell(…, layout, typography)` (0.11); a project without the shell gives it to `KitTheme`.

### The window (0.11)

`WindowClass` comes from the width an app is given, never from the platform: `Compact` < 600 dp ≤
`Medium` < 1000 dp ≤ `Expanded`. The shell provides it for the app's own area — inside a phone's
column on a desktop an app is `Compact`, as it should be. Read it with `LocalWindowClass.current` or
`byWindow(compact, medium, expanded)`; never measure widths in a screen. Outside any provider (a test)
it is `Compact`; a test of a wide layout provides the class itself.

## 4. Building a screen

### The shape of a screen

A screen is a scrolling column with the page gutter, made of groups and cards. The shell draws the
top bar and the way back; the screen draws only its content.

```kotlin
@Composable
internal fun ProfileScreen(state: ProfileState, onSave: () -> Unit, onLogout: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Kit.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(Kit.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Kit.spacing.xl),
    ) {
        Text(state.title, variant = TextVariant.H3)

        if (state.offline) {
            Alert(
                title = strings.offlineTitle,
                description = strings.offlineHint,
                variant = AlertVariant.Warning,
                icon = Lucide.WifiOff,
            )
        }

        Field(label = strings.name, error = state.nameError) {
            Input(
                value = state.name,
                onValueChange = state::onName,
                isInvalid = state.nameError != null,
                modifier = Modifier.testTag("profile:name"),   // lands on the editable node
            )
        }

        ItemGroup(title = strings.notifications) {
            SwitchItem(strings.orders, checked = state.orders, onCheckedChange = state::onOrders)
            ItemSeparator()
            SwitchItem(strings.promos, checked = state.promos, onCheckedChange = state::onPromos)
        }

        ItemGroup(title = strings.language) {
            state.languages.forEachIndexed { i, lang ->
                if (i > 0) ItemSeparator()
                ChoiceItem(lang.title, selected = lang == state.language, onClick = { state.onLanguage(lang) })
            }
        }

        Button(
            strings.save,
            onClick = onSave,
            size = ButtonSize.Lg,
            loading = state.saving,
            modifier = Modifier.fillMaxWidth().testTag("profile:save"),
        )
        Button(strings.logout, onClick = onLogout, variant = ButtonVariant.Ghost, modifier = Modifier.fillMaxWidth())
    }
}
```

### Which component for what

| You want | Use |
|---|---|
| a heading, body text, a hint, a number in a table | `Text(…, variant = H1…H4 / P / Lead / Large / Small / Muted / Mono)` |
| the main action of a screen | `Button(…, size = ButtonSize.Lg)`, full width; one `Default` per screen, the rest `Secondary`/`Outline`/`Ghost` |
| a dangerous action | `ButtonVariant.Destructive` — and ask first |
| an icon you can tap | `IconButton(Lucide.X, contentDescription = …, onClick)` (44 dp target) |
| a text field | `Input` inside a `Field` (visible label, hint, error) |
| a settings list | `ItemGroup` of `Item` / `SwitchItem` / `ChoiceItem`, `ItemSeparator()` between rows |
| a row that opens something | `Item(title, onClick = …, trailing = { Icon(Lucide.ChevronRight, null) })` |
| a block of content, tappable or not | `Card(onClick = …) { … }` |
| a status next to a title | `Badge(text, variant = Success / Warning / Destructive / Info, dot = true)` |
| a message that stays until its cause is gone | `Alert` — not a toast |
| a filter over a list | `Tabs(items, selectedIndex, onSelect)` |
| a person or a place | `Avatar(name, shape = Circle / Square)` |
| loading | `Skeleton` in the shape of what is coming; `Spinner` only where there is no shape; `Button(loading = true)` for a pressed action |
| a page: gutters, max width, rhythm (0.11) | `Page(PageWidth.Narrow / Default / Wide) { … }` — the only place they are set |
| the top of a page (0.11) | `PageHeader(title, description, crumbs, leading, badge, meta, actions)` — crumbs where there is no host bar, actions beside or under the title by width |
| a block of a page (0.11) | `Panel(title, description, action, flush)`; columns of blocks — `Grid(byWindow(1, 2, 3))` |
| a table on a wide window (0.11) | `DataTable(rows, columns = listOf(TableColumn(…)), onRow)`; on a phone the same records as rows |
| a status (0.11) | `StatusBadge(label, tone)`; a label that is not a status — `Pill`; a counter — `Badge` |
| filters, record tabs, a choice of options (0.11) | `Chip`, `LineTabs`, `ChoiceTile`; steps of a flow — `StepIndicator`; a quantity — `QtyStepper` |
| facts of a record (0.11) | `FactRow(label, value, mono, copyable)`, `CopyValue` |
| nothing to show (0.11) | `EmptyState(icon, title, description, action)` — "nothing yet" with an action, "nothing matches" with a reset |
| a number with its name (0.11) | `Stat(label, value, hint)`; a trend — `Sparkline`, `BarChart`; a share of a limit — `Ring`, `Meter`; shares — `ShareBar` |
| a tinted message with one thing to do (0.11) | `Notice(tone, icon, title, text, action)`; a log — `Terminal` |
| a question, a short form, a menu, a choice (0.11) | `ConfirmDialog`, `Dialog` + `DialogActions`, `SideSheet`, `Menu`/`MenuItem`, `Select` — bottom sheets on a phone by themselves |
| "done" that leaves by itself (0.11) | `ToastState.show(…)` with one `ToastHost` at the app's root |
| the sections of one app (0.11) | `AppSections(items, selected, onSelect) { … }` — a bottom bar on a phone, tabs under the `PageHeader` wider |
| a sticky bar of a phone's form (0.11) | `ActionBar { … }` |

### Rules the host and its tests rely on

- **Name clashes (0.11).** The kit's package has `Page`, `Panel`, `Dialog`, `Menu`, `Chip`, `Tone`,
  `EmptyState`, `Notice`… Import what you use by name; a star import of the kit next to another star
  import with the same names (`cx.m42.superizer.ssh.*` has a `Chip`) is ambiguous.

- **Test tags, not text.** Tag what a test touches `"<appId>:<element>"`; the text changes with the
  language, the tag does not ([07](07-testing.md)). `Input`'s modifier lands on the editable node.
- **Strings come from your string tables**, never hard-coded in one language.
- **One way back.** Inside the shell the host draws the back arrow. An app that draws its own «Back»
  for a nested screen draws it only when `!hostDrawsBack` (`cx.m42.superizer.ui.platform`).
- **Text and icons inside a component take its colour by themselves** (`LocalContentColor`): inside
  a `Button(onClick) { Icon(…); Text(…) }` do not pass a colour.

### What stays out of the kit

The kit is generic building blocks. Something that means something only in your product — an order
card, a schema editor, your app's own list row with a chevron and a counter — is **your** component,
in your module, *built from* kit components and `Kit.*` values. restoapp keeps its own in
`render/Components.kt`. If two products end up writing the same thing, that is the moment to move
it into the kit (§5).

### Moving off the old widgets

`AppButton`, `AppTextField`, `Section`, `ToggleRow`, `ChoiceRow`, `Divider`, `Monogram` and the
glyphs in `cx.m42.superizer.ui.components` are deprecated; the IDE's quick fix does the one-to-one
cases. The full table: [06 → Moving to ui-kit](06-ui-api.md#moving-to-ui-kit).

## 5. Adding a component to the kit

1. **Find the original.** The name, the variants and the look come from
   [shadcn/ui](https://ui.shadcn.com/docs/components). A Compose starting point comes from RikkaUI or
   shadcn-multiplatform (§1). Do not add either as a dependency; bring the code in.
2. **Rewrite it to the kit's rules:**
   - `compose.foundation` and `compose.ui` only — no Material, no compose-unstyled, no upstream type.
   - Colours, gaps, radii and type from `Kit.*` only; a component that paints a fill provides the
     matching `on…` colour as `LocalContentColor`.
   - Icons are `ImageVector` parameters (Lucide is what callers get for free, not a requirement).
   - `modifier: Modifier = Modifier` first after the required parameters; a tap target is at least
     44 dp; disabled is `alpha(0.5f)` and ignores taps; pressed uses `Modifier.pressable`, not a ripple.
   - Semantics: a `Role`, a `contentDescription` for anything without text.
   - Light **and** dark: any new colour goes into `KitColors` with a value in both `LightKitColors` and
     `DarkKitColors`.
   - KDoc on every public declaration (the module is `explicitApi()`).
3. **Put it in the file of its family** in `ui-kit/src/commonMain/kotlin/cx/m42/superizer/kit/`:
   `Button.kt`, `Display.kt` (card, badge, avatar, separator), `Feedback.kt` (alert, progress,
   spinner, skeleton), `Forms.kt`, `Items.kt`, `Tabs.kt`, `Text.kt`, `KitTheme.kt`.
4. **Test it** in `ui-kit/src/desktopTest/kotlin/cx/m42/superizer/kit/KitComponentsTest.kt`: that it
   reacts when enabled and not when disabled, its semantics, its tag.
5. **Only add to the public API, never change it.** Run `./gradlew :ui-kit:updateLegacyAbi` and commit
   the diff of `ui-kit/api/`; that diff is the review. `checkLegacyAbi` fails the build if you forget.
6. **Document it:** a row in [06 §0](06-ui-api.md#0-the-kit-cxm42superizerkit), a row in §4's
   "Which component for what" above if it answers a new need, and a line in `CHANGELOG.md`.
7. **Credit the upstream.** If the code was adapted from RikkaUI (Apache-2.0), say so in the
   component's KDoc (`Adapted from RikkaUI's X, Apache-2.0`) and keep §1's table current.

## 6. Where to look next time

| Question | Where |
|---|---|
| How do I build a screen, what's allowed, how do I add a component | this page, `docs/12-ui-kit.md` |
| What a finished screen must look like on a phone, a tablet and a desktop | [13: UI guidelines](13-ui-guidelines.md) |
| Every component and parameter | [06 §0](06-ui-api.md#0-the-kit-cxm42superizerkit) |
| Old widget → new component | [06 → Moving to ui-kit](06-ui-api.md#moving-to-ui-kit) |
| The source | `ui-kit/src/commonMain/kotlin/cx/m42/superizer/kit/` (and `ui-kit/README.md`, which points here) |
| Real screens drawn with the kit | `ui/src/commonMain/kotlin/cx/m42/superizer/ui/screens/` (Settings, Activate, Catalog) |
