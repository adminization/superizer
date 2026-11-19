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
     * How this app wants to be laid out by an adaptive host: as wide as the window allows, and where
     * its "back" leads. Observable for the same reason [chrome] is. A host without an adaptive shell
     * ignores it; an app that does not override it is laid out as before — in a phone's column.
     */
    public open val layout: StateFlow<AppLayout> get() = DefaultLayout

    /**
     * The host is about to close this app — back at the root, another app opened, the process
     * going away. Return false to veto (a form with unsaved edits) and the host asks the user.
     */
    public open suspend fun onCloseRequested(): Boolean = true

    /** True when the host's question after a veto may offer to save. Read at the moment it asks. */
    public open val canSaveBeforeClose: Boolean get() = false

    /**
     * "Save and leave" in the host's question. Returns once it is known: true and the host closes
     * the app, false — refused, or invalid — and the person stays on the screen, which says why.
     */
    public open suspend fun saveBeforeClose(): Boolean = false

    /** Save what must survive. After this the instance is never composed again. */
    public open fun onClose() {}

    /** Release everything; `runtime.scope` is cancelled by the handler right after. */
    public open fun dispose() {}

    private companion object {
        val DefaultChrome: StateFlow<AppChrome> = MutableStateFlow(AppChrome())
        val DefaultLayout: StateFlow<AppLayout> = MutableStateFlow(AppLayout())
    }
}

/** What an app tells the host's frame about itself. */
public data class AppChrome(
    /** Null → the host uses `metadata.title`. */
    val title: String? = null,
    val subtitle: String? = null,
    /** True while the app has somewhere to go back to inside itself; the host then routes back to it. */
    val canGoBack: Boolean = false,
    /**
     * True: the whole screen is the app's, edge to edge — a feed of cards, a player, a camera. No top
     * bar, no drawer, no width cap and no padding for the safe area, so the app draws its own way out
     * (`navigation.close`) and clears the status bar itself. A host that predates this draws its
     * ordinary frame around the app.
     */
    val fullscreen: Boolean = false,
)

/** How wide an app may be drawn. */
public enum class AppWidth {
    /** A phone's column, centred: the app was written for a phone and has not said otherwise. */
    Capped,

    /**
     * The whole width the host gives it. The app lays itself out by the window class (`ui-kit`'s
     * `LocalWindowClass`) and keeps its text readable with a page width of its own.
     */
    Adaptive,
}

/**
 * What an app tells an adaptive host about its layout — kept apart from [AppChrome] so that type
 * never changes.
 */
public data class AppLayout(
    val width: AppWidth = AppWidth.Capped,
    /**
     * Where the app's own "back" leads, in words — «Orders» while a record of an order is open. The
     * host writes it beside its arrow, or as a breadcrumb where it draws no bar. Null: no words.
     */
    val upTitle: String? = null,
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

    /**
     * A short mark beside the app in the host's menu — «8» new orders — that stays while the app is
     * closed. Null or empty: no mark. Collected until the app is disabled. A secret app gets none:
     * a number beside it would tell that it is there. A host that predates badges ignores it.
     */
    public fun badge(value: StateFlow<String?>) {}
}

/** Undoes one thing [SuperizerApp.setup] registered. Collected by the handler, run in reverse. */
public fun interface Disposable {
    public fun dispose()
}
