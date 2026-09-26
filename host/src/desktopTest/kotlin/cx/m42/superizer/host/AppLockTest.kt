package cx.m42.superizer.host

import cx.m42.superizer.app.AppId
import cx.m42.superizer.app.LockPolicy
import cx.m42.superizer.app.SuperizerApp
import cx.m42.superizer.event.SuperizerEvent
import cx.m42.superizer.host.lock.AppLockController
import cx.m42.superizer.host.lock.LockStore
import cx.m42.superizer.host.push.PendingRoute
import cx.m42.superizer.lock.AuthAvailability
import cx.m42.superizer.lock.AuthOutcome
import cx.m42.superizer.runtime.HostLifecycle
import cx.m42.superizer.testing.FakeClock
import cx.m42.superizer.testing.FakeDeviceAuthenticator
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * When the lock closes, line by line (06 §5.3, §11). Everything the controller listens to is a
 * value here — the clock, the lifecycle, the screen, the device's sheet — so each rule is one
 * assignment and one assertion rather than a sleep.
 */
class AppLockTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @BeforeTest
    fun setUp() = isolatePrefs()

    @AfterTest
    fun tearDown() = scope.cancel()

    private val secret = AppId("secret")
    private val plain = AppId("plain")

    private inner class Fixture(
        apps: List<SuperizerApp<*>> = listOf(StubApp("secret", lock = LockPolicy.Required), StubApp("plain")),
        availability: AuthAvailability = AuthAvailability.Available,
    ) {
        val clock = FakeClock()
        val lifecycle = MutableStateFlow(HostLifecycle.Foreground)
        val screenOff = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        var screenOn = true
        val device = FakeDeviceAuthenticator(availability)
        val apps = MutableStateFlow(apps)
        val lock = AppLockController(
            apps = this.apps,
            authenticator = device,
            store = LockStore(),
            clock = clock,
            lifecycle = lifecycle,
            screenOff = screenOff,
            screenOn = { screenOn },
        ).also { it.attach(scope) }

        suspend fun unlocked(): Fixture = apply { assertEquals(AuthOutcome.Success, lock.unlock("Unlock")) }

        fun away(seconds: Long) {
            lifecycle.value = HostLifecycle.Background
            clock.advance(seconds = seconds)
            lifecycle.value = HostLifecycle.Foreground
        }
    }

    @Test
    fun aProtectedAppStartsLockedAndAnOrdinaryOneIsNeverCovered() = runTest {
        val f = Fixture()
        assertTrue(f.lock.state.value.covers(secret))
        assertFalse(f.lock.state.value.covers(plain))

        f.unlocked()
        assertFalse(f.lock.state.value.covers(secret))
        assertEquals(listOf("Unlock"), f.device.prompts)
    }

    @Test
    fun aShortTripToAnotherAppKeepsItOpenAndALongOneLocksIt() = runTest {
        val f = Fixture().unlocked()

        f.away(seconds = 59)
        assertTrue(f.lock.state.value.open)

        f.away(seconds = 60)
        assertFalse(f.lock.state.value.open)
    }

    @Test
    fun theGraceIsThePersonsAndIsRemembered() = runTest {
        val f = Fixture().unlocked()
        f.lock.setGrace(300_000)
        f.away(seconds = 120)
        assertTrue(f.lock.state.value.open)

        // The next process reads it back; the unlock itself it does not (D130).
        val next = Fixture()
        assertEquals(300_000, next.lock.state.value.graceMillis)
        assertFalse(next.lock.state.value.open)
    }

    @Test
    fun graceZeroLocksOnEveryDepartureAndOnlyOnDeparture() = runTest {
        val f = Fixture().unlocked()
        f.lock.setGrace(0)

        f.lifecycle.value = HostLifecycle.Background
        assertFalse(f.lock.state.value.open)
    }

    @Test
    fun theScreenGoingOffLocksAtOnceWhereverTheAppIs() = runTest {
        // In front: the broadcast.
        val f = Fixture().unlocked()
        f.screenOff.emit(Unit)
        assertFalse(f.lock.state.value.open)

        // Leaving *because* the screen went off: the check at the moment of leaving.
        f.unlocked()
        f.screenOn = false
        f.lifecycle.value = HostLifecycle.Background
        assertFalse(f.lock.state.value.open)
    }

    @Test
    fun whileTheSheetIsUpLeavingAndComingBackDoNotLock() = runTest {
        // API 24–29: the screen-lock PIN is the system's own activity, and the process lifecycle
        // sees it as leaving. Locking on that would put the sheet straight back up.
        val f = Fixture()
        val answer = f.device.hold()
        val unlocking = async { f.lock.unlock("Unlock") }
        runCurrent()
        assertTrue(f.lock.state.value.prompting)

        f.lifecycle.value = HostLifecycle.Background
        f.clock.advance(minutes = 5)
        f.lifecycle.value = HostLifecycle.Foreground
        answer.complete(AuthOutcome.Success)

        assertEquals(AuthOutcome.Success, unlocking.await())
        assertTrue(f.lock.state.value.open)
        assertFalse(f.lock.state.value.prompting)
    }

    @Test
    fun walkingAwayFromAConfirmationStillCounts() = runTest {
        // The trap's other side: muting the lifecycle must not make the sheet a way to stay open.
        val f = Fixture().unlocked()
        val answer = f.device.hold()
        val confirming = async { f.lock.presence.confirm("Export") }
        runCurrent()
        assertTrue(f.lock.state.value.prompting)

        f.lifecycle.value = HostLifecycle.Background
        f.clock.advance(minutes = 5)
        answer.complete(AuthOutcome.Cancelled)
        assertFalse(confirming.await())

        f.lifecycle.value = HostLifecycle.Foreground
        assertFalse(f.lock.state.value.open)
    }

    @Test
    fun cancellingLeavesItLockedAndALockoutCarriesTheSystemsWords() = runTest {
        val f = Fixture()
        f.device.answer = AuthOutcome.Cancelled
        assertEquals(AuthOutcome.Cancelled, f.lock.unlock("Unlock"))
        assertTrue(f.lock.state.value.covers(secret))

        f.device.answer = AuthOutcome.LockedOut("Too many attempts")
        assertEquals(AuthOutcome.LockedOut("Too many attempts"), f.lock.unlock("Unlock"))
        assertTrue(f.lock.state.value.covers(secret))
    }

    @Test
    fun lockNowLocks() = runTest {
        val f = Fixture().unlocked()
        f.lock.lockNow()
        assertTrue(f.lock.state.value.covers(secret))
    }

    @Test
    fun anOptionalLockFollowsTheAppsDefaultUntilThePersonTouchesTheSwitch() = runTest {
        val off = AppId("off")
        val on = AppId("on")
        val f = Fixture(apps = listOf(StubApp("off", lock = LockPolicy.OptionalOff), StubApp("on", lock = LockPolicy.OptionalOn)))
        assertFalse(off in f.lock.state.value.guarded)
        assertTrue(on in f.lock.state.value.guarded)

        f.lock.setEnabled(on, false)
        assertFalse(on in f.lock.state.value.guarded)

        // An update flips the defaults. The person who switched `on` off keeps it off; `off`, which
        // nobody touched, follows its new default.
        val updated = Fixture(apps = listOf(StubApp("off", lock = LockPolicy.OptionalOn), StubApp("on", lock = LockPolicy.OptionalOff)))
        assertTrue(off in updated.lock.state.value.guarded)
        assertFalse(on in updated.lock.state.value.guarded)

        // And a move to Required overrides the choice: there is no switch left to have touched.
        val required = Fixture(apps = listOf(StubApp("on", lock = LockPolicy.Required)))
        assertTrue(on in required.lock.state.value.guarded)
        required.lock.setEnabled(on, false)
        assertTrue(on in required.lock.state.value.guarded)
    }

    @Test
    fun withNoScreenLockARequiredAppOpensUnderTheBannerUntilOneIsSetUp() = runTest {
        val f = Fixture(availability = AuthAvailability.NoScreenLock)
        assertFalse(f.lock.state.value.covers(secret))
        assertTrue(f.lock.state.value.unprotected(secret))
        // Nothing to ask with is not a refusal (D140).
        assertTrue(f.lock.presence.confirm("Export"))
        assertEquals(emptyList(), f.device.prompts)

        // Set up in the system's settings — from the background, so it is seen on the way back.
        f.device.availability = AuthAvailability.Available
        f.away(seconds = 5)
        assertFalse(f.lock.state.value.unprotected(secret))
        assertTrue(f.lock.state.value.covers(secret))
    }

    @Test
    fun aConfirmationIsTheDevicesSheetWithTheAppsReason() = runTest {
        val f = Fixture().unlocked()
        assertTrue(f.lock.presence.confirm("Export every account"))
        f.device.answer = AuthOutcome.Cancelled
        assertFalse(f.lock.presence.confirm("Show the key"))
        assertEquals(listOf("Unlock", "Export every account", "Show the key"), f.device.prompts)
    }

    @Test
    fun theSheetWaitsForTheAppToBeInFront() = runTest {
        // The curtain can come down in the background (the screen went off); its sheet must not
        // go up there, where on Android it would throw.
        val f = Fixture()
        f.lifecycle.value = HostLifecycle.Background
        val unlocking = async { f.lock.unlock("Unlock") }
        runCurrent()
        assertEquals(emptyList(), f.device.prompts)

        f.lifecycle.value = HostLifecycle.Foreground
        assertEquals(AuthOutcome.Success, unlocking.await())
        assertEquals(listOf("Unlock"), f.device.prompts)
    }

    @Test
    fun aDiscardedLinkToAProtectedAppIsLoggedWithoutItsQuery() {
        val events = MutableSharedFlow<SuperizerEvent>(replay = 8, extraBufferCapacity = 8)
        val route = PendingRoute(events) { it == AppId("2fa") }

        route.deliver("unitool://app/2fa/add?uri=otpauth%3A%2F%2Ftotp%2Fx%3Fsecret%3DJBSWY3DPEHPK3PXP")
        route.discard()
        route.deliver("unitool://app/calculator?mode=scientific")
        route.discard()

        val discarded = events.replayCache.filterIsInstance<SuperizerEvent.RouteDiscarded>()
        assertEquals("unitool://app/2fa/add", discarded[0].link)
        assertEquals("unitool://app/calculator?mode=scientific", discarded[1].link)
    }
}
