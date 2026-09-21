# Testing

`cx.m42.superizer:testing:0.1.0`, in the test source set. Three layers, in the order you will want
them:

1. **The TCK** — one line, checks what a host assumes about every app.
2. **`runAppTest`** — your app on fakes, composed, no host.
3. **Host tests** — the shell with your app in it, for the few things that are about the *product*.

---

## 1. `AppContractTest` — the TCK

```kotlin
class HelloContractTest : AppContractTest(HelloApp())
```

That inherits ten tests:

| Test | What it proves |
|---|---|
| `theDefaultConfigDecodes` | an empty payload opens the app |
| `aBrokenPayloadIsAFailureAndNotASilentDefault` | a malformed config either decodes honestly or fails *with a reason* |
| `everyFixtureStillReadsWithTheCurrentSpec` | every payload in `configFixtures` still decodes |
| `setupRegistersOnlyWhatTheManifestDeclares` | no deep link outside the manifest |
| `theManifestSurvivesARoundTripThroughJson` | id, `requires` and `deepLinks` come back equal |
| `launchingIsCheapAndTouchesNoNetworkBeforeTheFirstFrame` | `onLaunch` ≤ 200 ms of virtual time, `runtime.network.calls` empty |
| `aSnapshotSurvivesARoundTrip` | `saveState → restore → saveState` is stable, into a **fresh** instance |
| `everyStateFixtureCanStillBeRestored` | `restore` does not throw on an older snapshot |
| `closingIsAllowedByDefaultAndDisposeIsIdempotent` | `dispose()` twice is safe (the handler and shutdown can meet) |
| `theScreenComposesAndHasTheRootTagTheContainerPutsOnIt` | `Content()` composes at all |

Three hooks:

```kotlin
class ConverterContractTest : AppContractTest(ConverterApp()) {
    // Older payloads this app must still read. Raw strings, not files: a resource loader that
    // behaves the same in desktopTest, Robolectric and Karma does not exist.
    override val configFixtures = listOf(
        """{"base":"USD"}""",
        """{"schemaVersion":1,"base":"EUR","favorites":["USD"]}""",
    )
    // The same for saveState snapshots an update may be handed.
    override val stateFixtures = listOf("""{"amount":"100"}""")
    // A config that exercises more than the default would.
    override val sampleConfig = AppConfig(buildJsonObject { put("base", JsonPrimitive("EUR")) })
}
```

Add a fixture string **every time you change the config or snapshot shape**. That is the entire
migration test suite, and it costs one line.

---

## 2. `runAppTest` — the app, composed, on fakes

```kotlin
public fun <C : Any> runAppTest(
    app: SuperizerApp<C>,
    config: AppConfig = AppConfig.Empty,
    restore: JsonObject? = null,
    prepare: (FakeAppRuntime) -> Unit = {},
    block: AppTestScope<C>.() -> Unit,
)
```

It decodes the config, calls `launch`, runs `onLaunch`, applies `restore` if given, provides
`LocalAppRuntime`, wraps `Content()` in a `<appId>:root` box, and hands you:

```kotlin
class AppTestScope<C : Any>(
    val app: SuperizerApp<C>,
    val instance: AppInstance,
    val runtime: FakeAppRuntime,   // the fakes, with their innards visible
    val ui: ComposeUiTest,         // the escape hatch for anything below
) {
    fun node(tag: String): SemanticsNodeInteraction
    fun click(tag: String)
    fun type(tag: String, text: String)
    fun assertShown(tag: String)
    fun assertAbsent(tag: String)
    fun idle()
}
```

```kotlin
@Test
fun aRateArrivesAndTheResultUpdates() = runAppTest(
    ConverterApp(),
    prepare = { it.network.respond(ConverterConfig.DEFAULT_RATES_URL, """{"rates":{"EUR":0.9}}""") },
) {
    type("currency-converter:amount", "100")
    click("currency-converter:convert")
    assertShown("currency-converter:result")
    assertEquals(1, runtime.network.calls.size)
}

@Test
fun theHalfTypedSumComesBack() = runAppTest(
    CalculatorApp(),
    restore = buildJsonObject { put("entry", JsonPrimitive("12+")) },
) {
    assertShown("calculator:display")
}
```

The test scope runs on an **unconfined** dispatcher, so work an app starts in `runtime.scope` has
already happened by the time the test looks.

Find nodes by tag, never by text — a suite that asserts on Russian strings fails the day somebody
adds a third table.

---

## 3. The fakes

Every one of them is twenty lines, and that is a property of the *contract*: the service interfaces
take strings and JSON, so a fake cannot become an implementation in disguise.

| Fake | What it exposes to a test |
|---|---|
| `InMemoryStorage(initial)` | `entries: MutableMap<String, String>` |
| `FakeNetwork` | `respond(url, body, status)`, `respond(url) { req -> … }`, `fail(url, msg)`, `calls: MutableList<NetworkRequest>`, `online: MutableStateFlow<Boolean>`. **A URL with no stub throws** — a test that hits an unnamed endpoint is a test about to reach the real network. |
| `RecordingLogger` | `lines: MutableList<String>` |
| `RecordingAnalytics` | `events`, `screens` |
| `RecordingNavigation` | `opened`, `closed`, `settingsOpened`, `urls`, and `unknown: MutableSet<AppId>` to make `openApp` fail |
| `FakeLocale(tag)` | `langTag: MutableStateFlow<String>` |
| `FakeClock(millis)` | `advance(millis, seconds, minutes, hours)` |
| `RecordingHaptics` | `ticks: Int` |
| `FakePush` | `deliver(topic, data, link, receivedAt)`, `subscriptions`, `permissionGranted` |
| `FakeAuth(session)` | `session: MutableStateFlow<AuthSession?>` |
| `FakeApps(summaries)` | plus `appSummary(id, metadata, …)` to build one |
| `InMemorySnapshotStore` | `snapshot: SessionSnapshot?` |
| `FakeAppRuntime(appId, scope, hostName, debug, declaredServices)` | all of the above as an `InstanceRuntime`; `provide(key, impl)`, `emittedEvents`, `hostLifecycle` |
| `FakeHostRuntimeFactory(hostInfo)` | for a handler under test; `created: Map<AppId, FakeAppRuntime>` |

`FakeAppRuntime` offers **exactly** the service keys the manifest declares. An app that calls
`require(key)` on something it forgot to declare fails in its first test rather than as a null in
production.

---

## 4. Host-level tests

For the things that are about the product rather than an app: what a fresh install shows, whether
the drawer works, whether a promo code unlocks the right thing.

```kotlin
@OptIn(ExperimentalTestApi::class)
class ShellTest {

    private fun testSuperizer(): Superizer {
        PrefsStorage.useDirectory(Files.createTempDirectory("myhost-test").toFile())
        return buildMyHost(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), debug = true)
    }

    @Test fun aFreshInstallHasHelloOnHome() = runComposeUiTest {
        setContent { MyHostApp(testSuperizer()) }
        waitForIdle()
        onNodeWithTag("home:tile-hello").assertIsDisplayed()
        onNodeWithTag("home:tile-test-app").assertDoesNotExist()   // hidden
    }

    @Test fun aPromoCodeUnlocksTheBenchApp() = runComposeUiTest {
        setContent { MyHostApp(testSuperizer()) }
        onNodeWithContentDescription("Open menu").performClick()
        onNodeWithContentDescription("Activate").performClick()
        onNodeWithTag("activate:promo").performTextInput(TestApp.PROMO_CODE)
        onNodeWithTag("activate:apply").performClick()
        waitForIdle()
        onNodeWithTag("test-app:root").assertIsDisplayed()
    }
}
```

Give every test its **own** preferences directory — `PrefsStorage.useDirectory(tmp)` — or Home,
unlocks and the session snapshot leak between them and the order of the suite starts to matter.

Substitute anything that would reach outside the process:

```kotlin
buildMyHost(scope, debug = true) {
    network(FakeNetwork().apply { respond("https://api…", "{}") })
    clock(FakeClock())
    lifecycle(MutableStateFlow(HostLifecycle.Foreground))
}
```

(Give your `buildMyHost` a trailing `extra: SuperizerBuilder.() -> Unit = {}` parameter, as Unitool
does, and a test can add or override anything without a second host function.)

---

## 5. Screenshots

The desktop target composes the same `commonMain` tree every other target does, so screenshots need
no emulator, browser or window server:

```kotlin
runDesktopComposeUiTest(width = 412, height = 915) {
    setContent { CompositionLocalProvider(LocalDensity provides Density(2.625f)) { MyHostApp(host) } }
    onRoot().captureToImage().toAwtImage().let { ImageIO.write(it, "PNG", file) }
}
```

Run one class only: `./gradlew :app:desktopTest --tests '*Screenshots*'`. A test can be green while
the screen is blank — screenshots are meant to be *looked at*.

---

## 6. Web

`wasmJsTest` runs on Karma and needs a browser binary; set `CHROME_BIN`. Where there is none, the
task is disabled rather than failing with a download error — `scripts/verify.sh` reports the skip
by name.

A canvas has no DOM to query, so Playwright cannot find nodes. The fixture publishes a read-only
bridge as `window.__superizer` — and only when opened with `?test=1`:

```js
window.__superizer.appCount()    // the build started at all
window.__superizer.currentApp()  // a route reached the handler
window.__superizer.events()      // the lifecycle ran
window.__superizer.destination() // "Home" | "App"
```

Copy that pattern into your own host if you want the same three classes of wasm regression caught.

---

## 7. What CI runs

```bash
./scripts/verify.sh
```

1. `checkDependencyRules` and `checkLegacyAbi` (stale API dumps fail — run `updateLegacyAbi` and
   commit the diff);
2. `desktopTest --continue` across every module;
3. `wasmJsTest` when a Chromium is available;
4. the wasm bundle plus the Playwright smoke test;
5. `publishToMavenLocal` and then a build of `samples/consumer`, which resolves the library by
   coordinates from a separate build — the only step that can prove a *publication*.

`publish.yml` runs `./gradlew verify publish` in one invocation, so an artifact nobody can build
against is never uploaded: Gradle does not reach the publish tasks if a test task fails.

One report at `build/verify/report.md`, non-zero exit unless every line passes, skips always named.
