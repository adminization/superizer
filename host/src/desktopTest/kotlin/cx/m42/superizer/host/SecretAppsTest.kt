package cx.m42.superizer.host

import cx.m42.superizer.ShellCommand
import cx.m42.superizer.Superizer
import cx.m42.superizer.activation.Activation
import cx.m42.superizer.activation.ActivationResult
import cx.m42.superizer.app.AppId
import cx.m42.superizer.backup.BackupOutcome
import cx.m42.superizer.backup.HostBackupCipher
import cx.m42.superizer.backup.RestorePlan
import cx.m42.superizer.backup.RestoreReport
import cx.m42.superizer.diagnostics.Subject
import cx.m42.superizer.event.SuperizerEvent
import cx.m42.superizer.host.storage.PrefsStorage
import cx.m42.superizer.runtime.HostLifecycle
import cx.m42.superizer.runtime.NavigationError
import cx.m42.superizer.ssh.SshOutcome
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * The secret app of Unitool idea/09, against a whole host: only its code opens it, one visit at a
 * time, and nothing a person or another app can look at says it exists.
 */
class SecretAppsTest {

    @BeforeTest
    fun setUp() = isolatePrefs()

    private val fakeClock = FakeClock()
    private val lifecycle = MutableStateFlow(HostLifecycle.Foreground)

    private fun host(
        debug: Boolean = true,
        vaultSecret: Boolean = true,
        cipher: HostBackupCipher? = null,
    ): Superizer = Superizer.build(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)) {
        host(testHostInfo(debug))
        network(FakeNetwork())
        clock(fakeClock)
        lifecycle(this@SecretAppsTest.lifecycle)
        deviceAuthenticator(FakeDeviceAuthenticator())
        secretVault(FakeSecretVault())
        register(StubApp("alpha"))
        register(StubApp("vault", hidden = true, secret = vaultSecret))
        cipher?.let { backupCipher(it) }
        // The table says "unlock", as a careless host's would; the host overrules it for a secret app.
        promoCodes(mapOf("VAULT-2026" to ActivationResult.Success(Activation(AppId("vault")))))
    }

    private suspend fun Superizer.openByCode(): Activation {
        val code = assertIs<ActivationResult.Success>(activation.fromPromo("vault 2026"))
        assertTrue(activation.apply(code.activation, source = "promo").isSuccess)
        return code.activation
    }

    // ------------------------------------------------------------ the one door

    @Test
    fun everyDoorButTheCodeAnswersAsThoughItDidNotExist() = runBlocking<Unit> {
        val superizer = host()
        val unknown = ActivationResult.Rejected(ActivationResult.Reason.UnknownApp)
        // The same answer a made-up id gets: `Locked` would say there is something to unlock.
        assertEquals(unknown, superizer.activation.fromQr("""{"type":"app_activation","appId":"no-such-app"}"""))
        assertEquals(unknown, superizer.activation.fromQr("""{"type":"app_activation","appId":"vault"}"""))
        assertEquals(unknown, superizer.activation.fromQr("superizer://activate?a=vault"))
        assertEquals(unknown, superizer.activation.fromDeepLink("superizer://activate?a=vault"))
        assertEquals(unknown, superizer.activation.fromDeepLink("superizer://app/vault"))
        assertEquals(unknown, superizer.activation.fromDeepLink("""{"type":"app_activation","appId":"vault"}"""))
        assertEquals(emptySet(), superizer.unlocked.value)
    }

    @Test
    fun itsCodeOpensItForOneVisitWithoutUnlockingIt() = runBlocking<Unit> {
        val superizer = host()
        val used = superizer.openByCode()

        assertFalse(used.unlock)
        assertEquals(AppId("vault"), superizer.handler.current.value?.app?.id)
        assertFalse(AppId("vault") in superizer.unlocked.value)
        assertFalse(AppId("vault") in superizer.home.value)

        // The activation is a ticket, spent once; an equal one built by hand is not a ticket at all.
        superizer.handler.close(force = true)
        assertTrue(superizer.activation.apply(used, source = "promo").isFailure)
        assertTrue(superizer.activation.apply(Activation(AppId("vault"), unlock = false), source = "promo").isFailure)
        assertNull(superizer.handler.current.value)

        // The next visit is by code again, and it works every time.
        superizer.openByCode()
        assertEquals(AppId("vault"), superizer.handler.current.value?.app?.id)
    }

    @Test
    fun neitherHomeNorTheServiceMenuCanRevealIt() = runBlocking<Unit> {
        val superizer = host()
        superizer.addToHome(AppId("vault"))
        superizer.diagnostics.unlock(AppId("vault"))

        assertFalse(AppId("vault") in superizer.home.value)
        assertFalse(AppId("vault") in superizer.unlocked.value)
    }

    @Test
    fun aBuildThatTurnedAHiddenAppSecretForgetsItsUnlockAndItsTile() = runBlocking<Unit> {
        val before = host(vaultSecret = false)
        before.diagnostics.unlock(AppId("vault"))
        assertTrue(AppId("vault") in before.home.value)

        val after = host()
        assertFalse(AppId("vault") in after.unlocked.value)
        assertFalse(AppId("vault") in after.home.value)
    }

    // ------------------------------------------------------------ other apps

    @Test
    fun anotherAppCanNeitherListNorOpenNorOverhearIt() = runBlocking<Unit> {
        val superizer = host()
        val alpha = superizer.handler.launch(AppId("alpha")).getOrThrow().runtime

        assertEquals(listOf(AppId("alpha")), alpha.apps.list().map { it.id })
        assertNull(alpha.apps.get(AppId("vault")))
        val refused = alpha.navigation.openApp(AppId("vault")).exceptionOrNull()
        assertIs<NavigationError.UnknownApp>(refused)

        val alphaHeard = mutableListOf<SuperizerEvent>()
        val vaultHeard = mutableListOf<SuperizerEvent>()
        val listening = CoroutineScope(Dispatchers.Unconfined)
        listening.launch { alpha.events.collect { alphaHeard += it } }
        listening.launch { superizer.handler.appRuntime(AppId("vault"))!!.events.collect { vaultHeard += it } }
        superizer.openByCode()
        listening.cancel()

        assertTrue(alphaHeard.isNotEmpty(), "alpha heard its own close")
        assertTrue(alphaHeard.none { it.appId == AppId("vault") }, "alpha heard: $alphaHeard")
        assertTrue(vaultHeard.any { it is SuperizerEvent.Launched }, "the secret app still hears itself")
    }

    @Test
    fun aPushAimedAtItIsDroppedAsUnknown() = runBlocking<Unit> {
        val superizer = host()
        superizer.diagnostics.simulatePush(mapOf("schemaVersion" to "1", "appId" to "vault", "tapped" to "true"))

        val dropped = superizer.diagnostics.recentEvents.value.filterIsInstance<SuperizerEvent.PushDropped>().single()
        assertEquals("UnknownApp", dropped.reason)
        assertNull(superizer.handler.current.value)
    }

    // ------------------------------------------------------------ what a person can read

    @Test
    fun aReleaseBuildKeepsItOutOfTheEventListAndTheLog() = runBlocking<Unit> {
        val release = host(debug = false)
        release.openByCode()
        release.handler.appRuntime(AppId("vault"))!!.logger.warn("vault was here")

        assertTrue(release.diagnostics.recentEvents.value.none { it.appId == AppId("vault") })
        assertTrue(release.diagnostics.log.value.none { "vault" in it }, release.diagnostics.log.value.joinToString("\n"))

        isolatePrefs()
        val debug = host(debug = true)
        debug.openByCode()
        debug.handler.appRuntime(AppId("vault"))!!.logger.warn("vault was here")
        assertTrue(debug.diagnostics.recentEvents.value.any { it is SuperizerEvent.Activated && it.appId == AppId("vault") })
        assertTrue(debug.diagnostics.log.value.any { "vault was here" in it })
    }

    @Test
    fun storageAndSecurityNeverNamesItButTheAppStillHearsItsOwnFindings() = runBlocking<Unit> {
        val superizer = host()
        superizer.handler.appRuntime(AppId("vault"))!!.secrets.set("pin", "1234")
        superizer.handler.appRuntime(AppId("alpha"))!!.secrets.set("token", "abc")

        val report = superizer.storage.inspect()
        val named = report.findings.mapNotNull { (it.subject as? Subject.AppSecrets)?.appId }
        assertEquals(listOf("alpha"), named.distinct())
        assertFalse("vault" in superizer.storage.exportText())

        val own = superizer.handler.appRuntime(AppId("vault"))!!.diagnostics.findings.value
        assertTrue(own.any { (it.subject as? Subject.AppSecrets)?.appId == "vault" }, "own: $own")
    }

    @Test
    fun itsDataGoesIntoTheBackupAndComesBackWithoutALineAnywhere() = runBlocking<Unit> {
        val cipher = ReversingCipher()
        val old = host(cipher = cipher)
        old.handler.appRuntime(AppId("vault"))!!.storage.set("visits", "3")
        old.handler.appRuntime(AppId("alpha"))!!.storage.set("sum", "12")

        assertEquals(listOf("alpha"), old.backup.preview().apps.map { it.id })
        val file = assertIs<BackupOutcome.Ok<ByteArray>>(old.backup.export(listOf("ssh-ed25519 AAAA"), "Back up")).value

        isolatePrefs()
        val fresh = host(cipher = cipher)
        val plan = assertIs<BackupOutcome.Ok<RestorePlan>>(fresh.backup.read(file)).value
        assertEquals(listOf("alpha"), plan.apps.map { it.id }, "the list the person ticks")
        assertTrue("vault" in plan.bundle.apps, "but the data is in the file")

        val report = assertIs<BackupOutcome.Ok<RestoreReport>>(
            fresh.backup.restore(plan, setOf("alpha"), settings = false, reason = "Restore"),
        ).value
        assertEquals(listOf("alpha"), report.restored)
        assertEquals("3", PrefsStorage.get("app.vault.visits"), "restored with the rest, nobody ticked it")
    }

    // ------------------------------------------------------------ the background

    @Test
    fun aLongStayInTheBackgroundClosesItAndTheShellGoesHome() = runBlocking<Unit> {
        val superizer = host()
        val commands = mutableListOf<ShellCommand>()
        val listening = CoroutineScope(Dispatchers.Unconfined)
        listening.launch { superizer.commands.collect { commands += it } }
        superizer.openByCode()

        // A short trip — copying something from another app — finds it where it was left.
        lifecycle.value = HostLifecycle.Background
        fakeClock.advance(minutes = 1)
        lifecycle.value = HostLifecycle.Foreground
        assertEquals(AppId("vault"), superizer.handler.current.value?.app?.id)
        assertEquals(emptyList(), commands)

        lifecycle.value = HostLifecycle.Background
        fakeClock.advance(minutes = 6)
        lifecycle.value = HostLifecycle.Foreground
        listening.cancel()

        assertNull(superizer.handler.current.value)
        assertEquals(listOf<ShellCommand>(ShellCommand.Close), commands)
    }

    @Test
    fun anOrdinaryAppStaysOpenHoweverLongItWasAway() = runBlocking<Unit> {
        val superizer = host()
        superizer.handler.launch(AppId("alpha"))
        lifecycle.value = HostLifecycle.Background
        fakeClock.advance(hours = 3)
        lifecycle.value = HostLifecycle.Foreground
        assertEquals(AppId("alpha"), superizer.handler.current.value?.app?.id)
    }

    /** Reversible and visibly not the plaintext, as in [Contract3Test]. */
    private class ReversingCipher : HostBackupCipher {
        override suspend fun encrypt(bundle: ByteArray, recipients: List<String>): ByteArray =
            "AGE:".encodeToByteArray() + bundle.reversedArray()

        override suspend fun decrypt(file: ByteArray): SshOutcome<ByteArray> =
            SshOutcome.Ok(file.copyOfRange(4, file.size).reversedArray())
    }
}
