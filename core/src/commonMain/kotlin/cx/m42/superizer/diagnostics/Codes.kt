package cx.m42.superizer.diagnostics

import cx.m42.superizer.diagnostics.DiagnosticsAction.AddSshKey
import cx.m42.superizer.diagnostics.DiagnosticsAction.EnableScreenLock
import cx.m42.superizer.diagnostics.DiagnosticsAction.ImportKeyAgain
import cx.m42.superizer.diagnostics.DiagnosticsAction.MakeBackup
import cx.m42.superizer.diagnostics.DiagnosticsAction.Rekey
import cx.m42.superizer.diagnostics.DiagnosticsAction.RunSelfTest
import cx.m42.superizer.diagnostics.DiagnosticsAction.ShareReport

/**
 * The catalogue of 06 §6.1: every finding a host may report, with its status and what can be done.
 *
 * An enum so that a host's words table is a `when` the compiler checks for completeness (D177): a
 * code added here without a text fails the host's build, not a person's screen. The [id] is what
 * travels — in a [Finding], in a report, in the words table's lookup.
 */
public enum class DiagnosticsCode(
    public val id: String,
    public val status: Status,
    public val actions: List<DiagnosticsAction> = emptyList(),
) {
    VaultChipOk("vault.chip.ok", Status.Ok),
    VaultSoftwareKey("vault.software-key", Status.Unsafe, listOf(AddSshKey)),
    VaultPassphraseOk("vault.passphrase.ok", Status.Info, listOf(MakeBackup)),
    VaultPassphraseBrowser("vault.passphrase.browser", Status.Unsafe, listOf(ShareReport)),
    VaultMissing("vault.missing", Status.Missing, listOf(AddSshKey)),
    VaultNotImplemented("vault.not-implemented", Status.Missing, listOf(ShareReport)),
    VaultKeyLost("vault.key-lost", Status.Broken, listOf(ImportKeyAgain, ShareReport)),
    VaultBroken("vault.broken", Status.Broken, listOf(RunSelfTest, ShareReport)),
    VaultSessionOpen("vault.session-open", Status.Info),

    KeyLevel1("key.level1", Status.Ok),
    KeyLevel2("key.level2", Status.Ok),
    KeyLevel3("key.level3", Status.Info, listOf(Rekey)),
    KeyLevel3Browser("key.level3.browser", Status.Unsafe, listOf(ShareReport)),
    KeyUnusable("key.unusable", Status.Broken, listOf(ImportKeyAgain)),
    KeySecretsAnchor("key.secrets-anchor", Status.Info),
    KeySessionPolicy("key.session-policy", Status.Attention),

    DeviceNoScreenLock("device.no-screen-lock", Status.Unsafe, listOf(EnableScreenLock)),
    DeviceNoSecureHardware("device.no-secure-hardware", Status.Unsafe, listOf(AddSshKey)),
    DeviceEmulator("device.emulator", Status.Info),

    /** Not in 06 §6.1 as written; added with the self-test step it comes from (06 §3, `passphrase.roundtrip`). */
    PassphraseSlow("passphrase.slow", Status.Attention),

    BackupNever("backup.never", Status.Attention, listOf(MakeBackup)),
    BackupStale("backup.stale", Status.Attention, listOf(MakeBackup)),
    BackupOk("backup.ok", Status.Ok),

    SelfTestNever("selftest.never", Status.Attention, listOf(RunSelfTest)),
    SelfTestOk("selftest.ok", Status.Ok),
    SelfTestFailed("selftest.failed", Status.Broken, listOf(ShareReport)),

    AppSecretsSealed("app.secrets.sealed", Status.Ok),
    AppSecretsPlain("app.secrets.plain", Status.Missing, listOf(AddSshKey)),
    ;

    public companion object {
        public fun of(id: String): DiagnosticsCode? = entries.firstOrNull { it.id == id }
    }
}

/** The self-test's steps (06 §3), in the order they run. */
public enum class SelfTestCode(public val id: String) {
    VaultRoundtrip("vault.roundtrip"),
    VaultHardware("vault.hardware"),
    VaultTamper("vault.tamper"),
    VaultNonce("vault.nonce"),
    WrapAuthRequired("wrap.auth-required"),
    WrapConfirmed("wrap.confirmed"),
    WrapPasscodeClass("wrap.passcode-class"),
    EnclaveSign("enclave.sign"),
    PassphraseRoundtrip("passphrase.roundtrip"),
    AgeRoundtrip("age.roundtrip"),
    StorageLocation("storage.location"),
    MemoryZeroed("memory.zeroed"),
    ReportClean("report.clean"),
    ;

    public companion object {
        public fun of(id: String): SelfTestCode? = entries.firstOrNull { it.id == id }
    }
}
