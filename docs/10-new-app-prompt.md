# New-app prompt template

Copy this page, fill in the brackets, hand it to an agent. It is self-contained: an agent that
reads this and the three linked pages has everything it needs to produce a finished app, tests
included.

---

## The prompt

````markdown
# Build a Superizer app: <APP NAME>

You are adding a new app to a Superizer host. Superizer is a Compose Multiplatform host framework:
independent apps plug into a host through one contract and reach the platform through one object.

## Read first

- `docs/03-app-guide.md` — the complete authoring guide. Follow it.
- `docs/04-core-api.md` — every type and method you may call.
- `docs/09-recipes.md` — working patterns for config, storage, network, push, settings.

## The rule you may not break

An app is written as if it were already standalone. It never accepts, stores, or depends on a
`Superizer`, an `AppHandler`, or an `AppRegistry`, and never imports `cx.m42.superizer.host.*`.
Its whole view of the platform is the `AppRuntime` it is handed. `./gradlew checkDependencyRules`
fails the build if this is broken.

## What to build

| | |
|---|---|
| **App id** | `<kebab-case-id>` |
| **Module** | `apps/<kebab-case-id>`, package `<com.example.apps.name>` |
| **Version** | `1.0.0` |
| **Title** | en: `<English title>` · ru: `<Русское название>` |
| **Description** | en: `<one line>` · ru: `<одна строка>` |
| **Icon** | `<AppIcon.Path("M…") | AppIcon.Named("calculator") | AppIcon.Letter('X')>` |
| **Category** | `<Tools | Finance | Service | …>` |
| **Hidden** | `<no | yes — diagnostic, reachable only by promo code>` |
| **Languages** | `<en, ru>` |

### What it does

<Two or three sentences a user would recognise. The main gesture first.>

### Screens and behaviour

1. <screen / state 1 — what is on it, what the user can do>
2. <screen / state 2>
3. <edge case: empty, offline, error>

### Config (`<Name>Config`)

| Field | Type | Default | Why it is config and not code |
|---|---|---|---|
| `<field>` | `<String>` | `<"USD">` | <a QR code / promo code should be able to change it> |

`fallbackToDefault = <true | false>` — `<true: open on defaults when a payload is broken; false: refuse to open>`

### State that must survive

- **In `saveState`/`restore`** (a process death): <the half-typed input, the selected tab>
- **In `runtime.storage`** (forever, namespaced): <the user's last choice, a cache>

### Platform services it uses

- [ ] `storage` — <what for>
- [ ] `network` — <endpoint(s), and what happens offline>
- [ ] `push` — topics: `<topic>`; what a message does
- [ ] `locale`, `haptics`, `analytics`, `clock`, `lifecycle`
- [ ] `navigation` — opens `<other app id>` when <…>
- [ ] optional `service(<Key>)` — <required, or degrade how?>

### Deep links

| Path | Query | Produces |
|---|---|---|
| `<rate>` | `<pair=USD-RUB>` | `<a config with from/to set>` |

Full link: `<scheme>://app/<id>/<path>?<query>`. Declare every path in the manifest and register
exactly those in `setup`.

### A block on the host's Settings page

<yes: what is in it — a choice of X, a switch for Y | no>

## Deliverables

```
apps/<id>/build.gradle.kts                 # KMP library: android, desktop, wasmJs; explicitApi()
apps/<id>/src/commonMain/kotlin/<pkg>/
    <Name>App.kt                           # the ONLY public class (+ its config data class)
    <Name>Instance.kt                      # internal
    <Name>Screen.kt                        # internal
    <Name>Strings.kt                       # internal, one table per language
apps/<id>/src/desktopTest/kotlin/<pkg>/
    <Name>ContractTest.kt                  # class …: AppContractTest(<Name>App())
    <Name>ScreenTest.kt                    # runAppTest, driving the main gesture
```

Plus, in the host: one `register(<Name>App())` line — and nothing else anywhere.

Dependencies for the app module: `api(libs.superizer.core)`,
`implementation(libs.superizer.ui.theme)`, `implementation(libs.superizer.ui)`,
`implementation(libs.compose.unstyled)`, `implementation(libs.kotlinx.serialization.json)`;
test: `libs.superizer.testing`, `libs.compose.ui.test`, `compose.desktop.currentOs`,
`kotlin("test")`. **Never** `superizer:host`, the host module, or another app.

## Constraints

- Exactly one public class. Everything else `internal`; the module sets `explicitApi()`.
- `onLaunch` under 200 ms and **no network call** — the TCK enforces both. Fetch after the first
  frame, on `runtime.scope`.
- No top bar, no drawer, no global modal: the host draws the frame. Veto a close with
  `onCloseRequested` and let the host ask.
- Every asserted node has a `testTag("<app-id>:<element>")`. Never assert on text.
- No hard-coded strings in one language, no `Color(0xFF…)` — read `AppTheme` and your own string
  tables.
- Use the widgets in `cx.m42.superizer.ui.components` (`AppButton`, `AppTextField`, `Section`,
  `ChoiceRow`, `ToggleRow`, `Switch`, `Divider`, `RowDivider`, `IconButton`).
- Time comes from `runtime.clock`, never from the platform.
- Declare in the manifest exactly the deep links, push topics and required services you use.

## Done means

- [ ] `./gradlew :apps:<id>:desktopTest` green, including the TCK.
- [ ] `./gradlew checkDependencyRules` green.
- [ ] The app opens from Home in the host and the main gesture works.
- [ ] Every checkbox in `docs/03-app-guide.md §11` is ticked.
- [ ] A one-paragraph note in the PR saying what a user can now do that they could not before.
````

---

## A filled-in example

````markdown
# Build a Superizer app: Tip calculator

## What to build

| | |
|---|---|
| **App id** | `tip` |
| **Module** | `apps/tip`, package `cx.m42.apps.tip` |
| **Title** | en: Tip calculator · ru: Калькулятор чаевых |
| **Description** | en: Split a bill, round it up · ru: Разделить счёт и округлить |
| **Icon** | `AppIcon.Letter('T')` |
| **Category** | Tools |
| **Hidden** | no |

### What it does

Enter a bill, pick a tip percentage, and see the total and the per-person share. Rounding up to the
nearest whole unit is one tap.

### Screens and behaviour

1. Amount field, a row of percentage chips (10 / 15 / 20 / custom), a people stepper.
2. A result block: total, tip, per person. Updates as you type.
3. Empty or unparseable amount → the result block shows dashes, not zeroes.

### Config (`TipConfig`)

| Field | Type | Default | Why |
|---|---|---|---|
| `defaultPercent` | `Int` | `15` | differs by country; a QR at a venue can set it |
| `currency` | `String` | `"USD"` | |
| `roundUp` | `Boolean` | `false` | |

`fallbackToDefault = true`.

### State that must survive

- `saveState`: the amount, the chosen percent, the people count.
- `runtime.storage`: the last percent used, so the next open starts there.

### Platform services

- `storage` — the last percent.
- `haptics` — one tick on a percentage chip.
- `analytics` — `screen("tip")`, `event("round-up")`.
- No network, no push.

### Deep links

| Path | Query | Produces |
|---|---|---|
| `bill` | `amount=42.50&percent=20` | a config with those two set |

### Settings block

Yes: a `ChoiceRow` list of default percentages (10 / 15 / 18 / 20), stored under `"percent"`.
````

---

## Handing it over

- The brief works in Russian or English — the constraints are what matter, not the language.
- If the agent asks where the library is: `docs/01-getting-started.md §2`. In this workspace it is
  `/prj/superizer`, consumed by coordinates with `includeBuild`.
- Review the diff for exactly two things first: **what is `public`**, and **whether the manifest
  matches `setup`**. Everything else the tests will tell you.
