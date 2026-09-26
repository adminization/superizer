package cx.m42.superizer

/**
 * The revision of the app contract this build of `superizer:core` implements.
 *
 * A plain integer and not the library's semver (D14, question 13 in 09): an app needs one
 * comparison — "is the host at least this new" — and a host publishing 0.2 and 0.3 of the library
 * with no change to the contract should not make every app look out of date. It goes up only when
 * `apiCheck` shows an incompatible change to `core`, and the CHANGELOG says so when it does.
 *
 * A host puts this in [cx.m42.superizer.runtime.HostInfo.contractVersion]; an app declares the
 * oldest it tolerates in [cx.m42.superizer.app.AppManifest.minHostContract].
 *
 * 2 added `AppManifest.protection`, the lock port and `UserPresence` (06, D138). An app that
 * declares protection has to ask for 2, so that an older host rejects it rather than showing it
 * unlocked.
 *
 * 3 added `runtime.secrets` (values the host seals itself), `runtime.diagnostics` and
 * `AppManifest.backup` (Unitool ssh-new 05–07). An app that uses any of them asks for 3; one that
 * declares a backup policy has to, or the registry turns it away.
 */
public object SuperizerContract {
    public const val VERSION: Int = 3
}
