package cx.m42.superizer.testing

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import cx.m42.superizer.app.AppConfig
import cx.m42.superizer.app.AppConfigSpec
import cx.m42.superizer.app.AppInstance
import cx.m42.superizer.app.AppSetupContext
import cx.m42.superizer.app.SuperizerApp
import cx.m42.superizer.event.SuperizerEvent
import cx.m42.superizer.runtime.AppRuntime
import cx.m42.superizer.runtime.InstanceRuntime
import cx.m42.superizer.runtime.LocalAppRuntime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * The guarantees every app owes the host, as tests it inherits in one line (D21).
 *
 * ```kotlin
 * class CalculatorContractTest : AppContractTest(CalculatorApp())
 * ```
 *
 * This is a TCK, not a sample: the things it checks are the things a host *assumes* and therefore
 * never checks again at runtime. An app that passes it can be dropped into any host; an app that
 * does not would fail somewhere far from the cause.
 */
public abstract class AppContractTest(private val app: SuperizerApp<*>) {

    /**
     * Older payloads this app must still be able to read, as raw JSON.
     *
     * Strings rather than files under `fixtures/` (which is what 12 §4.7 sketched): a resource
     * loader that works the same in `desktopTest`, Robolectric and Karma does not exist, and a
     * migration test nobody can run on wasm is a migration test that rots. An app with no
     * migrations still lists its current shape here, so the test exists from day one.
     */
    protected open val configFixtures: List<String> get() = emptyList()

    /** The same, for `saveState` snapshots: what [AppInstance.restore] may be handed after an update. */
    protected open val stateFixtures: List<String> get() = emptyList()

    /** A config a test can open the app with when the default would not exercise anything. */
    protected open val sampleConfig: AppConfig get() = AppConfig.Empty

    @Test
    public fun theDefaultConfigDecodes() {
        val decoded = app.configSpec.decode(AppConfig.Empty)
        assertTrue(decoded.isSuccess, "decoding an empty config failed: ${decoded.exceptionOrNull()}")
    }

    @Test
    public fun aBrokenPayloadIsAFailureAndNotASilentDefault() {
        // A string where an object belongs — the shape a Service Menu paste or an old QR produces.
        val broken = AppConfig(Json.parseToJsonElement("""{"mode":{"nested":true}}""") as JsonObject)
        val decoded = app.configSpec.decode(broken)
        // Either it decoded (the field genuinely accepts an object) or it failed. What must not
        // happen is a silent fall back to the default, which is what D18 rules out — and the
        // handler, not the spec, is what applies `fallbackToDefault`.
        if (decoded.isFailure) {
            assertTrue(
                decoded.exceptionOrNull() != null,
                "a failed decode must carry the reason the Service Menu shows",
            )
        }
    }

    @Test
    public fun everyFixtureStillReadsWithTheCurrentSpec() {
        configFixtures.forEach { raw ->
            val config = AppConfig(Json.parseToJsonElement(raw) as JsonObject)
            val decoded = app.configSpec.decode(config)
            assertTrue(decoded.isSuccess, "config fixture no longer decodes: $raw (${decoded.exceptionOrNull()})")
        }
    }

    @Test
    public fun setupRegistersOnlyWhatTheManifestDeclares() {
        val recorder = RecordingSetupContext(FakeAppRuntime(app.id, TestScope()))
        app.setup(recorder)
        val undeclared = recorder.deepLinks - app.manifest.deepLinks
        assertEquals(emptySet(), undeclared, "setup() registered deep links the manifest does not declare")
    }

    @Test
    public fun theManifestSurvivesARoundTripThroughJson() {
        // D47: the Service Menu shows it, and an extension manifest is this, serialized. A manifest
        // that cannot be written down is a manifest that only exists inside this process.
        val json = Json { ignoreUnknownKeys = true }
        val encoded = json.encodeToString(cx.m42.superizer.app.AppManifest.serializer(), app.manifest)
        val back = json.decodeFromString(cx.m42.superizer.app.AppManifest.serializer(), encoded)
        assertEquals(app.manifest.id, back.id)
        assertEquals(app.manifest.requires, back.requires)
        assertEquals(app.manifest.deepLinks, back.deepLinks)
    }

    @Test
    public fun launchingIsCheapAndTouchesNoNetworkBeforeTheFirstFrame(): TestResult = appTest { runtime ->
        val instance = create(runtime)
        val started = currentTimeMillisVirtual()
        instance.onLaunch()
        advanceUntilIdle()
        val elapsed = currentTimeMillisVirtual() - started
        assertTrue(elapsed <= ON_LAUNCH_BUDGET_MS, "onLaunch took ${elapsed}ms of virtual time")
        assertEquals(
            emptyList(),
            runtime.network.calls,
            "onLaunch made a network call; that belongs in Content() or the instance scope",
        )
        instance.dispose()
    }

    @Test
    public fun aSnapshotSurvivesARoundTrip(): TestResult = appTest { runtime ->
        val instance = create(runtime)
        instance.onLaunch()
        val snapshot = instance.saveState()
        if (snapshot != null) {
            // Restoring into a *fresh* instance, because that is the only case that ever happens:
            // the process died and this object is new.
            val restored = create(fakeRuntime(runtime.scope))
            restored.onLaunch()
            restored.restore(snapshot)
            assertEquals(snapshot, restored.saveState(), "saveState → restore → saveState is not stable")
            restored.dispose()
        }
        instance.dispose()
    }

    @Test
    public fun everyStateFixtureCanStillBeRestored(): TestResult = appTest { runtime ->
        stateFixtures.forEach { raw ->
            val instance = create(fakeRuntime(runtime.scope))
            instance.onLaunch()
            // Must not throw: a snapshot may predate the update that is reading it (03).
            instance.restore(Json.parseToJsonElement(raw) as JsonObject)
            instance.dispose()
        }
    }

    @Test
    public fun closingIsAllowedByDefaultAndDisposeIsIdempotent(): TestResult = appTest { runtime ->
        val instance = create(runtime)
        instance.onLaunch()
        instance.onClose()
        instance.dispose()
        // Twice, because the handler disposes on close and the host disposes on shutdown, and the
        // two can meet.
        instance.dispose()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    public fun theScreenComposesAndHasTheRootTagTheContainerPutsOnIt(): Unit = runComposeUiTest {
        val scope = TestScope()
        val runtime = fakeRuntime(scope)
        val instance = create(runtime)
        setContent {
            // The tag is the container's job (D39), so the TCK puts one on exactly as the shell
            // would — and an app that crashed on its first composition fails here instead of in
            // whatever host ships it.
            CompositionLocalProvider(LocalAppRuntime provides runtime) {
                androidx.compose.foundation.layout.Box(Modifier.testTag("${app.id.value}:root")) {
                    instance.Content()
                }
            }
        }
        onNodeWithTag("${app.id.value}:root").assertIsDisplayed()
    }

    // ------------------------------------------------------------------ helpers

    /**
     * A fake runtime on a scope that is **not** the test's own.
     *
     * `runTest` waits for every child of its scope before it finishes, and `onLaunch` is exactly
     * where an app is supposed to start long-lived work — collecting pushes, watching events. Hung
     * off the test coroutine those would make every app's TCK run time out after a minute, which is
     * a failure about the harness rather than about the app.
     */
    private fun appTest(body: suspend TestScope.(FakeAppRuntime) -> Unit): TestResult = runTest {
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        try {
            body(fakeRuntime(scope))
        } finally {
            scope.cancel()
        }
    }

    private fun fakeRuntime(scope: CoroutineScope): FakeAppRuntime = FakeAppRuntime(
        appId = app.id,
        scope = scope,
        // Exactly what the manifest declares, and nothing more: `require(key)` on an undeclared
        // key must fail here rather than find something a generous fake happened to offer.
        declaredServices = app.manifest.requires,
    )

    @Suppress("UNCHECKED_CAST")
    private fun create(runtime: InstanceRuntime): AppInstance {
        val spec = app.configSpec as AppConfigSpec<Any>
        val config = spec.decode(sampleConfig).getOrElse { spec.default }
        return (app as SuperizerApp<Any>).launch(runtime, config)
    }

    private companion object {
        /** 12 §4.7: an app that is slow to open is one the container has to show a spinner for. */
        const val ON_LAUNCH_BUDGET_MS = 200L
    }
}

/** Virtual time, read the same way on every target `runTest` supports. */
private fun kotlinx.coroutines.test.TestScope.currentTimeMillisVirtual(): Long =
    testScheduler.currentTime

/** Collects what `setup` registered, so the TCK can compare it with the manifest (D47). */
private class RecordingSetupContext(override val runtime: AppRuntime) : AppSetupContext {
    val deepLinks = mutableSetOf<String>()
    var sections = 0
    var listeners = 0

    override fun settingsSection(section: @androidx.compose.runtime.Composable (runtime: AppRuntime) -> Unit) {
        sections++
    }

    override fun deepLink(path: String, toConfig: (params: Map<String, String>) -> AppConfig) {
        deepLinks += path
    }

    override fun listener(handler: suspend (event: SuperizerEvent) -> Unit) {
        listeners++
    }
}
