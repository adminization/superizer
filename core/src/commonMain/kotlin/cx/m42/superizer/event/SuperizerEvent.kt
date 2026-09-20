package cx.m42.superizer.event

import cx.m42.superizer.app.AppConfig
import cx.m42.superizer.app.AppId

/**
 * Everything the host does to an app, as a value.
 *
 * Adminizer emits strings on an emitter (`app:registered`, `app:enabled`); a sealed hierarchy says
 * the same thing with the compiler holding the list. The bench app renders this flow as its
 * Lifecycle card, and the Service Menu as its event log — which is the whole of "the lifecycle is
 * observable" in §22.
 *
 * Replay is 0 (05): listeners registered in `setup` subscribe before the first launch, and the
 * current truth is always `handler.states` / `handler.current`. A log is not a source of truth.
 */
public sealed interface SuperizerEvent {
    public val appId: AppId?

    public data class Registered(override val appId: AppId, val version: String) : SuperizerEvent

    /** The manifest does not fit this host (D47): contract too new, a required service missing or unknown, a bad path. */
    public data class RegistrationRejected(override val appId: AppId, val reason: String) : SuperizerEvent

    /** `setup()` registered something the manifest does not declare (D47). The app stays disabled. */
    public data class SetupMismatch(override val appId: AppId, val what: String) : SuperizerEvent

    public data class Enabled(override val appId: AppId) : SuperizerEvent

    public data class Disabled(override val appId: AppId) : SuperizerEvent

    public data class RuntimeCreated(override val appId: AppId) : SuperizerEvent

    public data class Configured(override val appId: AppId, val config: AppConfig) : SuperizerEvent

    /** The payload did not decode (D18). The launch continues on the default unless the spec forbids it. */
    public data class ConfigRejected(
        override val appId: AppId,
        val config: AppConfig,
        val reason: String,
    ) : SuperizerEvent

    public data class Created(override val appId: AppId) : SuperizerEvent

    public data class Launched(override val appId: AppId) : SuperizerEvent

    public data class Restored(override val appId: AppId) : SuperizerEvent

    public data class Background(override val appId: AppId?) : SuperizerEvent

    public data class Foreground(override val appId: AppId?) : SuperizerEvent

    public data class Active(override val appId: AppId) : SuperizerEvent

    public data class Closed(override val appId: AppId) : SuperizerEvent

    public data class Disposed(override val appId: AppId) : SuperizerEvent

    public data class LaunchFailed(
        override val appId: AppId?,
        val reason: String,
        val cause: Throwable? = null,
    ) : SuperizerEvent

    /** `source`: "qr" | "promo" | "deeplink" | "push" | "service-menu". Visible to the host only. */
    public data class Activated(override val appId: AppId, val source: String) : SuperizerEvent

    public data class Unlocked(override val appId: AppId) : SuperizerEvent

    public data class PushReceived(override val appId: AppId, val topic: String?) : SuperizerEvent

    /** UnsupportedSchema | UnknownApp | Locked | Disabled | LinkMismatch (13). */
    public data class PushDropped(override val appId: AppId?, val reason: String) : SuperizerEvent

    public data class DeepLinkUnmatched(override val appId: AppId, val path: String) : SuperizerEvent

    /** A route arrived, the open app vetoed its own close, and the user chose to stay (13 §5). */
    public data class RouteDiscarded(override val appId: AppId, val link: String) : SuperizerEvent

    /** An exception escaped the instance's coroutine scope (D31). The session is Failed, the host is not. */
    public data class Crashed(override val appId: AppId, val cause: Throwable) : SuperizerEvent

    /** Every trace of an app erased on purpose (D35) — storage, topics, snapshot, unlock. */
    public data class Reset(override val appId: AppId) : SuperizerEvent
}
