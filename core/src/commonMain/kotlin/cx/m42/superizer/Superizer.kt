package cx.m42.superizer

import cx.m42.superizer.activation.Activation
import cx.m42.superizer.activation.ActivationResult
import cx.m42.superizer.app.AppId
import cx.m42.superizer.event.SuperizerEvent
import cx.m42.superizer.registry.AppHandler
import cx.m42.superizer.registry.AppRegistry
import cx.m42.superizer.registry.AppSession
import cx.m42.superizer.runtime.HapticsService
import cx.m42.superizer.runtime.HostInfo
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Everything the host's own screens need from the host, as an interface in `core`.
 *
 * It is here, and not in `host`, for one reason with consequences everywhere: the shell and the
 * host screens live in `superizer:ui`, apps depend on `ui`, and an app must never be able to reach
 * `host` (02, §19). Declaring the *ports* next to the contract and implementing them in `host`
 * keeps that edge out of the graph — the build checks it (D21), so it cannot come back by accident.
 *
 * Apps do not see this type either. They see [cx.m42.superizer.runtime.AppRuntime] and nothing else.
 */
public interface Superizer {
    public val hostInfo: HostInfo
    public val registry: AppRegistry
    public val handler: AppHandler
    public val events: SharedFlow<SuperizerEvent>

    /** Hidden apps this device has unlocked (D8). All Apps and the drawer filter on it. */
    public val unlocked: StateFlow<Set<AppId>>

    /**
     * Takes an unlocked hidden app back out of [unlocked] (D48).
     *
     * On [Superizer] rather than on [DiagnosticsPort], because hiding is the one half of D8 an
     * ordinary user is allowed to do: what an activation revealed, a long press on its tile puts
     * away again. Revealing stays where it was — behind a QR code, a promo code, or the Service
     * Menu. The app's stored data is untouched; this is not [DiagnosticsPort.reset].
     */
    public suspend fun hide(id: AppId)

    /**
     * The host's own haptic. Here rather than only on [cx.m42.superizer.runtime.AppRuntime] because
     * the drawer settling is a host gesture and it has to feel like every other tap (D38).
     */
    public val haptics: HapticsService

    public val settings: HostSettingsPort
    public val activation: ActivationPort
    public val route: RoutePort
    public val diagnostics: DiagnosticsPort

    /**
     * What an app asked the host to do about navigation (04). The shell collects these and moves;
     * the app never touches the shell's state, which is the rule in §19 made structural.
     */
    public val commands: SharedFlow<ShellCommand>

    /**
     * Empty, and deliberately so: `host` hangs `Superizer.build { … }` off it as an extension, so a
     * host reads `Superizer.build` while `ui` still only sees the interface.
     */
    public companion object
}

/**
 * The three things `NavigationService` can ask the shell for.
 *
 * Deliberately not "navigate to this screen": an app may open another app, go to Settings, or
 * close itself, and nothing else. There is no route, no back stack and no way to reach a host
 * screen that is not on this list (§19).
 */
public sealed interface ShellCommand {
    public data class OpenApp(val id: AppId) : ShellCommand
    public data object OpenSettings : ShellCommand
    public data object Close : ShellCommand
}

/** One language the host speaks, as the Settings picker needs it. */
public data class LanguageOption(
    val tag: String,
    /** How the language names itself: a picker written in the language you are trying to leave is useless. */
    val nativeName: String,
)

/** The handful of decisions that belong to the host rather than to any app. */
public interface HostSettingsPort {
    public val langTag: StateFlow<String>
    public val languages: List<LanguageOption>
    public fun chooseLanguage(tag: String)
    public val haptics: StateFlow<Boolean>
    public fun setHaptics(enabled: Boolean)
}

/**
 * The four doors into the same room (06): a QR payload, a promo code, a deep link, and applying
 * whatever they resolved to. The Activate screen and the shell's cold start both use this.
 */
public interface ActivationPort {
    public suspend fun fromQr(text: String): ActivationResult
    public suspend fun fromPromo(code: String): ActivationResult
    public suspend fun fromDeepLink(url: String): ActivationResult

    /** Unlock if hidden, emit `Activated(source)`, launch. */
    public suspend fun apply(activation: Activation, source: String): Result<AppSession>
}

/**
 * A link that arrived before anything could handle it — a notification tap into a dead process, an
 * intent, `?link=` in the browser's query (13). Consume-once, because a route is an instruction and
 * replaying it would reopen the app every time the shell recomposed.
 */
public interface RoutePort {
    public val route: StateFlow<String?>
    public fun deliver(link: String)
    public fun consume(): String?
    public fun discard()
}

/** What the Service Menu shows and does. Nothing here is reachable from a release build by gesture (D34). */
public interface DiagnosticsPort {
    /** The host's ring buffer, newest last. */
    public val log: StateFlow<List<String>>

    /** The last hundred events, newest first — the same flow the bench app renders. */
    public val recentEvents: StateFlow<List<SuperizerEvent>>

    /** D35: erase one app's namespace, its topics, its snapshot and its unlock. Confirmed, and separate from disable. */
    public suspend fun reset(id: AppId)

    /**
     * Reveals a hidden app with no activation at all — the one power this port has over D8. The
     * way back is [Superizer.hide], which is not a diagnostic and lives with the rest of the host.
     */
    public suspend fun unlock(id: AppId)

    /** Feeds a raw push payload through the whole router, so the chain is testable without Firebase (13 §7). */
    public suspend fun simulatePush(payload: Map<String, String>)
}
