# Activation, deep links and push

Four doors into the same room. **An app never learns which one it came through** — it receives a
config, and that is all. One code path, one set of unlock rules.

```
QR payload ─┐
promo code ─┼→ ActivationPort → Activation(appId, config, unlock) → handler.launch(force = true)
deep link  ─┤
push tap   ─┘
```

---

## 1. The QR payload

Two forms, one parser. Both are accepted by `activation.fromQr(text)` and by the Activate screen's
text field.

**JSON** — what a printed code usually carries:

```json
{
  "schemaVersion": 1,
  "type": "app_activation",
  "appId": "calculator",
  "config": { "mode": "scientific" }
}
```

**URL** — what a long code degrades to:

```
unitool://activate?a=calculator&c=eyJtb2RlIjoic2NpZW50aWZpYyJ9
unitool://activate?appId=calculator&config={"mode":"scientific"}
```

`c` / `config` is base64url first, then plain JSON. A *broken config inside a valid activation* is
not a parse rejection — the activation succeeds and the handler reports `ConfigRejected`, because
those are two different failures and they are shown to two different people.

Rejections (`ActivationResult.Rejected(reason)`): `Malformed`, `UnsupportedSchema` (a
`schemaVersion` that is not 1), `UnknownApp` (not registered), `UnknownCode`, `Expired`. Nothing
throws — the whole input is text somebody else produced.

**A QR activation unlocks.** `Activation.unlock` defaults to `true`, so a payload can reveal a
hidden app.

---

## 2. Promo codes

```kotlin
promoCodes(
    mapOf(
        "SCI" to ActivationResult.Success(
            Activation(AppId("calculator"), AppConfig(buildJsonObject { put("mode", JsonPrimitive("scientific")) })),
        ),
        TestApp.PROMO_CODE to ActivationResult.Success(Activation(AppId("test-app"), TestApp.promoConfig())),
    ),
)
```

Codes are normalised — everything but letters and digits is stripped and the rest upper-cased — so
`test 2026`, `TEST-2026` and `test2026` are one code. These are read off paper and typed by hand.

A server-backed table is the same interface:

```kotlin
promoCodes(PromoCodeResolver { code -> api.resolve(code) })
```

`serviceCode("SERVICE")` is matched **before** the table and resolves to
`ActivationResult.HostCommand(OpenServiceMenu)` — a host command, not a reserved app id, because one
magic name always becomes several.

---

## 3. Deep links

```
<scheme>://app/<appId>                  → open the app on an empty config
<scheme>://app/<appId>/<path>?k=v       → open it with the config the app's handler builds
<scheme>://activate?a=<id>&c=<payload>  → the QR form, arriving by another route
<anything else>                         → parsed as a QR payload
```

The app declares the paths it serves and registers one handler each:

```kotlin
// manifest
deepLinks = setOf("rate")

// setup
ctx.deepLink("rate") { params ->
    configSpec.encode(ConverterConfig(from = params["from"], to = params["to"]))
}
```

Rules:

- **A deep link does not unlock** (`unlock = false`). Naming an app is not knowing a secret about
  it. A link to a hidden, locked app is rejected as `UnknownApp` — which is also the honest answer,
  since the sender has no business knowing it exists.
- **An unknown path is soft**: the app opens on an empty config and the host emits
  `DeepLinkUnmatched`. A link from a newer build should not be a dead end.
- Paths are validated at registration: `[a-z0-9][a-z0-9-]*(/[a-z0-9][a-z0-9-]*)*`, no query, no
  scheme. `rate` and `chart/day` are fine; `rate?pair=X` is not — the query is data, not the path.
- Query strings are percent-decoded; repeated keys keep the first.

### How a link reaches the host

Whatever delivers it calls `superizer.route.deliver(link)`. The shell consumes it **once**, runs it
through `activation.fromDeepLink`, and applies the result.

| Platform | Delivery |
|---|---|
| Android | `intent.data` in `onCreate` **and** `onNewIntent`; `singleTask` + a `VIEW` filter for the scheme |
| Desktop | `--link=` / `--activate=` on the command line |
| Web | `?link=` / `?activate=` in the query, percent-decoded |

`RoutePort` is consume-once on purpose: a route is an instruction, and a `StateFlow` that kept
handing it back would reopen the app on every recomposition. If the open app vetoes its close and
the user chooses to stay, the shell calls `discard()` and the host emits `RouteDiscarded`.

---

## 4. Push

### Payload — schema 1

FCM `data` is `Map<String, String>`, so every value is a string, including the version.

| Key | Required | Meaning |
|---|---|---|
| `schemaVersion` | yes | `"1"`. Anything else → `PushDropped(UnsupportedSchema)` |
| `appId` | yes | must be registered → else `PushDropped(UnknownApp)` |
| `topic` | no | passed through to the app |
| `data` | no | a JSON **object as a string**; parsed into `PushMessage.data` |
| `link` | no | must start with `<scheme>://app/<appId>` → else `PushDropped(LinkMismatch)` |
| `silent` | no | `"true"` suppresses the notifier |
| `title`, `body` | no | for the notifier |

### The two roads

```
tapped == true   → the screen road: the link goes into PendingRoute, and from there through the
                   very same parser, unlock rules and handler a QR code uses.
tapped == false  → the data road: PushMessage lands on the app-level PushService.messages, and the
                   host emits PushReceived. If `silent` is not "true", the Notifier is asked to
                   draw something.
```

The data road reaches an app **with no screen open** — that is what the app-level runtime is for.
Subscribe in `setup`, collect on `ctx.runtime.scope`.

### The rules

- A push **never unlocks** a hidden app. A notification is something anyone with the app id can
  cause; unlocking is what a QR code or a promo code does, because those carry a secret.
- A locked app, a disabled app, a mismatched link and an unknown id are all dropped with a named
  reason, visible in the Service Menu.
- Topics are namespaced before they reach the transport: `subscribe("rates")` on `currency-converter`
  becomes `currency-converter.rates`.
- `subscribe` refuses a topic the manifest does not declare, and logs a warning.
- Subscriptions are persisted by the host and re-applied after a token refresh, so one `subscribe()`
  in `setup` is enough.

### Without Firebase

`PushTransport` is an `expect object` whose `actual` is a no-op on all three targets. Everything
else — the router, the topic store, the bench app's Push card, the Service Menu's simulator —
already works:

```kotlin
superizer.diagnostics.simulatePush(
    mapOf(
        "schemaVersion" to "1",
        "appId" to "currency-converter",
        "topic" to "rates",
        "data" to """{"base":"USD"}""",
        "silent" to "true",
    ),
)
// add "tapped" to "true" to take the screen road instead
```

Adding FCM is one `actual` file and a `google-services.json`. If it turns out to be more than that,
the seam was drawn wrong.

---

## 5. Startup priority

```
1. a link already waiting   (a notification tap into a dead process, ?link=, an intent)
2. the session snapshot     (≤ 24 h old, the app still registered, still unlocked)
3. Home
```

Any other order would open yesterday's screen over the notification someone just tapped.

A restored session does **not** use `force`: a snapshot is yesterday's screen, not an activation, so
a hidden app that was locked again in between does not walk back in through it. A failed restore
clears the snapshot.

---

## 6. Hidden apps, unlock and Home

| Action | Unlocks? | Adds to Home? |
|---|---|---|
| QR payload / promo code (`unlock = true`) | yes | yes — whatever reveals an app also puts it on Home |
| Deep link naming an app | no | no |
| Push | no | no |
| `Superizer.addToHome(id)` | no (a locked id is ignored) | yes |
| Service Menu → unlock | yes | yes |
| Service Menu → lock | reverses it | removes the tile, closes the app, forgets the snapshot |
| Service Menu → reset | same as lock, **plus** storage and topics erased | removes |
| Long press on a tile | no | removes only — the app stays unlocked and in the catalog |

Home is a **chosen, ordered** set. Ids that are no longer registered or unlocked stay in the store
and are skipped by the shell, so a build that drops an app and a later build that brings it back do
not lose the user's arrangement in between.
