package cx.m42.superizer

import cx.m42.superizer.app.AppConfig
import cx.m42.superizer.app.AppId
import cx.m42.superizer.event.SuperizerEvent
import cx.m42.superizer.registry.AppHandler
import cx.m42.superizer.registry.AppRegistry
import cx.m42.superizer.registry.AppState
import cx.m42.superizer.registry.SessionSnapshot
import cx.m42.superizer.runtime.Clock
import cx.m42.superizer.runtime.HostLifecycle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * The handler's specification, as the order of events it publishes.
 *
 * Asserting the *sequence* rather than the outcome is the point: §9 is a sequence, the bench app
 * and the Service Menu render that sequence, and a refactor that still ends up Active but skipped
 * `Configured` has broken something a host depends on.
 */
class AppHandlerTest {

    /**
     * The handler's scope is deliberately *not* the test's own.
     *
     * `runTest` waits for every child of its scope before it finishes, and a handler has several
     * infinite collectors — the lifecycle observer, one per `ctx.listener`. Hanging them off the
     * test coroutine would make every test here time out after sixty seconds, which is how this
     * comment came to be written.
     */
    private class Fixture(val scope: CoroutineScope, private val clock: MutableClock = MutableClock()) {
        val events = MutableSharedFlow<SuperizerEvent>(replay = 64, extraBufferCapacity = 64)
        val lifecycle = MutableStateFlow(HostLifecycle.Foreground)
        val registry = AppRegistry(testHost(), events)
        val snapshots = MemorySnapshots()
        val handler = AppHandler(
            registry = registry,
            runtimeFactory = TestRuntimeFactory(events, lifecycle),
            events = events,
            hostLifecycle = lifecycle,
            snapshots = snapshots,
            clock = clock,
            scope = scope,
        )

        fun advance(hours: Long) {
            clock.millis += hours * 3_600_000
        }

        fun names(): List<String> = events.replayCache.map { it::class.simpleName!! }
    }

    private class MutableClock(var millis: Long = 1_700_000_000_000) : Clock {
        override fun now(): Long = millis
    }

    /** One fixture, on the shared virtual clock, torn down whatever the test did. */
    private fun handlerTest(body: suspend TestScope.(Fixture) -> Unit): TestResult = runTest {
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val fixture = Fixture(scope)
        try {
            body(fixture)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun launchingPublishesEveryStepOfTheSequenceInOrder(): TestResult = handlerTest { f ->
        val app = ProbeApp()
        f.registry.register(app)
        f.handler.enableAll()

        val session = f.handler.launch(AppId("probe")).getOrThrow()
        advanceUntilIdle()

        assertEquals(AppState.Active, session.state.value)
        val steps = f.names()
        assertEquals(
            listOf("Registered", "Enabled", "RuntimeCreated", "Configured", "Created", "Launched", "Active"),
            steps,
        )
    }

    @Test
    fun theTypedConfigReachesTheApp(): TestResult = handlerTest { f ->
        val app = ProbeApp()
        f.registry.register(app)
        f.handler.enableAll()

        f.handler.launch(AppId("probe"), config("""{"mode":"scientific"}"""))
        advanceUntilIdle()

        assertEquals("scientific", (app.lastInstance as ProbeInstance).config.mode)
    }

    @Test
    fun aBrokenConfigIsReportedAndThenTheDefaultIsUsed(): TestResult = handlerTest { f ->
        // D18: reported *and* recovered from. Silently recovering is the bug; refusing to open a
        // calculator over it is the overreaction.
        val app = ProbeApp()
        f.registry.register(app)
        f.handler.enableAll()

        val result = f.handler.launch(AppId("probe"), config("""{"mode":{"deep":1}}"""))
        advanceUntilIdle()

        assertTrue(result.isSuccess)
        assertTrue("ConfigRejected" in f.names())
        assertEquals("default", (app.lastInstance as ProbeInstance).config.mode)
    }

    @Test
    fun anAppThatRefusesDefaultsDoesNotOpenOnABrokenConfig(): TestResult = handlerTest { f ->
        f.registry.register(ProbeApp(fallbackToDefault = false))
        f.handler.enableAll()

        val result = f.handler.launch(AppId("probe"), config("""{"mode":{"deep":1}}"""))
        advanceUntilIdle()

        assertTrue(result.isFailure)
        assertEquals(AppState.Failed, f.handler.states.value[AppId("probe")])
        assertNull(f.handler.current.value)
    }

    @Test
    fun anAppThatThrowsOnLaunchFailsTheSessionAndNotTheHost(): TestResult = handlerTest { f ->
        f.registry.register(ProbeApp(failOnLaunch = true))
        f.handler.enableAll()

        val result = f.handler.launch(AppId("probe"))
        advanceUntilIdle()

        assertTrue(result.isFailure)
        assertEquals(AppState.Failed, f.handler.states.value[AppId("probe")])
        assertTrue("LaunchFailed" in f.names())
    }

    @Test
    fun anExceptionInTheInstanceScopeCrashesTheSessionAndTheNextLaunchStillWorks(): TestResult = handlerTest { f ->
        // D31. Without the handler's CoroutineExceptionHandler this throw takes the host with it.
        val app = ProbeApp()
        f.registry.register(app)
        f.handler.enableAll()
        f.handler.launch(AppId("probe"))
        advanceUntilIdle()

        (app.lastInstance as ProbeInstance).crash()
        advanceUntilIdle()

        assertTrue("Crashed" in f.names())
        assertEquals(AppState.Failed, f.handler.states.value[AppId("probe")])

        assertTrue(f.handler.launch(AppId("probe")).isSuccess)
        advanceUntilIdle()
        assertEquals(AppState.Active, f.handler.states.value[AppId("probe")])
    }

    @Test
    fun anAppMayRefuseToCloseAndTheHandlerSaysSo(): TestResult = handlerTest { f ->
        f.registry.register(ProbeApp(vetoClose = true))
        f.handler.enableAll()
        f.handler.launch(AppId("probe"))
        advanceUntilIdle()

        assertFalse(f.handler.close())
        assertNotNull(f.handler.current.value)

        // Forced is what the host does after the user has answered its dialog.
        assertTrue(f.handler.close(force = true))
        assertNull(f.handler.current.value)
    }

    @Test
    fun goingToBackgroundWritesASnapshotAndComingBackTellsTheApp(): TestResult = handlerTest { f ->
        val app = ProbeApp()
        f.registry.register(app)
        f.handler.enableAll()
        f.handler.observeLifecycle()
        f.handler.launch(AppId("probe"))
        advanceUntilIdle()
        (app.lastInstance as ProbeInstance).entry = "12+"

        f.lifecycle.value = HostLifecycle.Background
        advanceUntilIdle()

        val snapshot = f.snapshots.snapshot
        assertNotNull(snapshot)
        assertEquals(AppId("probe"), snapshot.appId)
        assertEquals("12+", (snapshot.state?.get("entry") as JsonPrimitive).content)
        assertTrue("onBackground" in (app.lastInstance as ProbeInstance).calls)

        f.lifecycle.value = HostLifecycle.Foreground
        advanceUntilIdle()
        assertTrue("onForeground" in (app.lastInstance as ProbeInstance).calls)
    }

    @Test
    fun aNewHandlerOnTheSameStoreReopensWhatWasOpen(): TestResult = runTest {
        // The process died. A different handler, a different instance, the same storage — and the
        // user's half-typed sum is still there (D16).
        val snapshots = MemorySnapshots()
        snapshots.save(
            SessionSnapshot(
                AppId("probe"),
                AppConfig.Empty,
                buildJsonObject { put("entry", JsonPrimitive("99")) },
                1_700_000_000_000,
            ),
        )
        val events = MutableSharedFlow<SuperizerEvent>(replay = 64, extraBufferCapacity = 64)
        val lifecycle = MutableStateFlow(HostLifecycle.Foreground)
        val registry = AppRegistry(testHost(), events)
        val app = ProbeApp()
        registry.register(app)
        val hostScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val handler = AppHandler(
            registry, TestRuntimeFactory(events, lifecycle), events, lifecycle, snapshots,
            Clock { 1_700_000_000_000 }, hostScope,
        )
        handler.enableAll()

        val restored = handler.restoreLastSession()
        advanceUntilIdle()

        assertNotNull(restored)
        assertTrue(restored.isSuccess)
        assertEquals("99", (app.lastInstance as ProbeInstance).entry)
        hostScope.cancel()
    }

    @Test
    fun aSnapshotOlderThanADayIsNotWhatTheUserCameBackFor(): TestResult = handlerTest { f ->
        f.registry.register(ProbeApp())
        f.handler.enableAll()
        f.snapshots.save(SessionSnapshot(AppId("probe"), AppConfig.Empty, null, 1_700_000_000_000))
        f.advance(hours = 25)

        assertNull(f.handler.restoreLastSession())
        assertNull(f.snapshots.snapshot)
    }

    @Test
    fun aSnapshotOfAnAppLockedAgainSinceIsDroppedNotRestored(): TestResult = runTest {
        // The snapshot is yesterday's screen, not an activation: an app put back behind its
        // activation in between (the Service Menu, a reset) does not walk back in through it.
        val unlocked = mutableSetOf<AppId>()
        val events = MutableSharedFlow<SuperizerEvent>(replay = 64, extraBufferCapacity = 64)
        val lifecycle = MutableStateFlow(HostLifecycle.Foreground)
        val registry = AppRegistry(testHost(), events)
        registry.register(ProbeApp(hidden = true))
        val snapshots = MemorySnapshots()
        snapshots.save(SessionSnapshot(AppId("probe"), AppConfig.Empty, null, 0))
        val hostScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val handler = AppHandler(
            registry, TestRuntimeFactory(events, lifecycle), events, lifecycle, snapshots,
            Clock { 0 }, hostScope, unlocked = { unlocked },
        )
        handler.enableAll()

        val restored = handler.restoreLastSession()
        advanceUntilIdle()

        assertNotNull(restored)
        assertTrue(restored.isFailure)
        assertNull(snapshots.snapshot)
        assertNull(handler.current.value)
        hostScope.cancel()
    }

    @Test
    fun setupThatRegistersAnUndeclaredDeepLinkLeavesTheAppDisabled(): TestResult = handlerTest { f ->
        // D47: the manifest is the declaration, `setup` is the wiring, and a manifest that can be
        // wrong about what an app serves is a manifest the Service Menu cannot be trusted to show.
        f.registry.register(ProbeApp(deepLinks = setOf("rate"), registersUndeclaredLink = "secret"))
        f.handler.enableAll()
        advanceUntilIdle()

        assertTrue("SetupMismatch" in f.names())
        assertFalse(f.handler.isEnabled(AppId("probe")))
    }

    @Test
    fun aDeclaredDeepLinkIsAvailableToTheActivationService(): TestResult = handlerTest { f ->
        f.registry.register(ProbeApp(deepLinks = setOf("rate")))
        f.handler.enableAll()

        val toConfig = f.handler.deepLinkHandler(AppId("probe"), "rate")
        assertNotNull(toConfig)
        assertEquals("usd", Json.parseToJsonElement(toConfig(mapOf("mode" to "usd")).json.toString()).let {
            ((it as JsonObject)["mode"] as JsonPrimitive).content
        })
    }

    @Test
    fun aHiddenAppDoesNotOpenUntilSomethingUnlocksIt(): TestResult = runTest {
        val unlocked = mutableSetOf<AppId>()
        val events = MutableSharedFlow<SuperizerEvent>(replay = 64, extraBufferCapacity = 64)
        val lifecycle = MutableStateFlow(HostLifecycle.Foreground)
        val registry = AppRegistry(testHost(), events)
        registry.register(ProbeApp(hidden = true))
        val hostScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val handler = AppHandler(
            registry, TestRuntimeFactory(events, lifecycle), events, lifecycle, MemorySnapshots(),
            Clock { 0 }, hostScope, unlocked = { unlocked },
        )
        handler.enableAll()

        assertTrue(handler.launch(AppId("probe")).isFailure)

        // `force` is what an activation has and a link does not (D25).
        assertTrue(handler.launch(AppId("probe"), force = true).isSuccess)
        advanceUntilIdle()
        hostScope.cancel()
    }

    @Test
    fun openingASecondAppClosesTheFirstAndKeepsItsSnapshot(): TestResult = handlerTest { f ->
        val first = ProbeApp(id = "first")
        f.registry.register(first)
        f.registry.register(ProbeApp(id = "second"))
        f.handler.enableAll()

        f.handler.launch(AppId("first"))
        advanceUntilIdle()
        (first.lastInstance as ProbeInstance).entry = "kept"

        f.handler.launch(AppId("second"))
        advanceUntilIdle()

        assertEquals(AppId("second"), f.handler.current.value?.app?.id)
        assertEquals("kept", (f.snapshots.snapshot?.state?.get("entry") as JsonPrimitive).content)
    }

    @Test
    fun aSecondLaunchWhileTheFirstIsStillLaunchingIsQueuedAndNotDropped(): TestResult = handlerTest { f ->
        f.registry.register(ProbeApp(id = "first"))
        f.registry.register(ProbeApp(id = "second"))
        f.handler.enableAll()

        launch { f.handler.launch(AppId("first")) }
        launch { f.handler.launch(AppId("second")) }
        advanceUntilIdle()

        // Whichever ran second is the one that is open; what must not happen is two sessions, or
        // one session whose instance belongs to the other app.
        val open = f.handler.current.value
        assertNotNull(open)
        assertEquals(AppState.Active, open.state.value)
    }

    @Test
    fun disablingAnAppClosesItAndRunsItsDisposers(): TestResult = handlerTest { f ->
        val app = ProbeApp(deepLinks = setOf("rate"))
        f.registry.register(app)
        f.handler.enableAll()
        f.handler.launch(AppId("probe"))
        advanceUntilIdle()

        assertTrue(f.handler.disable(AppId("probe")))
        assertNull(f.handler.current.value)
        assertNull(f.handler.deepLinkHandler(AppId("probe"), "rate"))
        assertFalse(f.handler.isEnabled(AppId("probe")))
    }

    private fun config(json: String) = AppConfig(Json.parseToJsonElement(json) as JsonObject)
}
