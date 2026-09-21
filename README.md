# Superizer

A Compose Multiplatform host framework: independent apps plug into a host through one contract and
reach the platform through one object.

The rule the whole library is built on:

> **An app is written as if it were already standalone.** The host is one possible runtime provider.
> An app never sees the host — only its `AppRuntime`.

That is Adminizer's "App Boundary" rule, kept word for word. An app does not accept, store or depend
on a `Superizer`, an `AppHandler` or an `AppRegistry`; the build fails if it tries.

Targets: Android, desktop (JVM), wasm in the browser. MIT licensed.

**Full documentation is in [`docs/`](docs/)** — [getting started from scratch](docs/01-getting-started.md),
[the architecture](docs/02-architecture.md), [writing an app](docs/03-app-guide.md), a reference for
[`core`](docs/04-core-api.md), [`host`](docs/05-host-api.md) and [`ui`](docs/06-ui-api.md),
[testing](docs/07-testing.md), [activation, deep links and push](docs/08-activation-routing-push.md),
[recipes](docs/09-recipes.md), a [brief to hand an agent](docs/10-new-app-prompt.md), and
[troubleshooting](docs/11-troubleshooting.md). What follows here is the two-minute version.

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

You get: Home (the apps the user chose, starting from `home("calculator")`), All Apps (the catalog
a tile is added from; a long press takes one off), the drawer, Settings (with a section per app),
Activate (promo code and QR text — activating a hidden app also puts it on Home), the Service Menu,
the app container, deep links, push routing, session restore after a process death, and the error
screen a failed launch lands on.

## Getting the library

Published to GitHub Packages as `cx.m42.superizer`. **The branch decides the channel and the
registry decides the number** — nobody edits a version to cut a release, and there are no tags.

| Push to | You get | For |
|---|---|---|
| `main` / `master` | `0.1.1` — the last release, patch bumped | the release |
| `next` | `0.1.1-next.3` | a release being prepared |
| `alpha` | `0.1.1-alpha.0` — its own counter | something being tried |
| `commit` | `0.1.0-commit.d66035c` | one build, pinned by the commit in it |

`superizer.version` and `superizer.appsVersion` in `gradle.properties` are **floors**, not
decisions: `scripts/resolve-version.sh` asks the registry what exists and goes one past it, and a
floor only wins while it is higher. Raise one by hand to start a new minor or major series. The
script runs on a laptop too, which is how you find out what the next push will publish:

```bash
GPR_USER=… GPR_TOKEN=… ./scripts/resolve-version.sh release cx/m42/superizer/core 0.1.0
```

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven("https://maven.pkg.github.com/adminization/superizer") {
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                    ?: providers.environmentVariable("GITHUB_ACTOR").orNull
                password = providers.gradleProperty("gpr.key").orNull
                    ?: providers.environmentVariable("GITHUB_TOKEN").orNull
            }
        }
    }
}
```

```kotlin
// build.gradle.kts — an app needs the first three, a host needs all four
implementation("cx.m42.superizer:core:0.1.1")
implementation("cx.m42.superizer:ui-theme:0.1.1")
implementation("cx.m42.superizer:ui:0.1.1")
implementation("cx.m42.superizer:host:0.1.1")
testImplementation("cx.m42.superizer:testing:0.1.1")
```

Maven has no dist-tags, so there is no `latest` to follow and no floating version to resolve: pin
the number. To track a channel instead, name it — `0.1.+` for the releases, or the exact
`-next.N` you want. A version catalog is the right place for it, so a bump is one line.

**The credentials are not optional.** GitHub Packages authenticates downloads even for a public
repository — a build with no token gets a 401, not an anonymous read. Locally, put a classic PAT
with the `read:packages` scope in `~/.gradle/gradle.properties`:

```properties
gpr.user=your-github-username
gpr.key=ghp_…
```

In another repository's Actions, `secrets.GITHUB_TOKEN` is enough **only** for a repository inside
the same organisation with access granted to the package; anywhere else, pass an organisation
secret holding a PAT. This is the one real cost of GitHub Packages over Maven Central, and it is
worth knowing before wiring it into a public CI.

Publishing is the mirror image, and `publish.yml` does it for you. To publish by hand, the
repository name decides the property names Gradle looks for:

```bash
ORG_GRADLE_PROJECT_GitHubPackagesUsername=… ORG_GRADLE_PROJECT_GitHubPackagesPassword=…   ./gradlew publish -Psuperizer.version=0.1.1 -Psuperizer.appsVersion=1.0.1
```


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
