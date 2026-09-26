package cx.m42.superizer.host

import cx.m42.superizer.Superizer
import cx.m42.superizer.app.AppId
import cx.m42.superizer.backup.BackupKeyName
import cx.m42.superizer.backup.BackupOutcome
import cx.m42.superizer.backup.BackupPolicy
import cx.m42.superizer.backup.HostBackupCipher
import cx.m42.superizer.diagnostics.DiagnosticsCode
import cx.m42.superizer.diagnostics.Inspected
import cx.m42.superizer.diagnostics.SelfTestResult
import cx.m42.superizer.diagnostics.SelfTestStep
import cx.m42.superizer.diagnostics.Status
import cx.m42.superizer.diagnostics.Subject
import cx.m42.superizer.diagnostics.finding
import cx.m42.superizer.host.storage.HostKeys
import cx.m42.superizer.host.storage.PrefsStorage
import cx.m42.superizer.registry.RegistrationOutcome
import cx.m42.superizer.runtime.ServiceKey
import cx.m42.superizer.secrets.SecretsUnavailableException
import cx.m42.superizer.ssh.SshOutcome
import cx.m42.superizer.testing.Canary
import cx.m42.superizer.testing.FakeClock
import cx.m42.superizer.testing.FakeDeviceAuthenticator
import cx.m42.superizer.testing.FakeNetwork
import cx.m42.superizer.testing.FakeSecretVault
import cx.m42.superizer.testing.LeakScanner
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking

/**
 * Contract 3's host mechanics (ssh-new 07 §6): `runtime.secrets` under a vault the host holds,
 * services bound to their caller, the diagnostics thread, and the backup walk — each against the
 * host's real prefs and fakes of what the host would plug in.
 */
class Contract3Test {

    @BeforeTest
    fun setUp() = isolatePrefs()

    // Not `clock`: inside the builder block that name is the builder's own (internal, and visible here).
    private val fakeClock = FakeClock()

    private fun host(
        vault: FakeSecretVault? = FakeSecretVault(),
        cipher: HostBackupCipher? = null,
        apps: List<StubApp> = listOf(StubApp("alpha"), StubApp("beta")),
        extra: SuperizerBuilder.() -> Unit = {},
    ): Superizer = Superizer.build(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)) {
        host(testHostInfo())
        network(FakeNetwork())
        clock(fakeClock)
        deviceAuthenticator(FakeDeviceAuthenticator())
        apps.forEach { register(it) }
        secretVault(vault)
        cipher?.let { backupCipher(it) }
        extra()
    }

    private fun Superizer.secretsOf(id: String) = requireNotNull(handler.appRuntime(AppId(id))) { "$id not enabled" }.secrets

    private fun storedText(): String = PrefsStorage.keys().joinToString("\n") { "$it=${PrefsStorage.get(it)}" }

    // ------------------------------------------------------------ runtime.secrets

    @Test
    fun aSecretIsSealedInPrefsAndOpensBackForItsApp() = runBlocking<Unit> {
        val superizer = host()
        superizer.secretsOf("alpha").set("accounts", "otpauth://totp/alpha?secret=JBSWY3DPEHPK3PXP")

        assertEquals("otpauth://totp/alpha?secret=JBSWY3DPEHPK3PXP", superizer.secretsOf("alpha").get("accounts"))
        assertEquals(setOf("accounts"), superizer.secretsOf("alpha").keys())
        assertFalse("JBSWY3DPEHPK3PXP" in storedText(), "the plain value is nowhere in prefs")
        assertTrue(PrefsStorage.get("secret.alpha.accounts")!!.startsWith("fake:"))
        // Namespaced like storage: the other app sees none of it.
        assertNull(superizer.secretsOf("beta").get("accounts"))
        assertEquals(emptySet(), superizer.secretsOf("beta").keys())
    }

    @Test
    fun aSecretThatCannotBeOpenedThrowsRatherThanReadingAsAbsent() = runBlocking<Unit> {
        val vault = FakeSecretVault()
        val superizer = host(vault)
        superizer.secretsOf("alpha").set("accounts", "[]")
        vault.declines = true
        assertFailsWith<SecretsUnavailableException> { superizer.secretsOf("alpha").get("accounts") }
        // Absent is still absent.
        assertNull(superizer.secretsOf("alpha").get("nothing"))
    }

    @Test
    fun aVaultThatCannotSealWritesNothing() = runBlocking<Unit> {
        val vault = FakeSecretVault().apply { failsToSeal = true }
        val superizer = host(vault)
        assertFailsWith<IllegalStateException> { superizer.secretsOf("alpha").set("accounts", "[1]") }
        assertNull(PrefsStorage.get("secret.alpha.accounts"))
    }

    @Test
    fun withoutAVaultSecretsArePlainAndResealingSealsThemWithoutAsking() = runBlocking<Unit> {
        val superizer = host(vault = null)
        superizer.secretsOf("alpha").set("accounts", "[plain]")
        assertEquals("plain:[plain]", PrefsStorage.get("secret.alpha.accounts"))
        assertEquals(1, superizer.secrets.counts()[AppId("alpha")]?.plain)

        val vault = FakeSecretVault().apply { silent = false }
        assertTrue(superizer.secrets.reseal(vault))
        assertSame(vault, superizer.secrets.vault.value)
        assertEquals(0, vault.asked, "sealing never asks (I16)")
        assertTrue(PrefsStorage.get("secret.alpha.accounts")!!.startsWith("fake:"))
        assertEquals("[plain]", superizer.secretsOf("alpha").get("accounts"))
        assertEquals(1, superizer.secrets.counts()[AppId("alpha")]?.sealed)
    }

    @Test
    fun resealingMovesEverythingOrNothing() = runBlocking<Unit> {
        val first = FakeSecretVault("first")
        val superizer = host(first)
        superizer.secretsOf("alpha").set("a", "1")
        superizer.secretsOf("beta").set("b", "2")

        first.declines = true
        assertFalse(superizer.secrets.reseal(FakeSecretVault("second")))
        assertTrue(PrefsStorage.get("secret.alpha.a")!!.startsWith("first:"), "nothing was rewritten")

        first.declines = false
        val second = FakeSecretVault("second")
        assertTrue(superizer.secrets.reseal(second))
        assertTrue(PrefsStorage.get("secret.alpha.a")!!.startsWith("second:"))
        assertTrue(PrefsStorage.get("secret.beta.b")!!.startsWith("second:"))
        assertEquals("2", superizer.secretsOf("beta").get("b"))
    }

    @Test
    fun resetErasesAnAppsSecretsWithItsStorage() = runBlocking<Unit> {
        val superizer = host()
        superizer.secretsOf("alpha").set("accounts", "[x]")
        superizer.secretsOf("beta").set("accounts", "[y]")
        superizer.diagnostics.reset(AppId("alpha"))
        assertNull(PrefsStorage.get("secret.alpha.accounts"))
        assertNotNull(PrefsStorage.get("secret.beta.accounts"))
    }

    // ------------------------------------------------------------ caller-bound services

    interface Greeter {
        val caller: AppId
    }

    @Test
    fun aCallerBoundServiceIsMadeOncePerAppAndCountsAsProvided() {
        val key = ServiceKey<Greeter>("greeter")
        val made = mutableListOf<AppId>()
        val superizer = host(apps = listOf(StubApp("alpha"), StubApp("beta"))) {
            service(key) { caller -> made += caller; object : Greeter { override val caller = caller } }
        }
        val alpha = superizer.handler.appRuntime(AppId("alpha"))!!.service(key)!!
        val beta = superizer.handler.appRuntime(AppId("beta"))!!.service(key)!!
        assertEquals(AppId("alpha"), alpha.caller)
        assertEquals(AppId("beta"), beta.caller)
        assertSame(alpha, superizer.handler.appRuntime(AppId("alpha"))!!.service(key))
        assertEquals(listOf(AppId("alpha"), AppId("beta")), made)
        assertTrue(key in superizer.hostInfo.services)
    }

    @Test
    fun aBackupPolicyOnAContractTwoManifestIsTurnedAway() {
        val superizer = host(apps = listOf(StubApp("old", backup = BackupPolicy.None, minContract = 2), StubApp("new", backup = BackupPolicy.None)))
        assertIs<RegistrationOutcome.Rejected>(superizer.registry.outcome(AppId("old")))
        assertEquals(RegistrationOutcome.Accepted, superizer.registry.outcome(AppId("new")))
    }

    // ------------------------------------------------------------ diagnostics

    @Test
    fun noVaultIsReportedAsMissingAndPlainSecretsPerApp() = runBlocking<Unit> {
        val superizer = host(vault = null)
        superizer.secretsOf("alpha").set("k", "v")
        val report = superizer.storage.inspect()
        assertTrue(report.findings.any { it.code == DiagnosticsCode.VaultMissing.id && it.subject == Subject.HostSecrets })
        val plain = report.findings.single { it.code == DiagnosticsCode.AppSecretsPlain.id }
        assertEquals(Subject.AppSecrets("alpha"), plain.subject)
        assertEquals("1", plain.facts["n"])
        assertEquals(Status.Missing, report.worst)
    }

    @Test
    fun theHeartbeatFindsALostKeyWithoutAskingAnyone() = runBlocking<Unit> {
        val vault = FakeSecretVault()
        val superizer = host(vault)
        superizer.storage.inspect()
        assertNotNull(PrefsStorage.get(HostKeys.HEARTBEAT), "first run seals the heartbeat")
        assertTrue(superizer.storage.inspect().findings.none { it.subject == Subject.HostSecrets && it.status >= Status.Broken })

        vault.keyLost = true
        assertTrue(superizer.storage.inspect().findings.any { it.code == DiagnosticsCode.VaultKeyLost.id })

        // A vault that would have to ask is not opened at all.
        vault.keyLost = false
        vault.silent = false
        superizer.storage.inspect()
        assertEquals(0, vault.asked)
    }

    @Test
    fun anAppSeesItsOwnFindingsAndTheHostWideOnes() = runBlocking<Unit> {
        val superizer = host(vault = null)
        superizer.secretsOf("alpha").set("k", "v")
        superizer.secretsOf("beta").set("k", "v")
        superizer.storage.inspect()
        val seen = superizer.handler.appRuntime(AppId("alpha"))!!.diagnostics.findings.value
        assertTrue(seen.any { it.subject == Subject.AppSecrets("alpha") })
        assertTrue(seen.none { it.subject == Subject.AppSecrets("beta") })
        assertTrue(seen.any { it.subject == Subject.HostSecrets })
        assertTrue(seen.none { it.subject == Subject.Backup })
    }

    @Test
    fun theSelfTestRunsOncePerVersionAndItsFailureIsBroken() = runBlocking<Unit> {
        var runs = 0
        var pass = true
        val runner = cx.m42.superizer.diagnostics.SelfTestRunner {
            runs++
            SelfTestResult(listOf(SelfTestStep("vault.roundtrip", passed = pass, millis = 3, evidence = if (pass) null else "KeyStoreException: -26")), fakeClock.now(), "0.0.0")
        }
        host { selfTest(runner) }
        assertEquals(1, runs, "run once on first start")
        host { selfTest(runner) }
        assertEquals(1, runs, "not again for the same version")

        pass = false
        val superizer = host { selfTest(runner) }
        val report = superizer.storage.selfTest(withConfirmation = true)
        assertEquals(2, runs)
        val failed = report.findings.single { it.code == DiagnosticsCode.SelfTestFailed.id }
        assertEquals("vault.roundtrip", failed.facts["step"])
        assertEquals(Status.Broken, report.worst)
    }

    @Test
    fun theReportCarriesCodesAndFactsAndNoSecret() = runBlocking<Unit> {
        val canary = Canary.PASSPHRASE.reveal().decodeToString()
        val superizer = host(vault = null) {
            diagnosticsSource { Inspected(listOf(DiagnosticsCode.KeyLevel3.finding(Subject.Key("SHA256:abc"), mapOf("level" to "3"))), mapOf("platform" to "desktop")) }
        }
        superizer.secretsOf("alpha").set("password", canary)
        superizer.storage.inspect()
        val text = superizer.storage.exportText()
        assertTrue("key.level3" in text && "vault.missing" in text && "platform = desktop" in text, text)
        assertEquals(emptyList(), LeakScanner.scan(text, listOf(Canary.PASSPHRASE)))
        assertEquals(emptyList(), LeakScanner.scan(superizer.storage.report.value.toString(), listOf(Canary.PASSPHRASE)))
    }

    // ------------------------------------------------------------ backup

    /** Reversible and visibly not the plaintext, so a test can see what the host handed it. */
    private class ReversingCipher(var decryptAnswer: ((ByteArray) -> SshOutcome<ByteArray>)? = null) : HostBackupCipher {
        var recipients: List<String> = emptyList()
        override suspend fun encrypt(bundle: ByteArray, recipients: List<String>): ByteArray {
            this.recipients = recipients
            return "AGE:".encodeToByteArray() + bundle.reversedArray()
        }

        override suspend fun decrypt(file: ByteArray): SshOutcome<ByteArray> =
            decryptAnswer?.invoke(file) ?: SshOutcome.Ok(file.copyOfRange(4, file.size).reversedArray())
    }

    @Test
    fun aBackupRestoresOnACleanHostWithSecretsSealedAgainAndPoliciesKept() = runBlocking<Unit> {
        val cipher = ReversingCipher()
        val apps = listOf(
            StubApp("alpha"),
            StubApp("calc", backup = BackupPolicy.None),
            StubApp("rates", backup = BackupPolicy.Except(setOf("cache"))),
        )
        val old = host(cipher = cipher, apps = apps) {
            backupKeys { listOf(BackupKeyName("SHA256:old", "work", 3)) }
        }
        val secret = Canary.PLAINTEXT.reveal().decodeToString()
        old.secretsOf("alpha").set("accounts", secret)
        old.handler.appRuntime(AppId("alpha"))!!.storage.set("order", "[1,2]")
        old.handler.appRuntime(AppId("calc"))!!.storage.set("display", "42")
        old.handler.appRuntime(AppId("rates"))!!.storage.set("cache", "{big}")
        old.handler.appRuntime(AppId("rates"))!!.storage.set("base", "EUR")
        old.settings.chooseLanguage("ru")

        val preview = old.backup.preview()
        assertTrue(preview.apps.single { it.id == "calc" }.excluded)

        val made = old.backup.export(listOf("ssh-ed25519 AAAA test"), "Back up everything")
        val file = assertIs<BackupOutcome.Ok<ByteArray>>(made).value
        assertEquals(listOf("ssh-ed25519 AAAA test"), cipher.recipients)
        assertEquals(fakeClock.now(), old.backup.lastBackupAt.value)
        // What the cipher got is the plain bundle; what comes out of export is only what it made.
        assertTrue(file.decodeToString().startsWith("AGE:"))

        isolatePrefs()
        val fresh = host(cipher = cipher, apps = apps)
        val plan = assertIs<BackupOutcome.Ok<cx.m42.superizer.backup.RestorePlan>>(fresh.backup.read(file)).value
        assertEquals(setOf("alpha", "rates"), plan.bundle.apps.keys, "a None app is not in the bundle")
        assertEquals(mapOf("base" to "EUR"), plan.bundle.apps.getValue("rates").storage, "an Except key is not in it")

        val report = assertIs<BackupOutcome.Ok<cx.m42.superizer.backup.RestoreReport>>(
            fresh.backup.restore(plan, setOf("alpha", "rates"), settings = true, reason = "Restore"),
        ).value
        assertEquals(listOf("alpha", "rates"), report.restored)
        assertEquals(listOf("SHA256:old"), report.keysToImport.map { it.fingerprint })

        assertEquals("[1,2]", PrefsStorage.get("app.alpha.order"))
        assertEquals("ru", PrefsStorage.get(HostKeys.LANGUAGE))
        val sealedAgain = PrefsStorage.get("secret.alpha.accounts")!!
        assertTrue(sealedAgain.startsWith("fake:"), "sealed by the new host's vault, not written open (I18)")
        assertEquals(emptyList(), LeakScanner.scan(storedText(), listOf(Canary.PLAINTEXT)))
        assertEquals(secret, fresh.secretsOf("alpha").get("accounts"))
    }

    @Test
    fun aBackupIsRefusedWhenASecretCannotBeOpenedOrThePersonDeclines() = runBlocking<Unit> {
        val vault = FakeSecretVault()
        val device = FakeDeviceAuthenticator()
        val superizer = Superizer.build(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)) {
            host(testHostInfo())
            network(FakeNetwork())
            deviceAuthenticator(device)
            register(StubApp("alpha"))
            secretVault(vault)
            backupCipher(ReversingCipher())
        }
        superizer.secretsOf("alpha").set("k", "v")
        vault.declines = true
        assertEquals(BackupOutcome.SecretsLocked, superizer.backup.export(listOf("ssh-ed25519 AAAA"), "why"))
        vault.declines = false
        assertEquals(BackupOutcome.NoRecipients, superizer.backup.export(listOf(" "), "why"))
        device.answer = cx.m42.superizer.lock.AuthOutcome.Cancelled
        assertEquals(BackupOutcome.Cancelled, superizer.backup.export(listOf("ssh-ed25519 AAAA"), "why"))
        assertNull(superizer.backup.lastBackupAt.value)
    }

    @Test
    fun aBundleOpenedOnAComputerIsReadAsItIs() = runBlocking<Unit> {
        val cipher = ReversingCipher(decryptAnswer = { error("must not be asked") })
        val superizer = host(cipher = cipher)
        val json = """{"schema":1,"host":{"name":"Unitool","version":"1","contract":3,"platform":"Android","at":0},"apps":{"alpha":{"version":"1.0.0","storage":{"a":"b"}}}}"""
        val plan = assertIs<BackupOutcome.Ok<cx.m42.superizer.backup.RestorePlan>>(superizer.backup.read(json.encodeToByteArray())).value
        assertEquals(mapOf("a" to "b"), plan.bundle.apps.getValue("alpha").storage)
        assertEquals(BackupOutcome.Unreadable("not-a-backup"), superizer.backup.read("{nope".encodeToByteArray()))
        assertEquals(
            BackupOutcome.Unreadable("newer-schema"),
            superizer.backup.read("""{"schema":9,"host":{"name":"x","version":"1","contract":3,"platform":"Android","at":0}}""".encodeToByteArray()),
        )
    }

    @Test
    fun theBackupFindingFollowsTheLastBackup() = runBlocking<Unit> {
        val superizer = host(cipher = ReversingCipher())
        assertTrue(superizer.storage.inspect().findings.any { it.code == DiagnosticsCode.BackupNever.id })
        superizer.backup.export(listOf("ssh-ed25519 AAAA"), "why")
        assertTrue(superizer.storage.inspect().findings.any { it.code == DiagnosticsCode.BackupOk.id })
        fakeClock.advance(seconds = 31L * 24 * 3600)
        val stale = superizer.storage.inspect().findings.single { it.subject == Subject.Backup }
        assertEquals(DiagnosticsCode.BackupStale.id, stale.code)
        assertEquals("31", stale.facts["days"])
        assertNotEquals(Status.Ok, stale.status)
    }
}
