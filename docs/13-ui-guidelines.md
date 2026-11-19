# UI guidelines: product screens on phone, tablet and desktop

What a finished screen is. [12](12-ui-kit.md) says which component to draw with; this page says
what the screen made of them must look like and do, in a window of any width.

The rules are the admin panel's guidelines (Adminizer, `fable-ui/guidelines.md`) carried over to
Compose Multiplatform: the same templates, status dictionary, colour norm, form rules and
checklist; the web-panel parts (Inertia, CSS layers, server-side sidebar) dropped; an adaptive
layer added, because an app runs on a phone, on a tablet and in a desktop window that can be
dragged down to a phone's width.

**Marking.** **[I]** invariant: breaking it blocks review. **[R]** recommendation: you may depart
from it, saying why in the code. **[C]** the check that catches it. Every rule has a *why*; a rule
without one is not added, because a rule nobody understands is the first one broken.

> **Status (0.11).** The adaptive shell (§1) and the primitives (§3) are in Superizer now:
> `SuperizerShell(…, layout = ShellLayout.Adaptive, …)` and the page kit in `ui-kit`
> ([12](12-ui-kit.md)). They were tried first in a prototype (`prototype-rikka/cloudApp`), which is
> where the differences from the first draft come from: `Expanded` starts at 1000 dp, not 840; the
> navigation between apps stays the host's drawer (a rail and a sidebar on wider windows), and a
> bottom bar is an app's own sections (`AppSections`), never a global one.

**Not here.** A product's information architecture (which sections, which screens) — that is the
product's brief. Only what is the same for every app.

---

## 1. The window

### 1.1. Three window classes, chosen by width alone **[I]**

| Class | Window width | Typical |
|---|---|---|
| `Compact` | < 600 dp | a phone; a desktop window dragged narrow; a tablet in split view |
| `Medium` | 600–999 dp | a tablet in portrait; a small desktop window |
| `Expanded` | ≥ 1000 dp | a tablet in landscape; a desktop window |

1000 rather than Material's 840: a 264 dp sidebar leaves 576 dp of content at 840, a phone's layout
with a sidebar glued on. The class is of the area the app is given (`ui-kit`'s `WindowClassProvider`,
which the shell sets) — an app in a phone's column on a desktop is `Compact`.

```kotlin
enum class WindowClass { Compact, Medium, Expanded }
val LocalWindowClass = staticCompositionLocalOf { WindowClass.Compact }

// The shell provides it; outside a shell, once at the root:
WindowClassProvider { content() }   // measured after layout — not BoxWithConstraints (see §11)
```

- The class comes from the **current width of the window**, never from the platform or the device.
  **Why:** a desktop window can be 400 dp wide and a tablet can be in split view; `if (isDesktop)`
  is wrong in both.
- A screen reads `LocalWindowClass.current`. Raw widths (`maxWidth < …`) are read only inside the
  primitives of §3. **Why:** seven screens with seven breakpoints of their own is how a product
  gets seven layouts.
- Resizing the window across a boundary must not lose state: the selected row, the typed text and
  the scroll position survive. **Why:** on a desktop it happens by accident, mid-task.

**[C]** `grep -rn "maxWidth <\|maxWidth >" ui/screens/` finds nothing.

### 1.2. One list of destinations, three ways to draw it **[I]**

| Class | Navigation between apps (the host's) | Inside an app | Top of the screen |
|---|---|---|---|
| `Compact` | the drawer, under a burger | its sections as a bottom bar (`AppSections`, 3–5) | the host's bar: back with where it leads, title |
| `Medium` | a rail on the left, 84 dp: icon over label, the app's mark | its sections as tabs under the page header | the page header (T0) with crumbs; no host bar over an `Adaptive` app |
| `Expanded` | a sidebar on the left, 264 dp: the context switcher, apps by category, icons, marks | the same tabs | the page header (T0) with crumbs |

The apps on Home are **one list**; the drawer, the rail and the sidebar are three renderings of it,
by the shell. An app's own sections are one list of its own (`AppSections`). A destination hidden by
rights is absent from all three.

**Why:** a bottom bar on a 1400 dp window and a sidebar on a phone are both the look of a
prototype; and two lists drift apart the first time one is edited.

A screen opened *from* a destination (a record, a nested setting) keeps the destination
highlighted. On `Compact` it covers the bottom bar only if it is a focused flow (T7).

### 1.3. Width and gutters: one container, three modes **[I]**

```kotlin
enum class PageWidth(val max: Dp) { Narrow(640.dp), Default(960.dp), Wide(Dp.Unspecified) }
```

| | `Compact` | `Medium` | `Expanded` |
|---|---|---|---|
| Horizontal gutter | 16 dp | 24 dp | 32 dp |
| Top / bottom padding | 16 dp | 20 dp | 24 dp |
| Max content width | the window | `PageWidth.max`, centred | `PageWidth.max`, centred |

- `Narrow`: settings, simple forms, a focused flow. `Default`: lists and record pages. `Wide`: a
  log, a board, a master-detail, a table with many columns.
- The **screen's template** picks the width (§4), not taste: `Wide` means "the content is wider
  than a column by nature", not "there is a lot here".
- `Page` is the only place gutters and max width are set. No `padding(20.dp)` of a screen's own
  around its content, no second `widthIn(max = …)`.

**Why:** different left edges on different screens are what reads as "thrown together".

**[C]** In `ui/screens/`, no `padding(horizontal =` on a root container and no `widthIn(max`.

### 1.4. Touch and pointer at once **[I]**

- A tap target is at least 44 dp in every class. **Why:** a desktop window may be on a touch screen,
  and a mouse does not suffer from a large target.
- Hover is an enhancement, never the only way: nothing is revealed *only* on hover, and what a
  tooltip says (an absolute date, a full id) is also reachable on the record's page.
- Density changes with the class, not with the platform: a list row is 56 dp on `Compact`, 48 dp
  on `Medium` and `Expanded`; table text is `Small` on `Expanded`.
- A keyboard is assumed on `Expanded`: Tab order follows reading order, Enter submits a focused
  flow, Esc closes a dialog or a sheet.

### 1.5. Scrolling and insets **[I]**

- One vertical scroller per pane. The page never scrolls sideways; a wide table scrolls sideways
  itself, inside its own frame.
- Never a `LazyColumn` inside a `verticalScroll` column (it has no height and throws). A screen is
  either a `LazyColumn` whose items are the sections, or a scrolling `Column` with short lists.
- The shell applies the safe-area and keyboard insets once. A sticky bottom action bar (§7) sits
  above the keyboard and above the bottom navigation.

---

## 2. Navigation and state

### 2.1. The route table lives in one file **[I]**

```kotlin
sealed interface Route {
    data object Services : Route
    data class Service(val id: String, val tab: ServiceTab = ServiceTab.Overview) : Route
    data object Balance : Route
}
fun Route.path(): String            // "services/pizza-north/logs"
fun parseRoute(path: String): Route? // null → the home destination, never a blank screen
```

Every navigation is `navigate(Route…)`. The same table serves deep links
(`scheme://app/<id>/<path>`, [08](08-activation-routing-push.md)), the web hash in a browser
build, breadcrumbs and the highlighted destination.

**Why:** a sidebar, a breadcrumb, a button and a push payload that each write the address by hand
start to disagree at the first edit of one of them.

**[C]** No string that looks like a path outside the route file.

### 2.2. What lives in the route, what in saved state, what nowhere **[I]**

| What | Where | Survives |
|---|---|---|
| destination, record, a tab that is a section of the record | the **route** | a deep link, a restart, sharing |
| a filter, a sort, the selected row of a master-detail, a tab inside a section | **saved state** (`AppInstance.saveState` / `rememberSaveable`) | a process death, a window resize, going to a record and back |
| a block open or folded, a form draft, the scroll position | **nowhere** (composable state) | nothing |

**Why:** coming back from a record to an unfiltered list at the top is the most common way a
mobile app feels broken; and a link to a record must open the same thing for another person.

### 2.3. One way back **[I]**

- Inside the shell the host draws the back arrow; an app draws its own only where
  `!hostDrawsBack` ([12 §4](12-ui-kit.md#4-building-a-screen)).
- Back goes **up the route**, not through history: from a record to its list, with the list's
  filter and scroll intact.
- In a two-pane master-detail (`Expanded`) the detail has no back arrow: the list is on screen.
  The same route on `Compact` shows the detail alone, with back to the list.

### 2.4. The screen's key is its route **[I]**

Wrap the screen in `key(route)`. **Why:** two records share one composable; without the key
`remember { … }` keeps the previous record's state and the screen shows the wrong data.

### 2.5. Loading and refreshing **[I]**

- First frame: what is already known (a cached list, the row the person tapped) or a `Skeleton` in
  the shape of what is coming. Never a blank screen, never a full-screen spinner.
- Poll only while something can change (a service that is starting; a payment in progress), every
  2.5–3 s. Lists refresh on an action or pull-to-refresh, not on a timer.
- A failed refresh **does not empty** the list: keep the data, show one line "not updating" by the
  filters. A toast or an alert is for an action the person took themselves.
- After an action, update from its result, not "a second later".

---

## 3. Primitives: one set, never a second **[I]**

A screen is assembled from these and from `ui-kit` components, nothing else. If something is
missing, **add it to the set**, not next to the screen. A second date formatter, a second status
dictionary, a second page wrapper are forbidden.

All of them are in `ui-kit` (0.11) — signatures in [12](12-ui-kit.md) and the KDoc; the rules are
these:

| Primitive | Rule |
|---|---|
| `Page(PageWidth)` | gutters (16/24/32), max width, the gap between blocks; the only scroller of the pane |
| `PageHeader` | T0. A title **always**; crumbs where the host draws no bar (`Medium`, `Expanded`); actions beside the title where there is room, under it otherwise; the app's section tabs under it by themselves |
| `Panel` | a block about one subject: a title row, then a bordered card. Blocks are separated by the page's gap, not by their borders alone |
| `FactRow` | label and value; what a person quotes when asking about the record. `copyable` for ids |
| `EmptyState` | a dashed frame. Two meanings, two texts: "nothing yet" (what it is, why, a "Create" action) and "nothing matches the filter" ("Reset") |
| `StatusBadge(label, tone)` | the one look of a status; the app's dictionary (§5.1) gives the label and the tone |
| `Chip` + `Input` | the filter row: always visible, never behind a button; on `Compact` the chips scroll sideways. It stores nothing: the applied filter is saved state (§2.2) |
| `DataTable` | T1 on `Medium`/`Expanded`; the same records as rows on `Compact` — the screen chooses, by `LocalWindowClass` |
| `Grid(byWindow(…), spans)` | T2's main + rail (`spans = listOf(3, 2)` on `Expanded`, one column narrower), tiles of T5 |
| `ActionBar` | the sticky bottom bar of `Compact` (§7) |
| `Select` | a dropdown on `Medium`+, a bottom sheet on `Compact`; the caller does not choose |
| `ConfirmDialog` | a centred dialog on `Medium`+, a bottom sheet on `Compact`; `typeToConfirm` for the irreversible |
| `SideSheet` | from the right (440 dp) on `Medium`+, from the bottom on `Compact` |
| `AppSections` | the app's own navigation: a bottom bar on `Compact`, tabs under the header wider |
| formatters | `plural`, `ago`, `elapsed`, `money`, `shortId` — still the **app's** (they need its locale and currency); one of each per app |

Not yet in the kit: a master-detail pane pair (T3) and a board (T4) — an app that needs one builds it
once, from `Grid` and `Panel`, and it moves into the kit when a second app needs it.

`ui-kit` gives the rest: `Text`, `Button`, `IconButton`, `Card`, `Badge`, `Alert`, `Input`,
`Field`, `Switch`, `Checkbox`, `ItemGroup`/`Item`/`SwitchItem`/`ChoiceItem`, `Tabs`, `Avatar`,
`Separator`, `Progress`, `Spinner`, `Skeleton`, and Lucide icons
([06 §0](06-ui-api.md#0-the-kit-cxm42superizerkit)).

The dialog, the sheet, the menu (`⋯`), the select, the table and the toast are the kit's (0.11). A
screen never builds its own.

---

## 4. Screen templates: pick one of eight **[I]**

A ninth is not invented. If a screen fits none, the screen is doing two jobs: split it.

### T0. Page header — on every screen
`PageHeader`. The title is the name of the destination or of the record, the same words as the
navigation item and the last breadcrumb. Exactly **one** primary action; the rest are `Outline`
and `⋯`. Service badges (connection state, limits) are not in the header: a dot with a hint.

| `Compact` | `Medium` | `Expanded` |
|---|---|---|
| title in the top bar; primary action as the top bar's action or in the `ActionBar`; the rest in `⋯`; meta and tabs under the bar | title, meta and actions in one block; tabs under it | breadcrumbs, then title + status + actions on one line, description, tabs |

### T1. List — `PageWidth.Default`
`FilterBar` always visible; search; a sort; 5–8 columns at most, the first is the record's **name**
and opens it; the whole row is the target; row actions at the end of the row (`⋯`); dates relative
(the absolute one on the record's page); `EmptyState` in both meanings; `Skeleton` rows while loading.

| `Compact` | `Medium` | `Expanded` |
|---|---|---|
| rows: name, status badge, one line of meta; chips for filters; pull to refresh | a table of 3–4 columns | the full table; filters as dropdowns; the bulk-action bar appears only when rows are selected |

### T2. Record — `PageWidth.Default`
The title is the record's **name**, never its id; status badges beside it; one primary action.
Tabs when there is more than one subject (the tab is part of the route). `TwoColumn`: the main
column, and a rail with status, links to related records, identifiers (copyable), dates. A
"Service" tab or section takes ids, hashes, raw JSON and logs. Deleting lives in `⋯` or in a
"Danger zone" at the bottom, always with `ConfirmDialog`.

| `Compact` | `Medium` | `Expanded` |
|---|---|---|
| one column; the rail's blocks follow the main ones; tabs scroll sideways | one column, `Facts` in 2 columns | main + 320 dp rail |

### T3. Master-detail — `PageWidth.Wide`
For catalogues of settings: dozens of records, each fitting a screen. The selected row is saved
state; when the filter changes, the first row that matches is selected. Business records with a
life of their own (an order, a service) are T1 → T2, not T3.

| `Compact`, `Medium` | `Expanded` |
|---|---|
| the list; a tap opens the detail as its own screen with back | list 320 dp on the left, detail on the right, no back arrow |

### T4. Board — `PageWidth.Wide`
Live data, a "board / list" switch. Columns are the human names of states from the dictionary,
colours from the status tones. A card opens a `Sheet` with a summary and "Open" (T2).

| `Compact` | `Medium`, `Expanded` |
|---|---|
| one column at a time, the states as `Tabs` with counts | columns side by side; the board scrolls sideways inside its frame |

### T5. Analytics — `PageWidth.Default`
One period picker for the whole screen. KPI tiles have no fill; colour only on the delta and the
charts; a value of 0 is neutral, not an alarm; a tile opens the filtered list.

| `Compact` | `Medium` | `Expanded` |
|---|---|---|
| tiles 2 per row; charts stacked | tiles 2–4 per row | tiles in one row; charts in two columns |

### T6. Settings — `PageWidth.Narrow`
Grouped **by subject**, not by key; the human name first, the key in small mono beside it. Rows
are `ItemGroup`s. The "Danger zone" is the only place with a red fill.

| `Compact` | `Medium` | `Expanded` |
|---|---|---|
| a list of groups; each group is its own screen | the same | a sub-navigation of groups on the left (200 dp), the chosen group on the right |

### T7. Focused flow — `PageWidth.Narrow`, centred
Signing in, connecting to a server, paying, a wizard: one job, one primary button, no navigation
chrome (no bottom bar, rail or sidebar), a way to leave in the corner. At most one question per
step when there are several steps; a step indicator; progress kept if the person goes a step back.
The result (success or failure) is a screen of its own with what to do next, not a toast.

| `Compact` | `Medium`, `Expanded` |
|---|---|
| full screen; the primary button in the `ActionBar` | a 440 dp card centred on the page background |

---

## 5. States, colour, type, icons

### 5.1. One status dictionary for the whole app **[I]**

The key is the **value the server sends**, so a status reaches the screen without a translation
on the way. The tone comes from a closed set; the colour only from `Kit.colors`.

```kotlin
enum class Tone { Neutral, Running, Success, Warn, Danger, Muted }

val STATUSES: Map<String, Status> = mapOf(
    "online"   to Status("Работает", Tone.Success),
    "starting" to Status("Запускается", Tone.Running),
    "error"    to Status("Ошибка", Tone.Danger),
    …
)
```

| Tone | Text and dot | Fill |
|---|---|---|
| `Neutral` | `foreground` | `secondary` |
| `Running` | `info` | `info` at 12 % |
| `Success` | `success` | `success` at 12 % |
| `Warn` | `warning` | `warning` at 15 % |
| `Danger` | `destructive` | `destructive` at 12 % |
| `Muted` | `mutedForeground` | none, a `border` outline |

- `StatusBadge` is always this **subtle** form, the same in a list, on a record and on a board. The
  kit's `Badge` variants are solid fills; they are for counters and labels, not for statuses.
- An unknown value is printed as it is: the screen does not crash and shows the truth.
- A raw constant (`COOKING`, `ON_THE_WAY`) on a screen is a **bug**, not "for now".
- `Running` may carry a pulsing dot. The badge itself does not animate.

### 5.2. The colour norm **[I]**

- **`ButtonVariant.Default`**: one per screen, the main action.
- **`Outline` / `Ghost`**: everything else.
- **`Destructive` fill**: only the confirm button of a `ConfirmDialog` and the "Danger zone". In a
  `⋯` menu, red text without a fill. Never for "Reset", "Hide", a type label, a tile whose value is 0.
- **Status**: only `StatusBadge`.
- "0" and "no data" are neutral.
- No `Color(0xFF…)` and no `.copy(alpha = …)` on a colour outside the primitives. A missing colour is
  added to `KitColors`, light and dark ([12 §5](12-ui-kit.md#5-adding-a-component-to-the-kit)).

**[C]** `grep -rn "Color(0x" ui/screens/` finds nothing.

### 5.3. Type is a table of roles, not taste **[I]**

| Role | `TextVariant` |
|---|---|
| page title | `H4` on `Compact`, `H3` on `Medium` and `Expanded` |
| meta under the title, a description | `Muted` |
| section title | `Large` |
| a fact's label, a field's caption | `Small`, in `mutedForeground` for a fact |
| a value, a row's title, a field | `P`; `Small` in a table on `Expanded` |
| a row's second line, a hint | `Muted` |
| identifiers, keys, addresses, log lines | `Mono` |
| a KPI number | `H2` |

One title size for the whole app: wanting another is an edit of `PageHeader`, not of each screen.
A screen never passes `style =` or a font size.

### 5.4. Rhythm: the parent's gap separates, not the border alone **[I]**

| Between | Gap |
|---|---|
| a caption and its control, a title and its second line | 4–6 dp |
| fields of one group, rows of meta | `Kit.spacing.md` (12) |
| blocks inside a section | `Kit.spacing.lg` (16) |
| sections on a page | `Kit.spacing.xl` (24); `xxl` (32) on `Expanded` |

The gap is set **once, by the parent** (`Arrangement.spacedBy`), not by a `Spacer` after each
child or a padding on each block. **Why:** cards that touch, and a border as the only separator,
are what makes a dense screen read as one glued mass.

**[C]** No `Spacer(Modifier.height(` in `ui/screens/`.

### 5.5. Icons: one set **[I]**

Lucide, through `Icon`. No emoji as icons, no second set. An icon next to text is 16–20 dp and
takes the text's colour; an icon alone that can be tapped is an `IconButton` with a
`contentDescription`.

### 5.6. Empty and loading **[I]**

`EmptyState` in its two meanings (§3). Loading is a `Skeleton` where the content will be: three
to five rows for a list, blocks for a record. A `Spinner` only inside a button (`loading = true`)
or where the result has no shape.

---

## 6. Words, numbers, dates **[I]**

- No raw field name, class name, constant, JSON or epoch number on a screen.
- Dates: relative in lists (`ago`: "5 мин назад"), absolute on the record and in forms; `elapsed`
  for "running for 4 min". One formatter, the person's locale.
- Numbers: plural forms always (`plural`); thousands separated; currency from the data, not a
  literal; numbers in a column right-aligned.
- One word for one action across the app: "Создать", "Сохранить", "Удалить", "Обновить".
- No "partially implemented" notices in a product screen: a "Beta" badge or nothing.
- Every string comes from the app's string table, the status dictionary included.

---

## 7. Forms and saving **[I]**

- **One** "Save", in one place per class: the `ActionBar` at the bottom on `Compact`, the page
  header on `Medium` and `Expanded`. Disabled until something changed.
- A "not saved" mark while there are changes, and a `ConfirmDialog` on leaving with them. Saving
  "on touch" only for single switches in a list, and then the row shows that it saved.
- Validation on losing focus and on save, **not on load**: red on a fresh form reads as breakage.
- Every control has a visible caption: `Input` inside `Field`. A placeholder is not a caption.
- Short fields sit two per row on `Medium` and `Expanded`, one per row on `Compact`.
- A choice from a list is `Select`; a boolean is `Switch`, **one** way across the app.
- The keyboard fits the field (`KeyboardOptions`: number, email, no auto-correct for codes).
- A destructive action asks first (`ConfirmDialog`), names the object in the question
  («Удалить «Пицца Север»?») and says what will be lost.

---

## 8. Checks

### 8.1. What a build or a grep must catch **[I]**

| Check | Catches |
|---|---|
| `Color(0x` outside the theme | invented colours; a broken dark theme |
| `maxWidth <` / `widthIn(max` outside the primitives | a screen's own breakpoint or width |
| `Spacer(Modifier.height(` in screens | rhythm set by hand |
| `fontSize =` / `style =` in screens | a type scale of a screen's own |
| `cx.m42.superizer.ui.components` imports | the deprecated widgets |
| a string literal with Cyrillic or a space inside `Text(` in screens | a string outside the string table |

### 8.2. Screenshots are the review **[I]**

Every screen is shot in **three widths and two themes**, and the pictures are looked at:

| Class | Viewport |
|---|---|
| `Compact` | 390 × 844 |
| `Medium` | 820 × 1180 |
| `Expanded` | 1440 × 900 |

A test can be green while the screen is blank ([07 §5](07-testing.md)). Each state that has its
own look gets a shot: loaded, empty, loading, error.

---

## 9. "The screen is done" **[I]**

1. It opens by its route; title = navigation item = last breadcrumb.
2. One of the templates of §4; a ninth was not invented.
3. Looked at in `Compact`, `Medium` and `Expanded`; the navigation is the bar, the rail and the
   sidebar respectively; nothing is cut off or scrolls sideways.
4. Looked at in the dark theme.
5. Resizing the window across a class boundary keeps the selection, the text typed and the filter.
6. The filter and the tab survive going to a record and back.
7. One primary button; saving in its one place, with the "not saved" mark and the question on leaving.
8. Only primitives of §3 and kit components; none of the patterns of §8.1.
9. No raw field name, constant, epoch or JSON; statuses only through `StatusBadge`.
10. An empty state (both meanings) and a skeleton are in place.
11. Related records are links, both ways where it makes sense.
12. Every tap target is 44 dp or more and has a `testTag` `"<app>:<element>"`.
13. A screen the person has no right to open is absent from the navigation, not disabled in it.

---

## 10. Never **[I]**

1. A list without an explicit choice of columns (or it becomes 51 columns with `rmsId` and epochs).
2. A form in one column several screens long with no sections or tabs.
3. Several places for "Save", or several kinds of tabs, in one app.
4. Two screens for one record with no way between them; two entrances to one function.
5. Red as an accent.
6. A technical log as the first block of a record; a service badge as the main thing in a header.
7. A hard-coded colour, currency, country or time zone.
8. A bottom bar on `Expanded`; a sidebar on `Compact`; a table on `Compact`.
9. A phone layout stretched across a desktop window (one 1400 dp column of full-width rows).
10. A component of a screen's own that the primitive set already has.
11. Something that exists only on hover.
12. A navigation item that leads where another one does; a section of one item.

---

## 11. Pitfalls: symptom → cause → cure

Each row is something that already happened.

| Symptom | Cause | Cure |
|---|---|---|
| Russian text is empty boxes in the browser | Compose on the web has no system fonts | a font with Cyrillic in resources, passed to `kitTypography(...)` |
| A host's font does not reach its apps | `HostTheme` calls `KitTheme` with the default typography | known gap ([06 §0](06-ui-api.md#0-the-kit-cxm42superizerkit)) |
| Content is a 480 dp strip in a wide window | the app did not say it can be wide | `AppInstance.layout` → `AppLayout(width = AppWidth.Adaptive)`, and `ShellLayout.Adaptive` in the host |
| A locked app is never closed when left idle | a `BoxWithConstraints` around the shell recomposed it with every measure | measure after layout (`WindowClassProvider` does) |
| The second record shows the first one's data | one composable for both, `remember` kept | `key(route)` (§2.4) |
| A crash "infinite height constraints" | a `LazyColumn` inside a `verticalScroll` | §1.5 |
| Content has a double inset at the top | a scaffold's `PaddingValues` applied twice | the page primitive applies insets, the screen never does |
| A screenshot of every screen shows the first screen | a change of the URL hash alone does not restart the app | open `about:blank` between screens |
| `Muted` text is not muted inside a filled block | `LocalContentColor` overrides the variant's colour | the kit's `Text` handles it; do not pass colours by hand around it |

---

## 12. How a set of screens gets made

Good screens came from a pipeline, not from this page alone. Skip a step and the result is a
working prototype, not a product.

1. **Review what exists**: a screenshot of every screen in three widths, a list of what is wrong.
2. **A brief**: the sections, the route table, a template of §4 for each screen, the status dictionary.
3. **A clickable prototype on made-up data**, screens only, no server. Looks are decided here.
4. **Primitives move into the library** once the prototype's screens pass §9.
5. **Real screens**, one at a time, most used first, each compared with its prototype.

## Keeping this page

- A rule is added **together with the case** that produced it and the check that catches it.
- A rule the library now enforces by itself is deleted, not kept "just in case".
- When this page and the code disagree, fix the one that is wrong and say which it was.
