package cx.m42.superizer

import cx.m42.superizer.app.AppId
import cx.m42.superizer.app.PushUse
import cx.m42.superizer.app.AppProtection
import cx.m42.superizer.app.LockPolicy
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
    fun aSecretAppIsHiddenAsksForContractFourAndHasNoDoorButItsCode() {
        // idea/09: whatever else could open a secret app is a mistake in its manifest, and the
        // mistake fails here — not as a notification on somebody's lock screen.
        val registry = AppRegistry(testHost(), events)
        val cases = mapOf(
            "not-hidden" to ProbeApp(id = "not-hidden", secret = true, minHostContract = 4),
            "old-contract" to ProbeApp(id = "old-contract", secret = true, hidden = true, minHostContract = 3),
            "with-link" to ProbeApp(id = "with-link", secret = true, hidden = true, minHostContract = 4, deepLinks = setOf("open")),
            "with-push" to ProbeApp(id = "with-push", secret = true, hidden = true, minHostContract = 4, pushTopics = setOf("news")),
        )
        cases.forEach { (id, app) ->
            assertFalse(registry.register(app), id)
            assertTrue(registry.outcome(AppId(id)) is RegistrationOutcome.Rejected, id)
        }

        assertTrue(registry.register(ProbeApp(id = "secret", secret = true, hidden = true, minHostContract = 4)))
        assertTrue(registry.isSecret(AppId("secret")))
        assertFalse(registry.isSecret(AppId("no-such-app")))
        assertFalse(registry.isSecret(null))
    }

    @Test
    fun aSecretAppIsNeverVisibleWhateverTheUnlockStoreSays() {
        val registry = AppRegistry(testHost(), events)
        registry.register(ProbeApp(id = "secret", secret = true, hidden = true, minHostContract = 4))
        registry.register(ProbeApp(id = "hidden", hidden = true))
        registry.register(ProbeApp(id = "plain"))

        val unlocked = setOf(AppId("secret"), AppId("hidden"))
        assertEquals(listOf(AppId("hidden"), AppId("plain")), registry.visible(unlocked).map { it.id })
        assertEquals(listOf(AppId("plain")), registry.visible(emptySet()).map { it.id })
    }

    @Test
    fun pushAsksForContractFiveAndTopicsComeOnlyWithPush() {
        // push-opt-in, D417: a contract-4 host would mint a token at start and never ask; topics
        // with push = None say two things at once.
        val registry = AppRegistry(testHost(), events)
        val cases = mapOf(
            "old-contract" to ProbeApp(id = "old-contract", minHostContract = 4, push = PushUse.Alerts),
            "topics-no-push" to ProbeApp(id = "topics-no-push", minHostContract = 5, pushTopics = setOf("news")),
            "secret-push" to ProbeApp(id = "secret-push", secret = true, hidden = true, minHostContract = 5, push = PushUse.Silent),
        )
        cases.forEach { (id, app) ->
            assertFalse(registry.register(app), id)
            assertTrue(registry.outcome(AppId(id)) is RegistrationOutcome.Rejected, id)
        }

        assertTrue(registry.register(ProbeApp(id = "alerts", minHostContract = 5, push = PushUse.Alerts, pushTopics = setOf("news"))))
        assertTrue(registry.register(ProbeApp(id = "silent", minHostContract = 5, push = PushUse.Silent)))
        assertEquals(PushUse.Alerts, registry.pushUse(AppId("alerts")))
        assertEquals(PushUse.Silent, registry.pushUse(AppId("silent")))
        assertEquals(PushUse.None, registry.pushUse(AppId("no-such-app")))
    }

    @Test
    fun anAppThatDeclaresProtectionMustAskForTheContractThatHasIt() {
        // D138: on a contract-1 host such a manifest would run with no lock. Refusing it on this
        // host as well is what makes the forgotten `minHostContract = 2` fail in the first test.
        val locked = AppProtection(lock = LockPolicy.Required, secureWindow = true)
        val registry = AppRegistry(testHost(), events)
        assertFalse(registry.register(ProbeApp(id = "careless", protection = locked)))
        val outcome = registry.outcome(AppId("careless"))
        assertTrue(outcome is RegistrationOutcome.Rejected && "protection" in outcome.reason)

        assertTrue(registry.register(ProbeApp(id = "careful", minHostContract = 2, protection = locked)))
    }

    @Test
    fun aHostOfContractOneRejectsAProtectedApp() {
        val registry = AppRegistry(testHost(contractVersion = 1), events)
        val locked = AppProtection(lock = LockPolicy.Required)
        assertFalse(registry.register(ProbeApp(minHostContract = 2, protection = locked)))
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
