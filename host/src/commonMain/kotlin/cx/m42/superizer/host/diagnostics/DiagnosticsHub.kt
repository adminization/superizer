package cx.m42.superizer.host.diagnostics

import cx.m42.superizer.app.AppId
import cx.m42.superizer.diagnostics.AppDiagnostics
import cx.m42.superizer.diagnostics.DeviceFacts
import cx.m42.superizer.diagnostics.DiagnosticsCode
import cx.m42.superizer.diagnostics.DiagnosticsReport
import cx.m42.superizer.diagnostics.DiagnosticsSource
import cx.m42.superizer.diagnostics.Finding
import cx.m42.superizer.diagnostics.SelfTestResult
import cx.m42.superizer.diagnostics.SelfTestRunner
import cx.m42.superizer.diagnostics.StorageDiagnostics
import cx.m42.superizer.diagnostics.Subject
import cx.m42.superizer.diagnostics.finding
import cx.m42.superizer.host.secrets.HostSecrets
import cx.m42.superizer.host.storage.HostKeys
import cx.m42.superizer.host.storage.SafePrefs
import cx.m42.superizer.runtime.Clock
import cx.m42.superizer.runtime.HostInfo
import cx.m42.superizer.runtime.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * The one thread through the host's storage (06 §1–§3): what the host knows itself, what each
 * [DiagnosticsSource] of the host's says, and the self-test the host's [SelfTestRunner] performs.
 *
 * The host's own findings: whether there is a vault at all, the heartbeat, each app's secrets
 * sealed or plain, the last backup, the last self-test. Everything about *which* chip or cipher
 * comes from the sources — the library knows none.
 *
 * Nothing here asks the person anything at start: the heartbeat opens only when the vault says it
 * can without asking ([cx.m42.superizer.secrets.SecretVault.opensSilently]).
 */
public class DiagnosticsHub(
    private val host: HostInfo,
    private val clock: Clock,
    private val secrets: HostSecrets,
    private val lastBackupAt: StateFlow<Long?>,
    private val sources: List<DiagnosticsSource>,
    private val runner: SelfTestRunner?,
    private val scope: CoroutineScope,
    private val logger: Logger? = null,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : StorageDiagnostics {

    private val _report = MutableStateFlow(
        DiagnosticsReport(at = clock.now(), hostVersion = host.version, platform = host.platform, selfTest = storedSelfTest()),
    )
    override val report: StateFlow<DiagnosticsReport> get() = _report.asStateFlow()

    private val running = Mutex()

    override suspend fun inspect(): DiagnosticsReport {
        val facts = linkedMapOf<String, String>()
        val findings = mutableListOf<Finding>()
        findings += vaultFindings()
        findings += appFindings()
        findings += backupFindings()
        val selfTest = storedSelfTest()
        findings += selfTestFindings(selfTest)
        sources.forEach { source ->
            runCatching { source.inspect() }
                .onSuccess { findings += it.findings; facts += it.device }
                .onFailure {
                    // A source that throws is a broken storage story, not a crash at start.
                    logger?.warn("diagnostics source failed", it)
                    findings += DiagnosticsCode.VaultBroken.finding(Subject.HostSecrets, mapOf("step" to "inspect"), it.describe())
                }
        }
        val next = DiagnosticsReport(
            at = clock.now(),
            hostVersion = host.version,
            platform = host.platform,
            device = DeviceFacts(facts = facts),
            findings = findings.sortedByDescending { it.status },
            selfTest = selfTest,
        )
        _report.value = next
        return next
    }

    override suspend fun selfTest(withConfirmation: Boolean): DiagnosticsReport = running.withLock {
        val runner = runner ?: return@withLock inspect()
        val result = runCatching { runner.run(withConfirmation) }.getOrElse {
            logger?.warn("self-test threw", it)
            SelfTestResult(
                steps = listOf(cx.m42.superizer.diagnostics.SelfTestStep("selftest.run", passed = false, millis = 0, evidence = it.describe())),
                at = clock.now(),
                hostVersion = host.version,
            )
        }
        SafePrefs.put(HostKeys.SELF_TEST, json.encodeToString(SelfTestResult.serializer(), result))
        inspect()
    }

    override fun exportText(): String = ReportText.format(host, _report.value)

    /** Findings that concern [appId]: its own secrets, and the host-wide ones about secrets and the device. */
    public fun forApp(appId: AppId): AppDiagnostics = object : AppDiagnostics {
        override val findings: StateFlow<List<Finding>> = _report
            .map { report -> report.findings.filter { it.concerns(appId) } }
            .stateIn(scope, SharingStarted.Eagerly, _report.value.findings.filter { it.concerns(appId) })
    }

    /**
     * At every start: inspect, then — once per install and once per new host version — the self-test,
     * without confirmation (06 §2, question 89).
     */
    public suspend fun onStart() {
        inspect()
        val last = storedSelfTest()
        if (runner != null && last?.hostVersion != host.version) selfTest(withConfirmation = false)
    }

    private fun Finding.concerns(appId: AppId): Boolean = when (val s = subject) {
        is Subject.AppSecrets -> s.appId == appId.value
        Subject.HostSecrets, Subject.Device -> true
        else -> false
    }

    private suspend fun vaultFindings(): List<Finding> {
        val vault = secrets.vault.value ?: return listOf(DiagnosticsCode.VaultMissing.finding(Subject.HostSecrets))
        if (!vault.opensSilently) return emptyList()
        val stored = SafePrefs.get(HostKeys.HEARTBEAT)
        return runCatching {
            if (stored == null) {
                SafePrefs.put(HostKeys.HEARTBEAT, vault.seal(HEARTBEAT))
                emptyList()
            } else if (vault.open(stored) == HEARTBEAT) {
                emptyList()
            } else {
                listOf(DiagnosticsCode.VaultKeyLost.finding(Subject.HostSecrets))
            }
        }.getOrElse {
            listOf(DiagnosticsCode.VaultBroken.finding(Subject.HostSecrets, mapOf("step" to "heartbeat"), it.describe()))
        }
    }

    private suspend fun appFindings(): List<Finding> = secrets.counts().flatMap { (id, count) ->
        buildList {
            if (count.sealed > 0) add(DiagnosticsCode.AppSecretsSealed.finding(Subject.AppSecrets(id.value), mapOf("n" to count.sealed.toString())))
            if (count.plain > 0) add(DiagnosticsCode.AppSecretsPlain.finding(Subject.AppSecrets(id.value), mapOf("n" to count.plain.toString())))
        }
    }

    private fun backupFindings(): List<Finding> {
        val at = lastBackupAt.value ?: return listOf(DiagnosticsCode.BackupNever.finding(Subject.Backup))
        val days = ((clock.now() - at) / DAY).coerceAtLeast(0)
        return if (days > STALE_DAYS) {
            listOf(DiagnosticsCode.BackupStale.finding(Subject.Backup, mapOf("days" to days.toString(), "date" to at.toString())))
        } else {
            listOf(DiagnosticsCode.BackupOk.finding(Subject.Backup, mapOf("date" to at.toString())))
        }
    }

    private fun selfTestFindings(result: SelfTestResult?): List<Finding> {
        if (runner == null && result == null) return emptyList()
        if (result == null) return listOf(DiagnosticsCode.SelfTestNever.finding(Subject.SelfTest))
        if (result.passed) {
            return listOf(
                DiagnosticsCode.SelfTestOk.finding(
                    Subject.SelfTest,
                    mapOf("date" to result.at.toString(), "n" to result.steps.count { it.ran }.toString()),
                ),
            )
        }
        val failed = result.steps.first { it.ran && !it.passed }
        return listOf(
            DiagnosticsCode.SelfTestFailed.finding(
                Subject.SelfTest,
                mapOf("step" to failed.code, "date" to result.at.toString()),
                failed.evidence,
            ),
        )
    }

    private fun storedSelfTest(): SelfTestResult? = SafePrefs.get(HostKeys.SELF_TEST)?.let {
        runCatching { json.decodeFromString(SelfTestResult.serializer(), it) }.getOrNull()
    }

    private fun Throwable.describe(): String = "${this::class.simpleName}: ${message.orEmpty()}".take(300)

    private companion object {
        const val HEARTBEAT = "superizer-heartbeat"
        const val DAY = 24L * 60 * 60 * 1000
        const val STALE_DAYS = 30
    }
}
