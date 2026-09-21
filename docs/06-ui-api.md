# `ui` and `ui-theme` API reference

`cx.m42.superizer:ui-theme:0.1.0` — design tokens only, so a third-party component set can match a
host without taking on its components.
`cx.m42.superizer:ui:0.1.0` — components, the scaffold, the host screens, the shell.

An app depends on both. An app that draws with them looks like it belongs — in this host and in any
other.

---

## 1. Tokens (`cx.m42.superizer.theme`)

```kotlin
@Composable fun SuperizerTheme(tokens: Tokens = LightTokens, content: @Composable () -> Unit)

val AppTheme: Tokens              // read inside any composable: AppTheme.foreground
val LocalTokens: ProvidableCompositionLocal<Tokens>
val LightTokens: Tokens
```

`Tokens` is an `@Immutable data class`, not an object — a dark theme is a second *value*, not a
second component. The shell calls `SuperizerTheme` once at the root; an app just reads `AppTheme`.

### Colours

| Token | Light value | Use |
|---|---|---|
| `background` | `#FFFFFF` | the surface a screen is drawn on |
| `foreground` | `#09090B` | text and glyphs |
| `primary` / `primaryForeground` | `#18181B` / `#FAFAFA` | the one accent, used for chrome |
| `muted` | `#71717A` | secondary text |
| `border` | `#E4E4E7` | dividers, outlines, the switch track |
| `surface` | `#FAFAFA` | a raised block |
| `menuBackground` | `#F4F4F5` | the backdrop the drawer sits on |
| `pageBackground` | `#F2F2F7` | a page made of cards |
| `danger` / `dangerSubtle` | `#DC2626` / `#FFEBE9` | destructive |
| `success` | `#1A7F37` | |
| `warning` | `#D97706` | |
| `disabled` | `#A1A1AA` | |
| `accent` / `accentSubtle` | `#0969DA` / `#DDF4FF` | "this needs a person" — kept apart from `primary` |
| `radius` | `12.dp` | |

Colour is reserved for *state*, never for chrome. If you find yourself writing `Color(0xFF…)` in an
app, the token is missing and should be added here rather than invented there.

### Type

`title` 34 · `header` 22 · `subtitle` 20 · `body` 16 · `buttonLabel` 16 · `label` 14 · `footnote` 13
· `display` 56 (one line of digits) · `keyLabel` 26 (a keypad key) · `mono` 12 (tables and logs).

---

## 2. Widgets (`cx.m42.superizer.ui.components`)

```kotlin
@Composable fun AppButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: ButtonVariant = ButtonVariant.Default,
    size: ButtonSize = ButtonSize.Lg,
)
enum class ButtonVariant { Default, Outline, Secondary, Ghost, Destructive, Accent }
enum class ButtonSize { Sm, Default, Lg }

@Composable fun IconButton(
    onClick: () -> Unit, label: String, modifier: Modifier = Modifier,
    enabled: Boolean = true, icon: @Composable (Color) -> Unit,
)   // a 44.dp round tap target — the platform minimum; `label` becomes the content description

@Composable fun AppTextField(
    value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    placeholder: String = "", label: String = placeholder,
    singleLine: Boolean = true, minHeight: Dp = 48.dp, textStyle: TextStyle? = null,
)   // the modifier lands on the *field*, so a testTag names the thing a test types into

@Composable fun Section(title: String, modifier: Modifier = Modifier, hint: String? = null, content: @Composable () -> Unit)
@Composable fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit)
@Composable fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit)
@Composable fun Switch(checked: Boolean, label: String)      // the row owns the click
@Composable fun Divider(modifier: Modifier = Modifier)
@Composable fun RowDivider()                                  // inset, between rows of one section
```

`Section` is the one structural unit of every list-of-unlike-things screen — Settings and the
Service Menu are built out of nothing else.

---

## 3. Icons

```kotlin
@Composable fun AppIconGlyph(icon: AppIcon, tint: Color, size: Dp = IconSize, fallbackLetter: Char = '?')

@Composable fun BurgerIcon(tint: Color, size: Dp = IconSize)
@Composable fun BackIcon(...)      @Composable fun SettingsIcon(...)
@Composable fun CheckIcon(...)     @Composable fun BackspaceIcon(...)
@Composable fun PlusIcon(...)      @Composable fun AppsIcon(...)
@Composable fun QrIcon(...)        @Composable fun CalculatorIcon(...)
@Composable fun CurrencyIcon(...)
val IconSize: Dp
```

Every glyph is hand-drawn on a canvas — no icon font, no vector resources, so the three targets
cannot disagree about what a back arrow looks like.

`AppIconGlyph` is what the catalog and Home use: `Named` resolves against the pack above
(`calculator`, `currency`, `apps`, `qr`, `settings`), `Path` parses an SVG `d` string and fills it,
and anything unresolvable falls back to `Letter`. A malformed path draws the letter rather than
throwing — a bad icon must not take down the screen that lists apps.

---

## 4. The frame

```kotlin
data class MenuEntry(
    val label: String, val onClick: () -> Unit,
    val enabled: Boolean = true, val danger: Boolean = false,
)

@Composable fun AppScaffold(
    title: String,
    menu: List<MenuEntry>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    onHaptic: () -> Unit = {},
    footer: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
)

val LocalDrawerDismiss: ProvidableCompositionLocal<() -> Unit>
```

A fixed top bar with a burger on the left and the content below it. The drawer does not slide over
the screen — it lies underneath; opening pushes the whole screen right and scales it to 0.94, so the
two read as one stack of cards. Drag from the left 24 dp, or tap the burger; a fling over 400 px/s
settles either way.

The scaffold pads for `WindowInsets.safeDrawing`, so nothing lands under the status bar or the home
indicator. It also registers the innermost back handler, which is why an open drawer answers back
before the shell does.

**An app never calls this** — the shell wraps every app in one. It is public because a host screen
outside the shell might need the same frame.

```kotlin
@Composable expect fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit)
```

Android intercepts the gesture and the button; desktop and the browser do nothing. Handlers nest,
and the nesting *is* the navigation policy: drawer → the open app's own handler inside `Content()`
→ the shell. `enabled = false` unregisters rather than swallowing.

---

## 5. The shell and the host screens

```kotlin
@Composable fun SuperizerShell(superizer: Superizer, modifier: Modifier = Modifier)

sealed interface ShellDestination {
    data object Home; data class App(val id: AppId)
    data class Catalog(val from: ShellDestination)
    data class Settings(val from: ShellDestination)
    data class Activate(val from: ShellDestination)
    data object ServiceMenu
}
```

One composable is the whole Super App. It owns where the user is, collects `superizer.commands` and
`superizer.route`, restores the last session at startup, draws the veto dialog, and wraps the open
app in a container that provides `LocalAppRuntime` and tags the root `<appId>:root`.

Startup priority: **a waiting link, then the session snapshot, then Home.** The other order would
open yesterday's screen over the notification someone just tapped.

Content is capped at 480 dp and centred on every destination. Screen changes slide: forward pushes
in from the right while the old screen recedes a third of the way left; back is the mirror.

The individual screens are public so an unusual host can compose its own shell:

```kotlin
@Composable fun HomeScreen(
    apps: List<SuperizerApp<*>>, langTag: String,
    onOpen: (AppId) -> Unit, onRemove: (AppId) -> Unit, onAdd: () -> Unit,
    modifier: Modifier = Modifier,
)
@Composable fun CatalogScreen(
    apps: List<SuperizerApp<*>>, onHome: Set<AppId>, langTag: String,
    onToggle: (AppId) -> Unit, modifier: Modifier = Modifier,
)
@Composable fun SettingsScreen(superizer: Superizer, onOpenServiceMenu: () -> Unit, modifier: Modifier = Modifier)
@Composable fun ActivateScreen(
    superizer: Superizer, onActivated: (AppId) -> Unit,
    onHostCommand: (ActivationResult.Command) -> Unit, modifier: Modifier = Modifier,
)
@Composable fun ServiceMenuScreen(superizer: Superizer, onLaunched: (AppId) -> Unit, modifier: Modifier = Modifier)
@Composable fun HostErrorScreen(onRetry: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier)
```

---

## 6. Host strings (`cx.m42.superizer.ui.i18n`)

```kotlin
interface HostStrings { val langTag: String; val home: String; /* ~50 entries */ }
val HostStringTables: Map<String, HostStrings>      // "en", "ru"
val HostStringsFallback: HostStrings
fun hostStringsFor(langTag: String): HostStrings
val LocalHostStrings: ProvidableCompositionLocal<HostStrings>
val hostStrings: HostStrings                        // @Composable shorthand for the local
```

These are the **host's** strings — the drawer, Settings, the Activate screen, the dialogs. An app
ships its own tables and never reads these, except when it deliberately wants to say "Back" the way
the host says it.

---

## 7. Test tags

Text changes with the language; tags do not. The convention is `<owner>:<element>`.

| Tag | Node |
|---|---|
| `<appId>:root` | the app container — put on by the shell, **not** by the app |
| `<appId>:<element>` | everything inside an app. Yours to choose, and to keep stable. |
| `home:tile-<appId>`, `home:add` | Home |
| `catalog:root`, `catalog:row-<appId>` | All Apps |
| `settings:section-<appId>`, `settings:build` | Settings (the block the app contributed; the build stamp) |
| `activate:promo`, `activate:qr`, `activate:apply`, `activate:error` | Activate |
| `service:root`, `service:app-<appId>`, `service:config`, `service:push`, `service:push-send`, `service:events`, `service:log`, `service:confirm-reset` | the Service Menu |
| `host:error` | the failed-launch screen |
| `shell:close-dialog` | the veto dialog (on the scrim, which owns the merged semantics) |

The drawer's controls are found by content description — `"Open menu"`, `"Back"`, `"Settings"`,
`"Activate"` — because they are icon buttons and a label is what a screen reader needs anyway.
