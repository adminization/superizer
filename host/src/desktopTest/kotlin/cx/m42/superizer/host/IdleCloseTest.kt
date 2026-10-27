package cx.m42.superizer.host

import cx.m42.superizer.ShellCommand
import cx.m42.superizer.Superizer
import cx.m42.superizer.activation.Activation
import cx.m42.superizer.activation.ActivationResult
import cx.m42.superizer.app.AppId
import cx.m42.superizer.app.LockPolicy
import cx.m42.superizer.lock.AuthOutcome
import cx.m42.superizer.runtime.HostLifecycle
import cx.m42.superizer.testing.FakeClock
import cx.m42.superizer.testing.FakeDeviceAuthenticator
import cx.m42.superizer.testing.FakeNetwork
import cx.m42.superizer.testing.FakeSecretVault
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * A secret app or one behind the lock, left open with nobody touching the screen, is closed a
 * minute later and the lock with it (Unitool, 2026-10-12). An ordinary app is never.
 */
class IdleCloseTest {

    @BeforeTest
    fun setUp() = isolatePrefs()

    private val lifecycle = MutableStateFlow(HostLifecycle.Foreground)
    private val device = FakeDeviceAuthenticator()

    private fun TestScope.host(): Superizer = Superizer.build(backgroundScope + UnconfinedTestDispatcher(testScheduler)) {
        host(testHostInfo(debug = true))
        network(FakeNetwork())
        clock(FakeClock())
        lifecycle(this@IdleCloseTest.lifecycle)
        deviceAuthenticator(device)
        secretVault(FakeSecretVault())
        register(StubApp("plain"))
        register(StubApp("locked", lock = LockPolicy.Required))
        register(StubApp("vault", hidden = true, secret = true))
        promoCodes(mapOf("VAULT-2026" to ActivationResult.Success(Activation(AppId("vault")))))
    }

    private fun TestScope.commandsOf(superizer: Superizer): List<ShellCommand> =
        mutableListOf<ShellCommand>().also { into ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { superizer.commands.collect { into += it } }
        }

    private fun TestScope.idle(seconds: Long) {
        testScheduler.advanceTimeBy(seconds * 1000)
        testScheduler.runCurrent()
    }

    @Test
    fun aLockedAppIsClosedAMinuteAfterTheLastTouchAndTheLockWithIt() = runTest {
        val superizer = host()
        val commands = commandsOf(superizer)
        assertEquals(AuthOutcome.Success, superizer.lock.unlock("Unlock"))
        superizer.handler.launch(AppId("locked"))

        idle(50)
        superizer.activity()
        idle(50)
        assertEquals(AppId("locked"), superizer.handler.current.value?.app?.id)
        assertTrue(superizer.lock.state.value.open)

        idle(11)
        assertNull(superizer.handler.current.value)
        assertFalse(superizer.lock.state.value.open)
        assertEquals(listOf<ShellCommand>(ShellCommand.Close), commands)
    }

    @Test
    fun aSecretAppIsClosedTooSoTheNextVisitIsByCodeAgain() = runTest {
        val superizer = host()
        val commands = commandsOf(superizer)
        val code = assertIs<ActivationResult.Success>(superizer.activation.fromPromo("VAULT-2026"))
        assertTrue(superizer.activation.apply(code.activation, source = "promo").isSuccess)

        idle(61)
        assertNull(superizer.handler.current.value)
        assertEquals(listOf<ShellCommand>(ShellCommand.Close), commands)
    }

    @Test
    fun anOrdinaryAppStaysHoweverLongNobodyTouchesIt() = runTest {
        val superizer = host()
        superizer.handler.launch(AppId("plain"))
        idle(3600)
        assertEquals(AppId("plain"), superizer.handler.current.value?.app?.id)
    }

    @Test
    fun theDevicesSheetAndTheBackgroundPauseIt() = runTest {
        val superizer = host()
        assertEquals(AuthOutcome.Success, superizer.lock.unlock("Unlock"))
        superizer.handler.launch(AppId("locked"))

        // In the background the grace decides, not this.
        lifecycle.value = HostLifecycle.Background
        idle(120)
        lifecycle.value = HostLifecycle.Foreground
        idle(30)
        assertEquals(AppId("locked"), superizer.handler.current.value?.app?.id)

        // A person answering the sheet is not idle.
        val held = device.hold()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { superizer.lock.unlock("Again") }
        idle(120)
        assertEquals(AppId("locked"), superizer.handler.current.value?.app?.id)
        held.complete(AuthOutcome.Success)
        idle(61)
        assertNull(superizer.handler.current.value)
    }
}
