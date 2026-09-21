# Getting started

From an empty directory to a Super App running on Android, desktop and the browser.

Everything below is copy-pasteable. If you only want to *write an app* for a host somebody else
built, skip to [Writing an app](03-app-guide.md) — you need §1 and §2 of this page and nothing else.

---

## 1. Prerequisites

| Tool | Version | Why |
|---|---|---|
| JDK | 21 (17 works; the samples set a 21 toolchain) | Gradle and the Kotlin compiler |
| Android SDK | compileSdk 36, minSdk 24 | only for the Android target |
| Node | 22 | only for the web smoke test in `scripts/verify.sh` |
| Chromium | any | only for `wasmJsTest` (Karma) — set `CHROME_BIN` |

Gradle comes with the wrapper. No global Kotlin install is needed.

---

## 2. Getting the library

The coordinates are the same in all three cases. **Always write Maven coordinates, never
`project(":core")`** — that is what lets you switch between the modes below without editing a line.

| Module | Coordinate | What it is |
|---|---|---|
| core | `cx.m42.superizer:core:0.1.0` | the contract. Apps and hosts both need it. |
| ui-theme | `cx.m42.superizer:ui-theme:0.1.0` | design tokens only |
| ui | `cx.m42.superizer:ui:0.1.0` | widgets, scaffold, host screens, the shell |
| host | `cx.m42.superizer:host:0.1.0` | service implementations, platform actuals, activation, push. **Hosts only.** |
| testing | `cx.m42.superizer:testing:0.1.0` | fakes, `runAppTest`, `AppContractTest` (test scope) |
| test-app | `cx.m42.superizer.apps:test-app:1.0.0` | the bench app: one card per runtime service. Optional. |

Apps version themselves; `test-app` is at `1.0.0` while the library is at `0.1.0`, and that is
deliberate — the library's version says nothing about an app's.

### Option A — local Maven (the default while developing)

```bash
git clone https://github.com/adminization/superizer.git
cd superizer
./gradlew publishToMavenLocal
```

That writes every module into `~/.m2/repository/cx/m42/superizer/`. In the consuming build:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenLocal()          // first, deliberately: resolve what you just published
        google()
        mavenCentral()
    }
}
```

### Option B — composite build (a checkout next door)

When the library's sources sit beside your host, Gradle can substitute them for the coordinates —
`npm link`, in Gradle. A change to the contract is then visible on your next build with no publish
step.

```kotlin
// settings.gradle.kts  (in your host repository)
val superizerDir = file("../superizer")
if (superizerDir.exists() && System.getenv("SUPERIZER_FROM_MAVEN") != "1") {
    includeBuild(superizerDir)
}
```

Nothing else changes: your build files keep naming `cx.m42.superizer:core`, and the escape hatch
(`SUPERIZER_FROM_MAVEN=1`) proves the published artifacts are still complete. This is exactly what
Unitool does.

### Option C — GitHub Packages

`.github/workflows/publish.yml` publishes to `https://maven.pkg.github.com/adminization/superizer`.
**The branch decides the channel and the registry decides the number** — nobody edits a version to
release, and there are no tags:

| Push to | Coordinates | For |
|---|---|---|
| `main` / `master` | `cx.m42.superizer:*:0.1.1` — the last release, patch bumped | the release |
| `next` | `cx.m42.superizer:*:0.1.1-next.3` | a release being prepared |
| `alpha` | `cx.m42.superizer:*:0.1.1-alpha.0` — its own counter | something being tried |
| `commit` | `cx.m42.superizer:*:0.1.0-commit.86f0a07` | one build, pinned by the commit in it |

`superizer.version` and `superizer.appsVersion` in `gradle.properties` are **floors**, not
decisions: [`scripts/resolve-version.sh`](https://github.com/adminization/superizer/blob/master/scripts/resolve-version.sh)
asks the registry what exists and goes one past it, and a floor only wins while it is higher.
Raise one by hand to start a new minor or major series.

**GitHub Packages authenticates downloads even for a public repository**, so a consumer needs a
token with `read:packages`:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/adminization/superizer")
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR")).get()
                password = providers.gradleProperty("gpr.key")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN")).get()
            }
        }
        google()
        mavenCentral()
    }
}
```

Put the two values in `~/.gradle/gradle.properties`, never in the repository:

```properties
gpr.user=your-github-login
gpr.key=ghp_yourPersonalAccessToken
```

Maven has no dist-tags, so there is no `latest` to follow: pin the number. To track a channel
instead, name it — `0.1.+` for the releases, or the exact `-next.N` you want. A version catalog is
the right place for it, so a bump is one line.

### Publishing it yourself

| Goal | How |
|---|---|
| local, for development | `./gradlew publishToMavenLocal` — needs no token |
| a prerelease | push the branch: `next` or `alpha` |
| a release | push `master`; CI patch bumps the last one for you |
| a new minor or major | raise `superizer.version` in `gradle.properties`, then push `master` |
| see what the next push will publish | `./scripts/resolve-version.sh release cx/m42/superizer/core 0.1.0` |

Publishing to GitHub Packages by hand needs the credentials Gradle looks up **by repository name**:

```bash
ORG_GRADLE_PROJECT_GitHubPackagesUsername=your-login \
ORG_GRADLE_PROJECT_GitHubPackagesPassword=ghp_… \
  ./gradlew publish -Psuperizer.version=0.1.1 -Psuperizer.appsVersion=1.0.1
```

(or `GitHubPackagesUsername` / `GitHubPackagesPassword` as Gradle properties). They are resolved at
execution time, so every other task — including `publishToMavenLocal` and the whole test suite —
runs with no token at all.

A fork publishes to its own registry by setting one property rather than editing the convention
plugin: `-Psuperizer.githubSlug=my-org/superizer`.

---

## 3. A host from scratch

A host is a Compose Multiplatform application module that registers apps and draws
`SuperizerShell`. Below is a complete, minimal one. Replace `myhost` with your own name.

```
my-host/
├── settings.gradle.kts
├── gradle/libs.versions.toml
├── build.gradle.kts
└── app/
    ├── build.gradle.kts
    └── src/
        ├── commonMain/kotlin/com/example/myhost/MyHost.kt
        ├── androidMain/kotlin/com/example/myhost/MainActivity.kt
        ├── androidMain/AndroidManifest.xml
        ├── desktopMain/kotlin/com/example/myhost/main.kt
        ├── wasmJsMain/kotlin/com/example/myhost/main.kt
        └── wasmJsMain/resources/index.html
```

### 3.1 `settings.gradle.kts`

```kotlin
rootProject.name = "my-host"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

// Option B, if the library's checkout is next door. Harmless when it is not.
val superizerDir = file("../superizer")
if (superizerDir.exists() && System.getenv("SUPERIZER_FROM_MAVEN") != "1") {
    includeBuild(superizerDir)
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":app")
include(":apps:hello")      // your first app, added in §4
```

### 3.2 `gradle/libs.versions.toml`

```toml
[versions]
kotlin = "2.4.10"
composeMultiplatform = "1.11.1"
agp = "8.13.2"
composeUnstyled = "1.36.1"
androidxActivityCompose = "1.13.0"
androidCompileSdk = "36"
androidMinSdk = "24"
androidTargetSdk = "36"
kotlinxCoroutines = "1.10.2"
kotlinxSerializationJson = "1.9.0"
superizer = "0.1.0"

[libraries]
compose-runtime = { module = "org.jetbrains.compose.runtime:runtime", version.ref = "composeMultiplatform" }
compose-foundation = { module = "org.jetbrains.compose.foundation:foundation", version.ref = "composeMultiplatform" }
compose-ui = { module = "org.jetbrains.compose.ui:ui", version.ref = "composeMultiplatform" }
compose-ui-test = { module = "org.jetbrains.compose.ui:ui-test", version.ref = "composeMultiplatform" }
compose-unstyled = { module = "com.composables:core", version.ref = "composeUnstyled" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "androidxActivityCompose" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinxCoroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinxSerializationJson" }

superizer-core = { module = "cx.m42.superizer:core", version.ref = "superizer" }
superizer-ui-theme = { module = "cx.m42.superizer:ui-theme", version.ref = "superizer" }
superizer-ui = { module = "cx.m42.superizer:ui", version.ref = "superizer" }
superizer-host = { module = "cx.m42.superizer:host", version.ref = "superizer" }
superizer-testing = { module = "cx.m42.superizer:testing", version.ref = "superizer" }
superizer-test-app = { module = "cx.m42.superizer.apps:test-app", version = "1.0.0" }

[plugins]
kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
androidApplication = { id = "com.android.application", version.ref = "agp" }
androidLibrary = { id = "com.android.library", version.ref = "agp" }
composeMultiplatform = { id = "org.jetbrains.compose", version.ref = "composeMultiplatform" }
composeCompiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlinxSerialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

Root `build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinxSerialization) apply false
}
```

### 3.3 `app/build.gradle.kts`

```kotlin
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
}

kotlin {
    androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }
    jvm("desktop")

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "app.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply { port = 8082 }
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)

            implementation(libs.superizer.core)
            implementation(libs.superizer.ui.theme)
            implementation(libs.superizer.ui)
            implementation(libs.superizer.host)      // the host module — only here, never in an app
            implementation(libs.superizer.test.app)  // optional: the bench app

            implementation(project(":apps:hello"))
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
        val desktopMain by getting
        desktopMain.dependencies { implementation(compose.desktop.currentOs) }

        val desktopTest by getting
        desktopTest.dependencies {
            implementation(libs.superizer.testing)
            implementation(libs.compose.ui.test)
            implementation(compose.desktop.currentOs)
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "com.example.myhost"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()
    defaultConfig {
        applicationId = "com.example.myhost"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { buildConfig = true }
}

compose.desktop {
    application {
        mainClass = "com.example.myhost.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Dmg, TargetFormat.Msi)
            packageName = "MyHost"
            packageVersion = "1.0.0"
        }
    }
}
```

### 3.4 The host itself — `commonMain/MyHost.kt`

```kotlin
package com.example.myhost

import androidx.compose.runtime.Composable
import com.example.apps.hello.HelloApp
import cx.m42.apps.testapp.TestApp
import cx.m42.superizer.LanguageOption
import cx.m42.superizer.Superizer
import cx.m42.superizer.SuperizerContract
import cx.m42.superizer.activation.Activation
import cx.m42.superizer.activation.ActivationResult
import cx.m42.superizer.app.AppId
import cx.m42.superizer.host.build
import cx.m42.superizer.host.platform.currentPlatform
import cx.m42.superizer.runtime.HostInfo
import cx.m42.superizer.ui.shell.SuperizerShell
import kotlinx.coroutines.CoroutineScope

fun buildMyHost(scope: CoroutineScope, debug: Boolean): Superizer = Superizer.build(scope) {
    host(
        HostInfo(
            name = "My Host",
            version = "1.0",
            build = "dev",
            platform = currentPlatform(),
            contractVersion = SuperizerContract.VERSION,
            debug = debug,
        ),
    )

    scheme("myhost")                       // myhost://app/<id>/<path>, myhost://activate?…

    languages(
        LanguageOption("en", "English"),
        LanguageOption("ru", "Русский"),
        fallback = "en",
    )

    register(HelloApp())
    register(TestApp())                    // hidden; reachable by its promo code

    home("hello")                          // what a fresh install shows on Home

    promoCodes(
        mapOf(
            TestApp.PROMO_CODE to ActivationResult.Success(
                Activation(AppId("test-app"), TestApp.promoConfig()),
            ),
        ),
    )

    serviceCode("SERVICE")                 // the one door into the Service Menu in a release
}

@Composable
fun MyHostApp(superizer: Superizer) = SuperizerShell(superizer)
```

Everything the user sees — Home, All Apps, the drawer, Settings, Activate, the Service Menu, the app
container, deep links, push routing, session restore, the error screen — comes from that. Adding a
fourth app is one more `register(…)` line and nothing else.

### 3.5 Android — `MainActivity.kt`

Two calls must happen **before the first composition**: `initPrefs` (storage) and `AndroidHost.init`
(vibrator, process lifecycle, connectivity).

```kotlin
package com.example.myhost

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import cx.m42.superizer.Superizer
import cx.m42.superizer.host.platform.AndroidHost
import cx.m42.superizer.host.push.PushTransport
import cx.m42.superizer.host.storage.initPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, 0x801B1B1B.toInt()),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, 0x801B1B1B.toInt()),
        )
        super.onCreate(savedInstanceState)

        initPrefs(this)
        AndroidHost.init(this)

        setContent {
            val superizer = remember { host() }
            remember { deliver(intent) }
            MyHostApp(superizer)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deliver(intent)
    }

    private fun deliver(intent: Intent?) {
        intent?.data?.toString()?.let { Holder.superizer?.route?.deliver(it) }
        intent?.extras?.let { extras ->
            val data = extras.keySet().mapNotNull { k -> extras.getString(k)?.let { k to it } }.toMap()
            if (data["schemaVersion"] != null) PushTransport.deliverTap(data)
        }
    }

    /** One host per **process**, not per activity: a rotation must not re-register every app. */
    private fun host(): Superizer = Holder.superizer ?: buildMyHost(
        CoroutineScope(SupervisorJob() + Dispatchers.Main),
        debug = BuildConfig.DEBUG,
    ).also { Holder.superizer = it }

    private object Holder { var superizer: Superizer? = null }
}
```

`AndroidManifest.xml` — `singleTask` so a second link reaches `onNewIntent` instead of stacking a
second host, and one filter for the scheme:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.VIBRATE" />

    <application android:label="My Host" android:supportsRtl="true">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTask"
            android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|density|uiMode|smallestScreenSize|fontScale">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            <intent-filter android:autoVerify="false">
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="myhost" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

### 3.6 Desktop — `desktopMain/main.kt`

```kotlin
fun main(args: Array<String>) = application {
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
    val superizer = remember { buildMyHost(scope, debug = true) }

    // The desktop stand-in for a QR code and a notification tap.
    remember {
        args.firstOrNull { it.startsWith("--link=") || it.startsWith("--activate=") }
            ?.substringAfter('=')
            ?.let { superizer.route.deliver(it) }
    }

    Window(
        onCloseRequest = ::exitApplication,
        state = rememberWindowState(width = 420.dp, height = 820.dp),
        title = "My Host",
    ) {
        MyHostApp(superizer)
    }
}
```

Optional: point the preferences file somewhere of your own with
`PrefsStorage.useDirectory(File(...))` before building the host.

### 3.7 Web — `wasmJsMain/main.kt` and `index.html`

```kotlin
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(viewportContainerId = "composeTarget") {
        val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
        val superizer = remember { buildMyHost(scope, debug = true) }
        remember {
            val search = window.location.search
            queryParam(search, "link")?.let { superizer.route.deliver(it) }
            queryParam(search, "activate")?.let { superizer.route.deliver(it) }
        }
        MyHostApp(superizer)
    }
}

internal fun queryParam(search: String, name: String): String? = search
    .removePrefix("?").split('&')
    .firstOrNull { it.startsWith("$name=") }?.substringAfter('=')
    ?.let { runCatching { decodeUriComponent(it) }.getOrDefault(it) }

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun decodeUriComponent(value: String): String = js("decodeURIComponent(value)")
```

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
    <title>My Host</title>
    <style>
        html, body { margin: 0; padding: 0; height: 100%; overflow: hidden; }
        #composeTarget { width: 100%; height: 100%; }
    </style>
</head>
<body>
<div id="composeTarget"></div>
<script src="app.js"></script>
</body>
</html>
```

Browser storage is `localStorage`; a browser with site data blocked degrades to "nothing was ever
saved" rather than crashing.

---

## 4. Your first app module

```kotlin
// apps/hello/build.gradle.kts
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
}

group = "com.example.apps"
version = "1.0.0"

kotlin {
    explicitApi()
    androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }
    jvm("desktop")
    @OptIn(ExperimentalWasmDsl::class) wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            api(libs.superizer.core)            // the contract
            implementation(libs.superizer.ui.theme)
            implementation(libs.superizer.ui)   // widgets — optional but recommended
            implementation(libs.compose.unstyled)
            implementation(libs.kotlinx.serialization.json)
            // NEVER: superizer:host, the host module, or another app.
        }
        val desktopTest by getting
        desktopTest.dependencies {
            implementation(libs.superizer.testing)
            implementation(libs.compose.ui.test)
            implementation(compose.desktop.currentOs)
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "com.example.apps.hello"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.androidMinSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
```

The app itself is [one page away](03-app-guide.md#2-the-smallest-app-that-works).

### Enforce the boundary in your build

Copy the `checkDependencyRules` task from Unitool's root `build.gradle.kts`: it reads the app
modules' build scripts as text and fails on `superizer:host`, on the host module, and on one app
depending on another. A rule a reviewer has to remember is a rule that erodes.

---

## 5. Running

```bash
./gradlew :app:run                        # desktop
./gradlew :app:installDebug               # Android, on a connected device
./gradlew :app:wasmJsBrowserDevelopmentRun  # web, http://localhost:8082
./gradlew :app:wasmJsBrowserDistribution  # a static bundle in build/dist/wasmJs/productionExecutable
```

Driving the routing paths by hand:

```bash
# desktop
./gradlew :app:run --args="--link=myhost://app/hello/greet?who=world"

# Android
adb shell am start -a android.intent.action.VIEW -d "myhost://app/hello/greet?who=world"

# web
open "http://localhost:8082/?link=myhost%3A%2F%2Fapp%2Fhello%2Fgreet%3Fwho%3Dworld"
```

## 6. Verifying

In the library's own checkout:

```bash
./scripts/verify.sh     # static checks, desktop tests, wasm tests, web smoke, publish, one report
```

The report lands in `build/verify/report.md` and exits non-zero unless every line passes; skips are
always named. Individually:

```bash
./gradlew checkDependencyRules   # the module layering
./gradlew checkLegacyAbi         # the public API still matches api/*.api
./gradlew updateLegacyAbi        # regenerate those dumps after an intended API change
./gradlew desktopTest            # every unit and UI test on the JVM
CHROME_BIN=$(which chromium) ./gradlew wasmJsTest
./gradlew publishToMavenLocal && ./gradlew -p samples/consumer build
```

The last line is the one that proves a *publication* rather than a compilation: `samples/consumer`
is a separate build that resolves the library by coordinates, exactly as a stranger would.
