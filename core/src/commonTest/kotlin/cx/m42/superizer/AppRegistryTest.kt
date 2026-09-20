package cx.m42.superizer

import cx.m42.superizer.app.AppId
import cx.m42.superizer.event.SuperizerEvent
import cx.m42.superizer.registry.AppRegistry
import cx.m42.superizer.registry.RegistrationOutcome
import cx.m42.superizer.runtime.ServiceKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableSharedFlow

class AppRegistryTest {

    private val events = MutableSharedFlow<SuperizerEvent>(replay = 32, extraBufferCapacity = 32)

    @Test
    fun anAppThatFitsIsRegistered() {
        val registry = AppRegistry(testHost(), events)
        assertTrue(registry.register(ProbeApp()))
        assertEquals(listOf(AppId("probe")), registry.all().map { it.id })
    }

    @Test
    fun twoAppsWithOneIdIsABuildMistakeAndThrows() {
        // Not a rejection: both apps are in this binary, so somebody can fix it before it ships.
        val registry = AppRegistry(testHost(), events)
        registry.register(ProbeApp())
        assertFailsWith<IllegalArgumentException> { registry.register(ProbeApp()) }
    }

    @Test
    fun anAppBuiltAgainstANewerContractIsRejectedRatherThanCrashed() {
        // D14: this is a deployment fact — an old host, a new app — and the Service Menu showing
        // the reason is worth more than an exception at startup.
        val registry = AppRegistry(testHost(contractVersion = 1), events)
        assertFalse(registry.register(ProbeApp(minHostContract = 99)))
        assertNull(registry.get(AppId("probe")))
        val outcome = registry.outcome(AppId("probe"))
        assertTrue(outcome is RegistrationOutcome.Rejected && "contract" in outcome.reason)
    }

    @Test
    fun anAppThatNeedsAServiceThisHostLacksIsRejected() {
        val camera = ServiceKey<Any>("camera")
        val registry = AppRegistry(testHost(), events)
        assertFalse(registry.register(ProbeApp(requires = setOf(camera))))
        val outcome = registry.outcome(AppId("probe"))
        assertTrue(outcome is RegistrationOutcome.Rejected && "camera" in outcome.reason)
    }

    @Test
    fun theSameAppIsAcceptedByAHostThatHasTheService() {
        val camera = ServiceKey<Any>("camera")
        val registry = AppRegistry(testHost(services = setOf(camera)), events)
        assertTrue(registry.register(ProbeApp(requires = setOf(camera))))
    }

    @Test
    fun aMalformedDeepLinkPathIsRejectedAtRegistration() {
        val registry = AppRegistry(testHost(), events)
        assertFalse(registry.register(ProbeApp(deepLinks = setOf("a path with spaces"))))
    }

    @Test
    fun aHiddenAppIsNotVisibleUntilItIsUnlocked() {
        val registry = AppRegistry(testHost(), events)
        registry.register(ProbeApp(id = "shown"))
        registry.register(ProbeApp(id = "secret", hidden = true))
        assertEquals(listOf(AppId("shown")), registry.visible(emptySet()).map { it.id })
        assertEquals(
            listOf(AppId("shown"), AppId("secret")),
            registry.visible(setOf(AppId("secret"))).map { it.id },
        )
    }

    @Test
    fun aRejectedAppIsStillListedSoSomebodyCanSeeWhy() {
        val registry = AppRegistry(testHost(contractVersion = 1), events)
        registry.register(ProbeApp(minHostContract = 99))
        assertEquals(1, registry.manifests.value.size)
    }
}
