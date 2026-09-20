# Superizer

A Compose Multiplatform host framework: independent apps plug into a host through one contract and
reach the platform through one object.

The rule the whole library is built on:

> **An app is written as if it were already standalone.** The host is one possible runtime provider.
> An app never sees the host — only its `AppRuntime`.

That is Adminizer's "App Boundary" rule, kept word for word. An app does not accept, store or depend
on a `Superizer`, an `AppHandler` or an `AppRegistry`; the build fails if it tries.

Targets: Android, desktop (JVM), wasm in the browser. MIT licensed.

## Writing an app

An app is one public class. Everything else in the module is `internal`.

```kotlin
@Serializable
public data class CalculatorConfig(val mode: String = "standard")

public class CalculatorApp : SuperizerApp<CalculatorConfig>() {

    // The static half: what the host may know before running a line of your code.
    override val manifest = AppManifest(
        id = AppId("calculator"),
        version = "1.1.0",
        metadata = AppMetadata(
            title = localized("en" to "Calculator", "ru" to "Калькулятор"),
            icon = AppIcon.Named("calculator"),      // or AppIcon.Path("M4 4h16…") — your own SVG
            category = "Tools",
        ),
        deepLinks = setOf("rate"),                   // paths you serve; setup() must register these
        pushTopics = setOf("rates"),                 // topics you may subscribe to
    )

    override val configSpec = AppConfigSpec(CalculatorConfig.serializer(), CalculatorConfig())

    // Once per enable: wire the behaviour behind the manifest. Everything registered here is torn
    // down for you on disable, in reverse order.
    override fun setup(ctx: AppSetupContext) {
        ctx.deepLink("rate") { params -> configSpec.encode(CalculatorConfig(params["mode"] ?: "standard")) }
    }

    // Once per open: cheap. Anything slow belongs in AppInstance.onLaunch.
    override fun launch(runtime: InstanceRuntime, config: CalculatorConfig) =
        CalculatorInstance(runtime, config)
}
```

An instance is one open screen and its ending:

```kotlin
internal class CalculatorInstance(
    private val runtime: InstanceRuntime,
    private val config: CalculatorConfig,
) : AppInstance() {

    override suspend fun onLaunch() { runtime.analytics.screen("calculator") }

    // What the screen would lose if the process died now. The host stores it and hands it back.
    override fun saveState(): JsonObject = buildJsonObject { put("entry", JsonPrimitive(entry)) }
    override fun restore(state: JsonObject) { entry = state["entry"]?.jsonPrimitive?.content ?: "0" }

    // No top bar, no drawer, no back handler: the host draws the frame around this.
    @Composable override fun Content() { CalculatorScreen(this) }

    // Return false and the host asks the user before closing you.
    override suspend fun onCloseRequested(): Boolean = true
}
```

## What an app gets

`runtime` is the whole of the platform an app can see. `AppRuntime` lives from enable to disable —
push topics and background work run on it — and `InstanceRuntime` adds `navigation` and a scope that
dies with the screen.

| | |
|---|---|
| `storage` | key/value that survives a restart, namespaced to your app id |
| `network` | strings in, strings out; `online` is a signal, not a guess |
| `logger`, `analytics` | tagged and prefixed with your app id by the host |
| `locale` | the language tag in effect, and `pick(tables, fallback)` |
| `haptics` | one tick; the host's setting is already applied |
| `push` | topics and a flow of messages — no token, no notifications, no navigation |
| `auth`, `apps`, `clock`, `lifecycle`, `events` | session, a read-only registry view, injectable time, foreground/background, the host's event flow |
| `navigation` | `openApp`, `close`, `openSettings`, `openUrl` — and nothing else |
| `service(key)` | optional services a host may or may not have |

Two rules keep this honest and are worth knowing before you add to it:

- **Nothing in a service interface takes a type that cannot be JSON.** No `KSerializer`, no
  `Painter`, no platform class.
- **Every suspending call is main-safe.** Call them from the main dispatcher without `withContext`.

## Testing an app

No host required.

```kotlin
class CalculatorContractTest : AppContractTest(CalculatorApp())   // the TCK, in one line

class CalculatorScreenTest {
    @Test fun tappingKeysMovesTheDisplay() = runAppTest(CalculatorApp()) {
        click("calculator:key-7")
        click("calculator:key-=")
        assertShown("calculator:display")
    }
}
```

`runAppTest` launches your app on fakes — `InMemoryStorage`, `FakeNetwork`, `FakeClock`,
`RecordingAnalytics` — inside a container that behaves like the host's. `AppContractTest` checks the
things a host *assumes* and therefore never re-checks at runtime: that your default config decodes,
that a broken one is reported rather than swallowed, that `saveState` → `restore` round-trips, that
`setup` registers only what the manifest declares, that `onLaunch` is under 200 ms and makes no
network call, and that `Content()` composes.

Find nodes by test tag, `<appId>:<element>`. Text changes with the language; tags do not.

## Building a host

```kotlin
val superizer = Superizer.build(scope) {
    host(HostInfo("Unitool", version, build, currentPlatform(), SuperizerContract.VERSION, debug = BuildConfig.DEBUG))
    scheme("unitool")                               // unitool://app/<id>/<path>
    register(CalculatorApp())
    register(CurrencyConverterApp())
    promoCodes(mapOf("SCI" to ActivationResult.Success(Activation(AppId("calculator"), scientific))))
    serviceCode("SERVICE")                          // the one door into the Service Menu in release
}

@Composable fun App() = SuperizerShell(superizer)
```

Registration is explicit — no classpath scanning, which does not exist on wasm and which R8 would
have to be told about. Reading this block tells you exactly what is in a build.

You get: All Apps, the drawer, Settings (with a section per app), Activate (promo code and QR text),
the Service Menu, the app container, deep links, push routing, session restore after a process
death, and the error screen a failed launch lands on.

## Contract versioning

`SuperizerContract.VERSION` is an integer, separate from the library's version. An app declares the
oldest host it tolerates as `manifest.minHostContract`; a host that is older **rejects** the app with
a reason the Service Menu shows, rather than crashing. The public API of every module is dumped to
`*/api/*.api` and checked by `checkLegacyAbi`; the diff of those files in a pull request is the
review of the contract.

## Layout

| Module | What is in it |
|---|---|
| `core` | the contract, the runtime interfaces, the registry, the handler, the events |
| `ui-theme` | design tokens only, so a third-party component set can match a host without taking on its components |
| `ui` | components, the scaffold, the host screens, the shell |
| `host` | service implementations, platform `actual`s, activation, push routing |
| `testing` | `FakeAppRuntime`, `runAppTest`, `AppContractTest` |
| `apps/test-app` | the bench app: one card per runtime service |
| `fixture` | the smallest possible host, so the library can prove itself without a product |
| `samples/consumer` | a separate build that resolves the library from a repository |

Apps depend on `core`, `ui-theme` and `ui`. Never on `host`, never on each other, never on a host —
`./gradlew checkDependencyRules` fails the build if they do.

## Verifying

```bash
./scripts/verify.sh        # static checks, tests, wasm, the web smoke test, publish, one report
```

The report lands in `build/verify/report.md` and exits non-zero unless every line passes. Skips are
always named.

## What is deliberately not here

Dynamic app loading, a plugin system, and anything that would need reflection on wasm. A stack of
open apps (there is exactly one open app at a time). Adaptive layouts. A dark theme — the tokens are
ready for one, the values are not written. FCM: the push transport is an `expect object` with a
no-op `actual` on all three targets, so the router, the topic store and the bench app all work
today; adding Firebase is one `actual` file.
