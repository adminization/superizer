# Superizer documentation

Superizer is a Compose Multiplatform **host framework**: independent apps plug into a host through
one contract and reach the platform through one object.

> **The rule the whole library is built on.** An app is written as if it were already standalone.
> The host is one possible runtime provider. An app never sees the host — only its `AppRuntime`.

Targets: Android, desktop (JVM), wasm in the browser. MIT licensed.
Source: <https://github.com/adminization/superizer>.

## Read in this order

| # | Document | What it answers |
|---|---|---|
| 1 | [Getting started](01-getting-started.md) | From an empty directory to a running Super App on three targets. How to get the library (mavenLocal, composite build, GitHub Packages). |
| 2 | [Architecture](02-architecture.md) | What the modules are, who may depend on whom, and the lifecycle an app goes through from registration to disposal. |
| 3 | [Writing an app](03-app-guide.md) | The complete, step-by-step guide to building a new app. **This is the one to hand to an agent.** |
| 4 | [`core` API reference](04-core-api.md) | Every public type and method of the contract: app, manifest, config, runtime, services, registry, handler, events, activation. |
| 5 | [`host` API reference](05-host-api.md) | `Superizer.build { … }`, every builder option, the host services, and what each platform needs initialised. |
| 6 | [`ui` API reference](06-ui-api.md) | Design tokens, widgets, icons, the scaffold, the host screens, i18n, and the test-tag convention. |
| 7 | [Testing](07-testing.md) | `runAppTest`, the `AppContractTest` TCK, the fakes, host-level shell tests, screenshots, the web smoke test. |
| 8 | [Activation, deep links, push](08-activation-routing-push.md) | QR payloads, promo codes, `scheme://app/<id>/<path>`, push payload schema 1, and the routing priorities at startup. |
| 9 | [Recipes](09-recipes.md) | Cookbook: config from a payload, a Settings block, offline cache, opening another app, hidden apps, custom icons, optional services. |
| 10 | [New-app prompt template](10-new-app-prompt.md) | A fill-in-the-blanks brief to give an agent: "build app X that does Y, Z". |
| 11 | [Troubleshooting](11-troubleshooting.md) | Every rejection reason, every dropped push, and what each one actually means. |

## The shortest possible summary

An **app** is one public class:

```kotlin
public class CalculatorApp : SuperizerApp<CalculatorConfig>() {
    override val manifest = AppManifest(id = AppId("calculator"), version = "1.1.0", metadata = …)
    override val configSpec = AppConfigSpec(CalculatorConfig.serializer(), CalculatorConfig())
    override fun setup(ctx: AppSetupContext) { /* deep links, Settings block, listeners */ }
    override fun launch(runtime: InstanceRuntime, config: CalculatorConfig) =
        CalculatorInstance(runtime, config)
}
```

A **host** is one function:

```kotlin
val superizer = Superizer.build(scope) {
    host(HostInfo("Unitool", version, build, currentPlatform(), SuperizerContract.VERSION, debug))
    scheme("unitool")
    register(CalculatorApp())
    home("calculator")
}

@Composable fun App() = SuperizerShell(superizer)
```

Everything else in this directory is detail behind those two blocks.

## Versions

| Thing | Value |
|---|---|
| Library version | `0.1.0` (one version for every module) |
| Contract version | `SuperizerContract.VERSION = 1` |
| Group | `cx.m42.superizer` |
| Kotlin / Compose MP / AGP | 2.4.10 / 1.11.1 / 8.13.2 |
| Android minSdk / compileSdk | 24 / 36 |
| JVM target | 11 (library), 21 toolchain for the sample consumer |

The library's version and the contract version move independently: see
[Architecture § Contract versioning](02-architecture.md#contract-versioning).
