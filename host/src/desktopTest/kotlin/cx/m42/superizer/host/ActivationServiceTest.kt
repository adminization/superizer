package cx.m42.superizer.host

import cx.m42.superizer.activation.ActivationResult
import cx.m42.superizer.app.AppId
import cx.m42.superizer.event.SuperizerEvent
import cx.m42.superizer.host.activation.ActivationService
import cx.m42.superizer.host.activation.LocalPromoCodes
import cx.m42.superizer.host.storage.UnlockStore
import cx.m42.superizer.registry.AppHandler
import cx.m42.superizer.registry.AppRegistry
import cx.m42.superizer.runtime.Clock
import cx.m42.superizer.runtime.HostLifecycle
import cx.m42.superizer.testing.FakeHostRuntimeFactory
import cx.m42.superizer.testing.InMemorySnapshotStore
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * The four doors of 06, and the rules about which of them may open a locked one.
 *
 * The distinction these exist to pin down: a QR payload and a promo code *carry a secret*, so they
 * unlock; a link that merely names an app does not (D25). Get that wrong and "hidden" means
 * "hidden from people who have not guessed the id".
 */
class ActivationServiceTest {

    @BeforeTest
    fun setUp() = isolatePrefs()

    private class Fixture(scope: CoroutineScope) {
        val events = MutableSharedFlow<SuperizerEvent>(replay = 64, extraBufferCapacity = 64)
        val registry = AppRegistry(testHostInfo(), events).apply {
            register(StubApp("calculator", deepLinks = setOf("rate")))
            register(StubApp("secret", hidden = true))
        }
        val unlocks = UnlockStore(events)
        val handler = AppHandler(
            registry = registry,
            runtimeFactory = FakeHostRuntimeFactory(),
            events = events,
            hostLifecycle = MutableStateFlow(HostLifecycle.Foreground),
            snapshots = InMemorySnapshotStore(),
            clock = Clock { 0 },
            scope = scope,
            unlocked = { unlocks.unlocked.value },
        )
        val service = ActivationService(
            registry = registry,
            promo = LocalPromoCodes(
                mapOf(
                    "SCI" to ActivationResult.Success(
                        cx.m42.superizer.activation.Activation(AppId("calculator")),
                    ),
                    "TEST-2026" to ActivationResult.Success(
                        cx.m42.superizer.activation.Activation(AppId("secret")),
                    ),
                ),
            ),
            unlocks = unlocks,
            handler = handler,
            events = events,
            scheme = "unitool",
            serviceCode = "SERVICE",
        )

        fun names(): List<String> = events.replayCache.map { it::class.simpleName!! }
    }

    private fun activationTest(body: suspend (Fixture) -> Unit) = runTest {
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        try {
            val fixture = Fixture(scope)
            fixture.handler.enableAll()
            body(fixture)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun aPromoCodeIsNormalisedSoItCanBeReadOffPaper() = activationTest { f ->
        // These are typed by hand from a card. A code that only works if you guess the punctuation
        // is a code that does not work.
        listOf("TEST-2026", "test 2026", "test2026", "Test-2026").forEach { spelling ->
            assertTrue(f.service.fromPromo(spelling) is ActivationResult.Success, "failed on '$spelling'")
        }
    }

    @Test
    fun anUnknownCodeIsRejectedByName() = activationTest { f ->
        val result = f.service.fromPromo("NOPE")
        assertEquals(ActivationResult.Reason.UnknownCode, (result as ActivationResult.Rejected).reason)
    }

    @Test
    fun theServiceCodeIsAHostCommandAndNotAnApp() = activationTest { f ->
        // D20: no reserved app id, so nothing to collide with a real one later.
        val result = f.service.fromPromo("service")
        assertEquals(ActivationResult.Command.OpenServiceMenu, (result as ActivationResult.HostCommand).command)
    }

    @Test
    fun applyingAnActivationUnlocksTheAppAndSaysWhereItCameFrom() = activationTest { f ->
        val result = f.service.fromPromo("TEST-2026") as ActivationResult.Success
        assertTrue(f.service.apply(result.activation, source = "promo").isSuccess)

        assertTrue(AppId("secret") in f.unlocks.unlocked.value)
        assertTrue("Unlocked" in f.names())
        assertTrue("Activated" in f.names())
    }

    @Test
    fun aLinkThatMerelyNamesAHiddenAppIsRefused() = activationTest { f ->
        // D25. The app id is not a secret; a payload is.
        val result = f.service.fromDeepLink("unitool://app/secret")
        assertEquals(ActivationResult.Reason.UnknownApp, (result as ActivationResult.Rejected).reason)
    }

    @Test
    fun aDeclaredPathTurnsItsQueryIntoAConfig() = activationTest { f ->
        val result = f.service.fromDeepLink("unitool://app/calculator/rate?mode=scientific")
        assertTrue(result is ActivationResult.Success)
        assertEquals("scientific", result.activation.config.json["mode"]?.toString()?.trim('"'))
        // A link names an app; it does not reveal one.
        assertTrue(!result.activation.unlock)
    }

    @Test
    fun anUndeclaredPathOpensTheAppAnywayAndSaysSo() = activationTest { f ->
        // Soft degradation, the same choice agentiz made for an unrecognised push `type`: a
        // payload from a newer server should leave the user somewhere sensible.
        val result = f.service.fromDeepLink("unitool://app/calculator/chart?x=1")
        assertTrue(result is ActivationResult.Success)
        assertTrue(result.activation.config.json.isEmpty())
        assertTrue("DeepLinkUnmatched" in f.names())
    }

    @Test
    fun aLinkForAnAppThisBuildDoesNotHaveIsRejected() = activationTest { f ->
        val result = f.service.fromDeepLink("unitool://app/weather")
        assertEquals(ActivationResult.Reason.UnknownApp, (result as ActivationResult.Rejected).reason)
    }

    @Test
    fun rubbishLinksAreRejectedRatherThanThrowing() = activationTest { f ->
        listOf(
            "",
            "not a url",
            "https://example.com/app/calculator",
            "unitool://",
            "unitool://app/",
            "unitool://app/CALCULATOR",
        ).forEach { link ->
            assertTrue(f.service.fromDeepLink(link) is ActivationResult.Rejected, "accepted '$link'")
        }
    }

    @Test
    fun anActivationLinkIsTheSameThingAQrCodeCarries() = activationTest { f ->
        val result = f.service.fromDeepLink("unitool://activate?a=calculator&c=eyJtb2RlIjoic2NpIn0")
        assertTrue(result is ActivationResult.Success)
        assertEquals(AppId("calculator"), result.activation.appId)
    }
}
