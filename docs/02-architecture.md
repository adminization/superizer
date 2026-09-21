# Architecture

## 1. The one rule

> **An app is written as if it were already standalone.** The host is one possible runtime
> provider. An app never sees the host — only its `AppRuntime`.

Concretely, an app never accepts, stores, or depends on a `Superizer`, an `AppHandler`, or an
`AppRegistry`. The build fails if it tries (`./gradlew checkDependencyRules`). Everything else in
this document follows from that.

## 2. Modules

| Module | Contains | May depend on |
|---|---|---|
| `core` | the contract, runtime interfaces, registry, handler, events, activation model, the `Superizer` ports | — |
| `ui-theme` | design tokens only | `core` |
| `ui` | widgets, the scaffold, the host screens, the shell | `core`, `ui-theme` |
| `host` | service implementations, platform `actual`s, activation service, push routing, stores | `core` |
| `testing` | `FakeAppRuntime`, `runAppTest`, `AppContractTest` | `core`, `ui` |
| `apps/test-app` | the bench app: one card per runtime service | `core`, `ui-theme`, `ui`, `testing` |
| `fixture` | the smallest possible host, so the library can prove itself without a product | all of the above |

**An app depends on `core`, `ui-theme` and `ui`. Never on `host`, never on a host, never on another
app.**

Two edges deserve their reason spelled out:

- `ui` does **not** depend on `host`. The shell needs things only a host can do — settings,
  activation, routing, diagnostics — so those are declared as *ports* in `core` (`HostSettingsPort`,
  `ActivationPort`, `RoutePort`, `DiagnosticsPort`) and implemented in `host`. Apps depend on `ui`;
  if `ui` reached `host`, apps would reach `host` transitively and the boundary would be a comment.
- `core` does not depend on `compose.foundation`, only on `compose.runtime` — it needs
  `@Composable` and snapshot state and nothing that draws. That is why `AppIcon` *describes* an icon
  (a name, a letter, or an SVG path string) and `ui` draws it.

## 3. The two levels of runtime

```
AppRuntime       created on enable,  cancelled on disable   — one per app id
  └─ InstanceRuntime  created on launch, cancelled on dispose — one per open screen
```

| | `AppRuntime` | `InstanceRuntime` |
|---|---|---|
| Lives | enable → disable | launch → dispose |
| Seen by | `setup(ctx)`, `ctx.runtime`; Settings sections | `launch(runtime, config)`, `Content()` via `LocalAppRuntime` |
| Has | storage, network, logger, analytics, locale, haptics, push, auth, apps, events, lifecycle, clock, `service(key)`, `scope` | all of that **plus `navigation`**, and a `scope` that dies with the screen |
| Use for | push subscriptions, background refresh, event listeners | anything a screen starts |

`InstanceRuntime.scope` deliberately shadows `AppRuntime.scope`: work a screen starts must not
outlive the screen. Work that must survive a close goes on `ctx.runtime.scope` in `setup`.

Both levels are built by a `HostRuntimeFactory`, so a host, a standalone wrapper and a test can each
supply their own — and so the two levels can never be created by different things.

## 4. The lifecycle

Every arrow below emits a `SuperizerEvent`, which is what makes the lifecycle *observable* rather
than merely documented: the bench app renders the flow, the Service Menu logs it, and tests assert
the order.

```
register(app)            → Registered            (or RegistrationRejected — manifest does not fit)
  enable(id)
    createApp(runtime)
    app.setup(ctx)       → Enabled               (or SetupMismatch — setup exceeded the manifest)
  launch(id, config)
    close the open app   → Closed, Disposed
    createInstance       → RuntimeCreated
    configSpec.decode    → Configured            (or ConfigRejected)
    app.launch(...)      → Created
    instance.onLaunch()  → Launched
    instance.restore()   → Restored              (only when a snapshot exists)
                         → Active
  (host to background)   → Background            — onBackground(), snapshot written
  (host to foreground)   → Foreground            — onForeground()
  close()
    onCloseRequested()   → false vetoes; the host asks the user
    saveState()
    onClose()            → Closed
    dispose(); scope.cancel() → Disposed
  disable(id)            → Disabled              — disposers run in reverse, scope cancelled
```

`AppState` is published per app id in `handler.states`: `Registered, Enabled, Creating, Launching,
Active, Closing, Disposed, Failed`.

Two invariants worth knowing:

- **One open app at a time** (`handler.current`). Opening another closes the first, keeping its
  snapshot.
- **Nothing in the handler throws at the caller.** A failure becomes `Failed` plus an event, because
  the host has a screen for that and none for an exception.

## 5. What is declared vs. what is wired

`AppManifest` says *what* the app is and what it will use. `setup(ctx)` wires *how*. The registry
and the handler check that the two agree:

| Declared in the manifest | Checked against |
|---|---|
| `minHostContract` | `HostInfo.contractVersion` — too new → `RegistrationRejected` |
| `requires` (service keys) | `HostInfo.services` — missing → `RegistrationRejected` |
| `deepLinks` | what `setup` registered — a path not declared → `SetupMismatch`, app stays disabled |
| `pushTopics` | `push.subscribe(topic)` — an undeclared topic is refused and logged |
| `metadata.hidden` | the unlock store — a locked app cannot be opened, linked to, or pushed to |

Declared-but-not-wired is only a warning: a deep link may legitimately be registered conditionally.

## 6. Data ownership

| Where | Lifetime | Who erases it |
|---|---|---|
| `runtime.storage` | forever, namespaced `app.<id>.` | `DiagnosticsPort.reset(id)` |
| `AppInstance.saveState()` | one process death or background, TTL 24 h | close without `keepSnapshot`, reset, a failed restore |
| push topics | forever, prefixed `<id>.<topic>` at the transport | reset |
| unlock set | forever | reset, Service Menu "lock" |
| Home list | forever, the user's own order | long press on a tile, reset |

Isolation is by prefix, not by process. Real boundaries need a process boundary, which a bridge
would bring; a prefix is what is honest without one.

## 7. Contract versioning

`SuperizerContract.VERSION` is a plain integer (`1` today), **separate from the library's semver**.
An app declares the oldest host it tolerates as `manifest.minHostContract`; an older host *rejects*
the app with a reason the Service Menu shows, rather than crashing.

The version moves only when `checkLegacyAbi` shows an incompatible change to `core`, and the
CHANGELOG says so when it does. The public API of every module is dumped to `*/api/*.api`; **the
diff of those files in a pull request is the review of the contract.**

## 8. What is deliberately not here

- Dynamic app loading, a plugin system, anything needing reflection on wasm.
- A stack of open apps. There is exactly one. The snapshot mechanism makes a stack cheap to add.
- Adaptive layouts. Content is capped at 480 dp and centred.
- A dark theme. `Tokens` is a data class precisely so a second value is the whole change.
- FCM. `PushTransport` is an `expect object` with a no-op `actual` on all three targets; the router,
  the topic store, the Service Menu's simulator and the bench app all work against it today.
- A camera for QR. A payload is a string; where the string came from is the platform's problem.
