package cx.m42.superizer.app

import androidx.compose.runtime.Composable
import cx.m42.superizer.event.SuperizerEvent
import cx.m42.superizer.runtime.AppRuntime
import cx.m42.superizer.runtime.InstanceRuntime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject

/**
 * The registered thing: one per app id, created by the host at startup, alive for the process.
 * Holds no screen state — that belongs to the [AppInstance] a launch creates (D3).
 *
 * This mirrors Adminizer's `AbstractAdminizerApp` — `id`, `version`, `setup(ctx)` — and adds
 * [launch], because here an app is something you *open*, not a set of routes you mount.
 *
 * An app never accepts, stores or depends on the host. Its whole view of the platform is the
 * [AppRuntime] it is handed; that rule is Adminizer's "App Boundary", kept verbatim, and it is
 * what makes the same app run standalone, under Unitool, or under a host nobody has written yet.
 */
public abstract class SuperizerApp<C : Any> {

    /** The static half of the app (D47): validated before [setup], shown before [launch]. */
    public abstract val manifest: AppManifest

    public abstract val configSpec: AppConfigSpec<C>

    public val id: AppId get() = manifest.id

    public val version: String get() = manifest.version

    public val metadata: AppMetadata get() = manifest.metadata

    /**
     * Registration-time hook, once per enable. Wires the *behaviour* behind the manifest: a
     * handler per declared deep link, a settings section, event listeners, a push subscription.
     *
     * Everything registered here is undone by the handler on disable, in reverse order — never by
     * the app. Registering a deep link the manifest does not declare is an error the registry
     * reports as `SetupMismatch`; the manifest is the declaration, this is only the wiring.
     *
     * `ctx.runtime` is the *app-level* runtime (D15): it outlives any screen, which is what push
     * topics and background refresh need.
     */
    public open fun setup(ctx: AppSetupContext) {}

    /**
     * Creates the live instance: called on every open, with an instance runtime and the config
     * already typed.
     *
     * One call rather than the specification's `initialize` / `configure` / `launch`, because
     * three steps over `lateinit` fields are three states the compiler cannot check. The steps
     * themselves survive as the *handler's* order of operations (05) and are visible as events.
     *
     * Must be cheap. Anything slow belongs in [AppInstance.onLaunch].
     */
    public abstract fun launch(runtime: InstanceRuntime, config: C): AppInstance
}

/**
 * One open app: its state, its screen, its end.
 *
 * Created by [SuperizerApp.launch], driven by the handler, drawn inside the host's frame. There is
 * at most one per app id at a time (D4).
 */
public abstract class AppInstance {

    /** Once, before the first frame. Start flows, read what you need out of `runtime.storage`. */
    public open suspend fun onLaunch() {}

    /**
     * A snapshot of what the screen would lose if the process died now (D16) — the calculator's
     * entry, the converter's amount. Null means nothing worth keeping.
     *
     * The handler takes one when the app closes and when the host goes to background, stores it,
     * and hands it back through [restore] on the next launch. Anything that should outlive the
     * *session*, rather than the process, belongs in `runtime.storage` instead.
     */
    public open fun saveState(): JsonObject? = null

    /**
     * After [onLaunch] and before the first frame, when a snapshot exists. Tolerate old shapes:
     * the snapshot may predate the update that is reading it.
     */
    public open fun restore(state: JsonObject) {}

    /** The host went to background / came back (D17). Stop polling; resume it. */
    public open fun onBackground() {}

    public open fun onForeground() {}

    /**
     * The whole of the app's UI. The host draws the frame — top bar, drawer, back — and this is
     * what goes inside it. An app that puts its own top bar here is fighting the shell for
     * global navigation, which is §14's one rule.
     */
    @Composable
    public abstract fun Content()

    /**
     * What the host's top bar should read for this app right now. Observable, so an app that
     * navigates inside itself can retitle the bar and claim back without the host knowing what
     * screen it is on.
     */
    public open val chrome: StateFlow<AppChrome> get() = DefaultChrome

    /**
     * The host is about to close this app — back at the root, another app opened, the process
     * going away. Return false to veto (a form with unsaved edits) and the host asks the user.
     */
    public open suspend fun onCloseRequested(): Boolean = true

    /** Save what must survive. After this the instance is never composed again. */
    public open fun onClose() {}

    /** Release everything; `runtime.scope` is cancelled by the handler right after. */
    public open fun dispose() {}

    private companion object {
        val DefaultChrome: StateFlow<AppChrome> = MutableStateFlow(AppChrome())
    }
}

/** What an app tells the host's frame about itself. */
public data class AppChrome(
    /** Null → the host uses `metadata.title`. */
    val title: String? = null,
    val subtitle: String? = null,
    /** True while the app has somewhere to go back to inside itself; the host then routes back to it. */
    val canGoBack: Boolean = false,
)

/**
 * Given to [SuperizerApp.setup]. Everything declared through it is owned by the handler and torn
 * down in reverse order on disable — Adminizer's disposers, in Kotlin.
 */
public interface AppSetupContext {

    /** App-level runtime (D15): alive until disable, with no navigation — there is no screen yet. */
    public val runtime: AppRuntime

    /** A block on the host's Settings screen, under this app's own title. */
    public fun settingsSection(section: @Composable (runtime: AppRuntime) -> Unit)

    /** `unitool://app/<id>/<path>` → opens this app with the config [toConfig] builds from the query. */
    public fun deepLink(path: String, toConfig: (params: Map<String, String>) -> AppConfig)

    /** Host events, delivered on the app-level scope whether or not a screen is open. */
    public fun listener(handler: suspend (event: SuperizerEvent) -> Unit)
}

/** Undoes one thing [SuperizerApp.setup] registered. Collected by the handler, run in reverse. */
public fun interface Disposable {
    public fun dispose()
}
