package cx.m42.superizer.activation

import cx.m42.superizer.app.AppConfig
import cx.m42.superizer.app.AppId

/**
 * What every activation path boils down to: which app, with what config.
 *
 * The *model* lives in `core` so an app's tests can name one; the parsers and the service that
 * applies them live in `host`, and no app imports them. An app receives a config and never learns
 * whether it came from a QR code, a promo code, a notification or the Service Menu — §12–13 in one
 * sentence.
 */
public data class Activation(
    val appId: AppId,
    val config: AppConfig = AppConfig.Empty,
    /** Whether applying this activation also reveals a hidden app (D8). */
    val unlock: Boolean = true,
)

public sealed interface ActivationResult {

    public data class Success(val activation: Activation) : ActivationResult

    /**
     * A code that drives the host itself, not an app (D20). The shell matches these; no app ever
     * sees one. Reserving an app id like `service-menu` instead would be one magic name, and one
     * magic name always becomes several.
     */
    public data class HostCommand(val command: Command) : ActivationResult

    public data class Rejected(val reason: Reason) : ActivationResult

    public enum class Reason { Malformed, UnsupportedSchema, UnknownApp, UnknownCode, Expired }

    public enum class Command { OpenServiceMenu }
}
