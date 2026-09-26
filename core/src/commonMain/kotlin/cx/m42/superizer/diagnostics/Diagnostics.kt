package cx.m42.superizer.diagnostics

import cx.m42.superizer.runtime.Platform
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * How the host's secrets and keys are kept, told honestly (Unitool ssh-new, 06).
 *
 * Codes and facts, never words: what a code means to a person is in the host's own tables
 * ([DiagnosticsStrings]), English first and translated from there, so that no screen, listing or
 * privacy text can say more than a code does (D179). The library knows the catalogue and the
 * mechanics — collecting findings, the heartbeat, when to run the self-test — and nothing about
 * any chip or cipher.
 */

/** Ordered: [worst][DiagnosticsReport.worst] is the maximum. */
@Serializable
public enum class Status {
    /** Everything as promised. */
    Ok,

    /** A fact with nothing to do: level 3 on desktop. */
    Info,

    /** Something to do: no backup for thirty days. */
    Attention,

    /** Protected less than the listing says: a software key, a key in a browser. */
    Unsafe,

    /** Not implemented or not set up: the data is plain. */
    Missing,

    /** The self-test failed; data may be unreadable. */
    Broken,
}

/** What a finding is about. */
@Serializable
public sealed interface Subject {
    @Serializable
    @SerialName("host-secrets")
    public data object HostSecrets : Subject

    @Serializable
    @SerialName("key")
    public data class Key(val id: String) : Subject

    @Serializable
    @SerialName("app-secrets")
    public data class AppSecrets(val appId: String) : Subject

    @Serializable
    @SerialName("backup")
    public data object Backup : Subject

    @Serializable
    @SerialName("device")
    public data object Device : Subject

    @Serializable
    @SerialName("self-test")
    public data object SelfTest : Subject
}

/** What the person can do about a finding. The words are the host's ([DiagnosticsStrings.action]). */
@Serializable
public enum class DiagnosticsAction { AddSshKey, SetPassphrase, Rekey, EnableScreenLock, RunSelfTest, MakeBackup, ShareReport, ImportKeyAgain }

@Serializable
public data class Finding(
    val subject: Subject,
    val status: Status,
    /** Stable id — a [DiagnosticsCode.id] — and the key into the words table. */
    val code: String,
    /** `location`, `level`, `hardware`, `n`, `days`, `date`, `step`, … — the `{…}` of the text. Never a secret. */
    val facts: Map<String, String> = emptyMap(),
    val actions: List<DiagnosticsAction> = emptyList(),
    /** The platform's own error text, untranslated, for the report. */
    val evidence: String? = null,
)

/** A finding from the catalogue, with the catalogue's status and actions. */
public fun DiagnosticsCode.finding(
    subject: Subject,
    facts: Map<String, String> = emptyMap(),
    evidence: String? = null,
): Finding = Finding(subject, status, id, facts, actions, evidence)

/** Facts about the device, for the report: model, OS, what the keyring found (`strongbox`, `tee`, …). */
@Serializable
public data class DeviceFacts(
    val model: String? = null,
    val os: String? = null,
    val facts: Map<String, String> = emptyMap(),
)

@Serializable
public data class SelfTestStep(
    /** A [SelfTestCode.id]. */
    val code: String,
    val passed: Boolean,
    val millis: Long,
    /** What the platform said — the expected refusal in `wrap.auth-required`, the error in a failure. */
    val evidence: String? = null,
    /** False for a step this platform has nothing to test with (`enclave.sign` on Android). */
    val ran: Boolean = true,
)

@Serializable
public data class SelfTestResult(
    val steps: List<SelfTestStep>,
    val at: Long,
    val hostVersion: String,
    val device: DeviceFacts = DeviceFacts(),
) {
    val passed: Boolean get() = steps.all { it.passed || !it.ran }
}

@Serializable
public data class DiagnosticsReport(
    val at: Long,
    val hostVersion: String,
    val platform: Platform,
    val device: DeviceFacts = DeviceFacts(),
    val findings: List<Finding> = emptyList(),
    /** Null: never run on this install. */
    val selfTest: SelfTestResult? = null,
) {
    val worst: Status get() = findings.maxOfOrNull { it.status } ?: Status.Ok
}

/** What one source saw. */
public data class Inspected(
    val findings: List<Finding>,
    /** Merged into [DiagnosticsReport.device] facts. */
    val device: Map<String, String> = emptyMap(),
)

/**
 * The host's view of its own storage (06 §1) — `Superizer.storage`. The Storage & security screen
 * reads it the way the Protection section reads the lock.
 */
public interface StorageDiagnostics {
    /** Latest report; refreshed by [inspect] at host start and by [selfTest] on demand. */
    public val report: StateFlow<DiagnosticsReport>

    /** Cheap, no prompts: every source, the heartbeat, the dates. Runs at every host start. */
    public suspend fun inspect(): DiagnosticsReport

    /** Runs the real cryptography with throwaway keys. May prompt once when [withConfirmation]. */
    public suspend fun selfTest(withConfirmation: Boolean): DiagnosticsReport

    /** Plain text for sharing (06 §5): English, codes and facts, raw platform errors, never secrets. */
    public fun exportText(): String
}

/** Findings the host cannot know itself: what a vault is backed by, what a keyring holds. The host's own implementation. */
public fun interface DiagnosticsSource {
    public suspend fun inspect(): Inspected
}

/** The self-test (06 §3). One implementation, run from Settings and from the instrumented tests alike. */
public fun interface SelfTestRunner {
    public suspend fun run(withConfirmation: Boolean): SelfTestResult
}

/**
 * What an app sees (06 §1): its own secrets' findings and the host-wide ones about secrets and the
 * device — enough to show "protected by…" or "NOT protected" in its own screen, in the host's words.
 */
public interface AppDiagnostics {
    public val findings: StateFlow<List<Finding>>
}

/**
 * The words. Declared here, written by the host: English as the source, other languages as
 * translations of the same interface (06 §6).
 */
public interface DiagnosticsStrings {
    public fun title(code: String): String

    /** [facts] fill the `{…}` of the text. */
    public fun detail(code: String, facts: Map<String, String>): String

    public fun action(action: DiagnosticsAction): String

    public fun status(status: Status): String
}

/** Codes as the words: what a host with no table shows — never nothing (06 §6). */
public object RawDiagnosticsStrings : DiagnosticsStrings {
    override fun title(code: String): String = code

    override fun detail(code: String, facts: Map<String, String>): String =
        facts.entries.joinToString(", ") { "${it.key}=${it.value}" }

    override fun action(action: DiagnosticsAction): String = action.name

    override fun status(status: Status): String = status.name
}
