# Recipes

Working patterns, lifted from the apps that ship with the library and with Unitool. Each one is
short on purpose: if a recipe needs a page, the contract is probably missing something.

---

## Config that changes what the screen is

```kotlin
@Serializable
public data class CalculatorConfig(val mode: String = STANDARD) {
    internal val isScientific: Boolean get() = mode == SCIENTIFIC
    public companion object {
        public const val STANDARD: String = "standard"
        public const val SCIENTIFIC: String = "scientific"
    }
}
```

A config nobody can see the effect of is a config nobody can tell is working. Make the first one
visible: an extra row of keys, a subtitle in the chrome.

The host can then ship it as a five-character promo code:

```kotlin
promoCodes(mapOf("SCI" to ActivationResult.Success(
    Activation(AppId("calculator"), AppConfig(buildJsonObject { put("mode", JsonPrimitive("scientific")) })),
)))
```

---

## Config with a migration

```kotlin
override val configSpec = AppConfigSpec(
    serializer = MyConfig.serializer(),
    default = MyConfig(),
    schemaVersion = 2,
    migrate = { from, raw ->
        if (from >= 2) raw
        else JsonObject(raw - "currency" + ("base" to (raw["currency"] ?: JsonPrimitive("USD"))))
    },
)
```

Then add the old shape to the TCK so it stays readable:

```kotlin
override val configFixtures = listOf("""{"schemaVersion":1,"currency":"EUR"}""")
```

---

## An endpoint that is configuration, not code

```kotlin
val ratesUrl: String = "https://api.frankfurter.app/latest?from=USD"
```

Putting the URL in the config means swapping the source — to a fallback endpoint, or to a fake in a
test — is a payload rather than a release. The app still never learns where the payload came from.

---

## Offline-first: cache, then network

```kotlin
override suspend fun onLaunch() {
    // Only the cache, and only if it is already there: cheap, and the first frame has numbers on it.
    repository.cached(config.base)?.let { state.value = Ready(it, fromCache = true) }
}

// Called by the screen once composed — the first network request of the app's life.
fun refresh(force: Boolean = false) {
    if (runtime.lifecycle.value == HostLifecycle.Background) return
    job?.cancel()
    job = runtime.scope.launch {
        repository.load(config.ratesUrl, config.base, force)
            .onSuccess { state.value = Ready(it.rates, it.fromCache) }
            .onFailure { if (state.value !is Ready) state.value = Failed }
    }
}

override fun onBackground() { job?.cancel() }
override fun onForeground() { refresh() }
```

Never fetch in `onLaunch` — the 200 ms budget is what lets the container show a frame immediately.
Show "offline" from `runtime.network.online`, not from the first failure, so the user is told
before waiting out a timeout.

The cache itself is plain storage:

```kotlin
runtime.storage.setJson("rates.$base", CachedRates(rates, runtime.clock.now()))
val cached = runtime.storage.getJson<CachedRates>("rates.$base")
    ?.takeIf { runtime.clock.now() - it.savedAt < 6 * 60 * 60 * 1000 }
```

`runtime.clock` and not `System.currentTimeMillis()`: that is what makes "older than six hours" one
line in a test.

---

## A preference the user sets in Settings

```kotlin
// setup
ctx.settingsSection { runtime -> BaseCurrencySection(runtime) }

@Composable
private fun BaseCurrencySection(runtime: AppRuntime) {
    val scope = rememberCoroutineScope()
    var base by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { base = runtime.storage.get("base") ?: "USD" }

    Column(Modifier.testTag("settings:converter-base")) {
        listOf("USD", "EUR", "RUB").forEach { code ->
            ChoiceRow(label = code, selected = base == code, onClick = {
                base = code
                scope.launch { runtime.storage.set("base", code) }
            })
            RowDivider()
        }
    }
}
```

And in the instance, let a *config* beat a *preference*:

```kotlin
// A currency named by the config wins: that is a deep link or a QR code saying what this launch is
// about. The stored preference is a standing wish, not an instruction.
if (config.from == null) runtime.storage.get("base")?.let { from.value = it }
```

---

## Opening another app

```kotlin
runtime.navigation.openApp(
    AppId("calculator"),
    AppConfig(buildJsonObject { put("mode", JsonPrimitive("scientific")) }),
).onFailure { error ->
    when (error) {
        is NavigationError.UnknownApp -> message = "not installed"
        is NavigationError.Locked     -> message = "not available"   // never "it exists but…"
        is NavigationError.Vetoed     -> Unit                         // the user was asked and said stay
        else -> Unit
    }
}
```

The current app is closed politely first, so a form with unsaved edits can still say no. An app
cannot reveal a hidden app by asking for it.

---

## Reacting to host events without a screen

```kotlin
override fun setup(ctx: AppSetupContext) {
    ctx.listener { event ->
        when (event) {
            is SuperizerEvent.Reset -> if (event.appId == ctx.runtime.appId) cache.clear()
            is SuperizerEvent.Foreground -> ctx.runtime.logger.debug("host is back")
            else -> Unit
        }
    }
}
```

The listener runs on the app-level scope and is cancelled for you on disable.

---

## Push: subscribe once, receive always

```kotlin
// manifest
pushTopics = setOf("rates")

// setup — persisted by the host, re-applied after a token refresh
ctx.runtime.scope.launch { ctx.runtime.push.subscribe("rates") }

// anywhere with a runtime
runtime.scope.launch {
    runtime.push.messages.collect { message ->
        val base = message.data["base"]?.jsonPrimitive?.content ?: return@collect
        cache.invalidate(base)
    }
}
```

Collect on the **app-level** scope (in `setup`) if the app must react while closed; on the instance
scope if only the open screen cares. Ask for permission from a gesture, never at launch:

```kotlin
AppButton("Enable notifications", onClick = { runtime.scope.launch { runtime.push.requestPermission() } })
```

---

## A hidden, diagnostic app

```kotlin
metadata = AppMetadata(title = localized("en" to "Diagnostics"), hidden = true, category = "Service")

public companion object {
    public val PROMO_CODE: String = "DIAG-2026"
    public fun promoConfig(): AppConfig = AppConfigSpec(Cfg.serializer(), Cfg()).encode(Cfg(mode = "promo"))
}
```

The host puts the code in its table; the app stays invisible until somebody types it, and appears on
Home when they do. A tile saying "you cannot have this" would be an advertisement.

---

## Your own icon

```kotlin
icon = AppIcon.Path("M4 4h16v16H4V4zM11 11v2h2v-2z")
```

An SVG `d` attribute over a 24×24 viewBox, filled by the non-zero rule — so a hole must be wound
opposite to the shape around it. No host file changes; the catalog draws it. A malformed path falls
back to a letter.

---

## An optional service the host may not have

Declare the key next to the interface, in a module both sides can see:

```kotlin
public interface CameraService {
    public suspend fun scanQr(): String?
    public companion object { public val Key: ServiceKey<CameraService> = ServiceKey("camera") }
}
```

The host offers it:

```kotlin
Superizer.build(scope) { service(CameraService.Key, AndroidCamera()) }
```

The app either declares it as required —

```kotlin
requires = setOf(CameraService.Key)      // a host without it rejects the app, with a reason
…
val camera = runtime.require(CameraService.Key)
```

— or degrades:

```kotlin
val camera = runtime.service(CameraService.Key)
if (camera == null) showManualEntry() else showScanner()
```

---

## An app that navigates inside itself

```kotlin
private val _chrome = MutableStateFlow(AppChrome())
override val chrome: StateFlow<AppChrome> get() = _chrome

fun openDetail(id: String) {
    page = Page.Detail(id)
    _chrome.value = AppChrome(title = "Detail", canGoBack = true)
}

@Composable
override fun Content() {
    SystemBackHandler(enabled = page !is Page.List) { back() }   // deeper than the shell's: yours wins
    when (val p = page) { … }
}
```

The host's top bar follows `chrome`; back unwinds inside you first and leaves the host only when you
stop claiming it.

---

## Refusing to close

```kotlin
override suspend fun onCloseRequested(): Boolean = !hasUnsavedEdits
```

The host draws the "close anyway?" dialog. Never draw your own — a modal is global UI.

---

## Logging and analytics that can be told apart

```kotlin
runtime.logger.warn("rate fetch failed", cause)   // → W/app:currency-converter: rate fetch failed
runtime.analytics.event("convert", mapOf("pair" to "USD-EUR"))   // → currency-converter.convert
```

Use your own short names. The host prefixes the app id before anything leaves the process, which is
why two apps both reporting `error` are still two apps you can tell apart.
