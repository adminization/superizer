# Writing an app

Everything needed to build a new Superizer app, in the order you will need it. If you are an agent
being told "build an app that does X", read this page and
[the recipes](09-recipes.md); the reference pages are for looking things up.

---

## 1. The shape of an app module

```
apps/hello/
├── build.gradle.kts                  # §4 of Getting started
└── src/
    ├── commonMain/kotlin/com/example/apps/hello/
    │   ├── HelloApp.kt               # public: the manifest, the config spec, setup, launch
    │   ├── HelloInstance.kt          # internal: state, saveState/restore, Content()
    │   ├── HelloScreen.kt            # internal: the composables
    │   ├── HelloStrings.kt           # internal: the language tables
    │   └── Hello.kt                  # internal: the pure logic, if there is any
    └── desktopTest/kotlin/com/example/apps/hello/
        ├── HelloContractTest.kt      # class HelloContractTest : AppContractTest(HelloApp())
        └── HelloScreenTest.kt        # runAppTest { … }
```

**Exactly one class is `public`: the `SuperizerApp` subclass** (plus its config data class, which
has to be, and any constant a host needs, such as a promo code). Everything else is `internal`. The
module sets `explicitApi()`, so Kotlin will make you say so.

Rules the build enforces:

- no dependency on `superizer:host`, on a host module, or on another app;
- `api(libs.superizer.core)`, `implementation` for `ui-theme` and `ui`.

---

## 2. The smallest app that works

```kotlin
package com.example.apps.hello

import androidx.compose.runtime.Composable
import cx.m42.superizer.app.*
import cx.m42.superizer.runtime.InstanceRuntime
import kotlinx.serialization.Serializable

@Serializable
public data class HelloConfig(val who: String = "world")

public class HelloApp : SuperizerApp<HelloConfig>() {

    override val manifest: AppManifest = AppManifest(
        id = AppId("hello"),
        version = "1.0.0",
        metadata = AppMetadata(
            title = localized("en" to "Hello", "ru" to "Привет"),
            icon = AppIcon.Letter('H'),
            category = "Tools",
        ),
    )

    override val configSpec: AppConfigSpec<HelloConfig> =
        AppConfigSpec(HelloConfig.serializer(), HelloConfig())

    override fun launch(runtime: InstanceRuntime, config: HelloConfig): AppInstance =
        HelloInstance(config)
}

internal class HelloInstance(private val config: HelloConfig) : AppInstance() {
    @Composable
    override fun Content() {
        Text("Hello, ${config.who}", modifier = Modifier.testTag("hello:greeting"))
    }
}
```

Register it in a host (`register(HelloApp())`, `home("hello")`) and it is on Home.

---

## 3. The manifest — what the host may know before running your code

```kotlin
AppManifest(
    id = AppId("currency-converter"),      // kebab-case, checked in the constructor
    version = "1.0.0",                     // the app's own version, unrelated to the library's
    minHostContract = 1,                   // oldest host this app tolerates
    metadata = AppMetadata(
        title = localized("en" to "Currency converter", "ru" to "Конвертер валют"),
        description = localized("en" to "ECB rates, works offline from cache"),
        icon = AppIcon.Path("M12 2a10 10 0 1 0 …"),   // your own SVG, 24×24 viewBox
        hidden = false,                    // true → invisible until an activation unlocks it
        category = "Finance",              // free-form grouping on All Apps
    ),
    requires = setOf(CameraService.Key),   // optional services you cannot work without
    deepLinks = setOf("rate"),             // paths you serve; setup() must register each
    pushTopics = setOf("rates"),           // topics you may subscribe to
    networkHosts = setOf("api.frankfurter.app"),  // informational today
)
```

Things to get right:

- **`id` is kebab-case** (`[a-z0-9][a-z0-9-]*`) and is what QR payloads, promo codes, deep links and
  push messages name. The constructor throws on anything else.
- **The manifest must round-trip through JSON.** `AppContractTest` checks it. That is why `title` is
  a serializable `Localized` table rather than a lambda, and why an icon is a *description*.
- **Declare only what you use.** An undeclared deep link registered in `setup` disables the app; an
  undeclared push topic is refused at `subscribe`.
- **`hidden = true`** for anything diagnostic. Hidden apps are absent from All Apps until an
  activation unlocks them, cannot be opened by another app, cannot be reached by a bare deep link,
  and drop pushes.

### Icons

| Form | When |
|---|---|
| `AppIcon.Letter('H')` | the fallback; one letter in a tile |
| `AppIcon.Named("calculator")` | a glyph from the host's pack: `calculator`, `currency`, `apps`, `qr`, `settings`. An unknown name falls back to a letter. |
| `AppIcon.Path("M4 4h16v16H4V4z…")` | **your own icon**, as an SVG `d` attribute over a 24×24 viewBox. Filled, not stroked. A malformed path draws the letter instead of crashing the catalog. |

`AppIcon.Path` is how an app ships an icon the host has never seen without anyone editing the host.
Holes need the opposite winding direction — SVG fills by the non-zero rule.

---

## 4. Config — JSON outside, typed inside

A config arrives from a QR code, a promo code, a deep link, another app, or the Service Menu. **The
app never learns which.** That is the point: one code path, one set of rules.

```kotlin
@Serializable
public data class ConverterConfig(
    val base: String = "USD",
    val favorites: List<String> = listOf("EUR", "RUB", "GBP"),
    val ratesUrl: String = DEFAULT_RATES_URL,
    val from: String? = null,
    val to: String? = null,
)

override val configSpec = AppConfigSpec(
    serializer = ConverterConfig.serializer(),
    default = ConverterConfig(),
    schemaVersion = 1,
    fallbackToDefault = true,
    migrate = { fromVersion, raw -> if (fromVersion < 2) raw else raw },
)
```

| Property | Meaning |
|---|---|
| `default` | what an empty payload decodes to. Missing keys keep the default value, field by field. |
| `schemaVersion` | stamped into every `encode`; a payload without one is assumed current. |
| `migrate(from, raw)` | called when a payload's version differs; return the up-to-date JSON. |
| `fallbackToDefault` | `true` (default): a broken payload is *reported* and the app opens on defaults. `false`: the launch fails and the host shows its error screen. |

`decode` is lenient about unknown keys and strict about broken ones: a config someone printed a year
ago must still open, and a config that says `scientific` must never silently open a plain
calculator.

Put behaviour in the config when you want it changeable without a release — the converter's
`ratesUrl` is in the config so a QR code can point it at a fallback endpoint or a test can point it
at a fake.

For an app with genuinely no config: `override val configSpec = AppConfigSpec.None` (a
`SuperizerApp<Unit>`).

---

## 5. `setup(ctx)` — once per enable

Called at host startup, before any screen exists. Wire the behaviour behind the manifest here.
**Everything registered is torn down for you on disable, in reverse order.** Never undo it yourself.

```kotlin
override fun setup(ctx: AppSetupContext) {
    // A block on the host's Settings page, under this app's own title.
    ctx.settingsSection { runtime -> ConverterSettingsSection(runtime) }

    // myhost://app/currency-converter/rate?pair=USD-RUB
    ctx.deepLink("rate") { params ->
        val pair = params["pair"].orEmpty().split('-')
        configSpec.encode(ConverterConfig(from = pair.getOrNull(0), to = pair.getOrNull(1)))
    }

    // Host events, delivered whether or not a screen is open.
    ctx.listener { event -> if (event is SuperizerEvent.Reset) cache.clear() }

    // Background work lives on the app-level scope.
    ctx.runtime.scope.launch { ctx.runtime.push.subscribe("rates") }
}
```

`ctx.runtime` is the **app-level** runtime: no `navigation` (there is no screen), and a scope that
survives closing the app.

Keep `setup` cheap and non-throwing: a throw here leaves the app disabled with a `LaunchFailed`
event.

---

## 6. `launch(runtime, config)` and `AppInstance`

`launch` is called on **every open**. It must be cheap — build the state holder and return.
Anything slow belongs in `onLaunch`.

```kotlin
internal class ConverterInstance(
    private val runtime: InstanceRuntime,
    private val config: ConverterConfig,
) : AppInstance() {

    private val amount = MutableStateFlow("100")
    private val _chrome = MutableStateFlow(AppChrome())
    override val chrome: StateFlow<AppChrome> get() = _chrome

    override suspend fun onLaunch() {
        runtime.analytics.screen("converter")
        _chrome.value = AppChrome(subtitle = config.base)
        // Long-lived collectors go on a scope, not in the body: onLaunch must return fast.
        runtime.scope.launch { runtime.push.messages.collect { … } }
    }

    override fun saveState(): JsonObject = buildJsonObject {
        put("amount", JsonPrimitive(amount.value))
    }

    override fun restore(state: JsonObject) {
        amount.value = state["amount"]?.jsonPrimitive?.content ?: "100"
    }

    override fun onBackground() { polling?.cancel() }
    override fun onForeground() { startPolling() }

    @Composable
    override fun Content() = ConverterScreen(this)

    override suspend fun onCloseRequested(): Boolean = !hasUnsavedEdits
    override fun onClose() { /* last chance to persist */ }
    override fun dispose() { /* release; the scope is cancelled right after */ }
}
```

### The budget

`onLaunch` has a **200 ms budget** and **must make no network call** — `AppContractTest` fails you
otherwise. The container draws a blank frame while an app launches, deliberately: a spinner that
flashes for one frame is worse than none. Fetch in `Content()`'s `LaunchedEffect` or on the scope.

### `saveState` / `restore`

A snapshot of what the *screen* would lose if the process died now — a half-typed sum, a scroll
position. Anything that should outlive the **session** rather than the **process** belongs in
`runtime.storage`.

- The handler takes a snapshot when the app closes to make room for another, and when the host goes
  to background.
- It hands it back through `restore` after `onLaunch` on the next launch.
- Snapshots older than 24 hours are dropped.
- `restore` must tolerate an old shape: the snapshot may predate the update reading it. Never throw
  — a throw is logged and the app starts fresh, which is the best case; assume it and be explicit.
- `saveState → restore → saveState` must be stable. The TCK checks it.

### `chrome`

What the host's top bar reads. Observable, so an app that navigates *inside* itself can retitle the
bar and claim back without the host knowing what screen it is on.

```kotlin
AppChrome(title = null /* → metadata.title */, subtitle = "scientific", canGoBack = true)
```

### `onCloseRequested`

Return `false` and the host puts up **its own** dialog asking the user. Do not draw your own — a
modal is global UI, and the host owns global UI.

---

## 7. The screen

The host draws the frame: top bar, drawer, back handling, the 480 dp cap. **Your `Content()` is what
goes inside it.** An app that draws its own top bar is fighting the shell.

```kotlin
@Composable
internal fun ConverterScreen(instance: ConverterInstance) {
    val runtime = LocalAppRuntime.current          // the same InstanceRuntime, for deep composables
    val strings = converterStrings(runtime.locale.langTag.collectAsState().value)
    val tokens = AppTheme                          // colours and type; never invent your own

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(strings.title, style = tokens.header, color = tokens.foreground)
        AppTextField(
            value = amount,
            onValueChange = { amount = it },
            modifier = Modifier.testTag("currency-converter:amount"),
        )
        AppButton(
            text = strings.convert,
            onClick = { instance.convert() },
            modifier = Modifier.testTag("currency-converter:convert"),
        )
    }
}
```

Use [the widgets in `ui`](06-ui-api.md) — `AppButton`, `AppTextField`, `Section`, `ChoiceRow`,
`ToggleRow`, `Switch`, `Divider`, `RowDivider`, `IconButton` — and the tokens in `ui-theme`. An app
built out of them looks like it belongs, in this host and in any other.

### Test tags

Every interactive or asserted node gets `testTag("<appId>:<element>")`. Text changes with the
language; tags do not. The container already puts `<appId>:root` on your root — do not add it
yourself.

### i18n

Ship your own tables; the host only supplies the tag.

```kotlin
internal interface ConverterStrings { val title: String; val convert: String }
internal object En : ConverterStrings { override val title = "Currency converter"; … }
internal object Ru : ConverterStrings { override val title = "Конвертер валют"; … }

internal fun converterStrings(tag: String): ConverterStrings =
    if (tag.startsWith("ru")) Ru else En
// or, better: runtime.locale.pick(mapOf("ru" to Ru, "en" to En), fallback = En)
```

`LocaleService.pick` is the one place the primary-subtag match is written, so `ru-RU` finds `ru`.

---

## 8. Reaching the platform

`runtime` is the whole of it. Two rules hold for every service, and knowing them saves surprises:

- **Nothing takes a type that cannot be JSON.** No `KSerializer`, no `Painter`, no platform class.
- **Every suspending call is main-safe.** Call it from the main dispatcher without `withContext`.

| | |
|---|---|
| `storage` | `get/set/remove/keys`, namespaced to your app id; `getJson`/`setJson` for typed sugar |
| `network` | `get/post/request` — strings in, strings out; `online` is a signal, not a guess |
| `logger` | `debug/info/warn/error`, tagged `app:<id>`; the ring buffer keeps every level |
| `analytics` | `event(name, params)`, `screen(name)` — the host prefixes your id |
| `locale` | `langTag: StateFlow<String>`, `pick(tables, fallback)` |
| `haptics` | `tick()`; the host's setting is already applied |
| `push` | `enabled`, `requestPermission`, `subscribe/unsubscribe`, `subscriptions`, `messages` |
| `auth` | `session`, `signIn`, `signOut` (anonymous today; the shape is fixed) |
| `apps` | `list()`, `get(id)` — a read-only view of the registry |
| `clock` | `now()` — injected, so "older than a day" is testable |
| `lifecycle` | `Foreground` / `Background` of the host process |
| `events` | the host's event flow |
| `navigation` | `openApp`, `close`, `openSettings`, `openUrl` — **instance level only** |
| `service(key)` | optional services a host may or may not have; `require(key)` when the manifest declares it |

Full signatures: [`core` API reference](04-core-api.md#7-the-services).

Notable behaviours:

- `network` injects **no credentials**: read `auth.session` and set the header yourself. 15-second
  timeout, no retries, `expectSuccess = false` — a 404 is an answer, not an exception. A transport
  failure throws `NetworkException`.
- `push.subscribe` silently refuses (and logs) a topic your manifest does not declare.
- `navigation.openApp` returns a typed `Result`: `NavigationError.UnknownApp`, `.Locked`
  (you may not reveal a hidden app by asking for it), `.Vetoed` (the open app refused to close).

---

## 9. Contributing to the host's Settings page

```kotlin
ctx.settingsSection { runtime ->
    val favorites by remember { … }
    Column {
        ToggleRow(label = "Refresh on open", checked = auto, onCheckedChange = { … })
        RowDivider()
        ChoiceRow(label = "USD", selected = base == "USD", onClick = { … })
    }
}
```

The host draws the frame and the title (your `metadata.title`); what is inside is yours. The block
is handed the **app-level** runtime, so it works whether or not the app is open. Put a
`testTag("settings:<appId>-<element>")` on anything you want to assert; the host already tags the
block itself `settings:section-<appId>`.

---

## 10. Testing

One line for the guarantees, `runAppTest` for the screen — **no host required**.

```kotlin
class HelloContractTest : AppContractTest(HelloApp())

class HelloScreenTest {
    @Test fun itGreetsWhoeverTheConfigNames() = runAppTest(
        HelloApp(),
        config = AppConfig(buildJsonObject { put("who", JsonPrimitive("Ada")) }),
    ) {
        assertShown("hello:greeting")
        runtime.storage.entries["seen"] = "1"        // the fakes are visible
        click("hello:again")
    }
}
```

`AppContractTest` checks the things a host *assumes* and therefore never re-checks at runtime: the
default config decodes, a broken one is reported rather than swallowed, the manifest round-trips,
`setup` registers only what the manifest declares, `saveState → restore` is stable, `onLaunch` is
under 200 ms and makes no network call, and `Content()` composes.

Override `configFixtures` and `stateFixtures` with raw JSON strings of older payloads so migrations
stay honest. Details: [Testing](07-testing.md).

---

## 11. The checklist before you call it done

- [ ] Exactly one public class (plus config and any host-facing constant); everything else `internal`.
- [ ] `AppId` is kebab-case and unique in the target host.
- [ ] Manifest declares every deep link, push topic and required service the code uses — and nothing else.
- [ ] `title` and `description` have a table per language the host speaks.
- [ ] An icon that is yours: `AppIcon.Path`, or a name from the pack.
- [ ] Config has sensible defaults; a broken payload's behaviour (`fallbackToDefault`) is a decision, not an accident.
- [ ] `onLaunch` is under 200 ms and makes no network call.
- [ ] `saveState`/`restore` cover what the screen would hate to lose, and `restore` tolerates an old shape.
- [ ] Every asserted node has a `<appId>:<element>` test tag.
- [ ] No string is hard-coded in one language.
- [ ] Colours and type come from `AppTheme`; no `Color(0xFF…)` in the app.
- [ ] `class XContractTest : AppContractTest(XApp())` exists and passes.
- [ ] At least one `runAppTest` that drives the main gesture.
- [ ] `./gradlew :apps:x:desktopTest` and `./gradlew checkDependencyRules` are green.
