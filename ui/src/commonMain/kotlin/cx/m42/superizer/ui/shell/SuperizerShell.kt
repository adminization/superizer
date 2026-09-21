package cx.m42.superizer.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import cx.m42.superizer.Superizer
import cx.m42.superizer.activation.ActivationResult
import cx.m42.superizer.app.AppChrome
import cx.m42.superizer.app.AppId
import cx.m42.superizer.registry.AppSession
import cx.m42.superizer.registry.AppState
import cx.m42.superizer.runtime.LocalAppRuntime
import cx.m42.superizer.theme.AppTheme
import cx.m42.superizer.theme.SuperizerTheme
import cx.m42.superizer.ui.components.AppButton
import cx.m42.superizer.ui.components.AppScaffold
import cx.m42.superizer.ui.components.ButtonSize
import cx.m42.superizer.ui.components.ButtonVariant
import cx.m42.superizer.ui.components.IconButton
import cx.m42.superizer.ui.components.LocalDrawerDismiss
import cx.m42.superizer.ui.components.MenuEntry
import cx.m42.superizer.ui.components.QrIcon
import cx.m42.superizer.ui.components.SettingsIcon
import cx.m42.superizer.ui.i18n.LocalHostStrings
import cx.m42.superizer.ui.i18n.hostStrings
import cx.m42.superizer.ui.i18n.hostStringsFor
import cx.m42.superizer.ui.platform.SystemBackHandler
import cx.m42.superizer.ui.screens.ActivateScreen
import cx.m42.superizer.ui.screens.AllAppsScreen
import cx.m42.superizer.ui.screens.HostErrorScreen
import cx.m42.superizer.ui.screens.ServiceMenuScreen
import cx.m42.superizer.ui.screens.SettingsScreen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Where the user is, at the *host's* level.
 *
 * A sealed hierarchy rather than string routes, and the same shape Unitool's `Destination` had: a
 * destination carries what it was opened from, which keeps "back" answerable without a navigation
 * library. An open app is one destination among these — the whole of what the shell knows about
 * apps.
 */
public sealed interface ShellDestination {
    public data object Home : ShellDestination
    public data class App(val id: AppId) : ShellDestination
    public data class Settings(val from: ShellDestination) : ShellDestination
    public data class Activate(val from: ShellDestination) : ShellDestination
    public data object ServiceMenu : ShellDestination
}

private enum class NavDirection { Forward, Back }

/**
 * How wide the content may get (D36). A phone never notices; a desktop window and a browser tab
 * get a column down the middle instead of a two-tile grid stretched across 1400 px. An adaptive
 * layout is a later decision — this only fixes the maximum.
 */
private val MaxContentWidth = 480.dp

/**
 * The Super App itself: one source of truth for where the user is, the drawer that is the same
 * everywhere, and the container an app is drawn inside.
 *
 * Growing the *host* means adding a destination and a `when` branch here. Growing the *product*
 * means registering an app — and nothing in this file changes at all, which is §24.
 */
@Composable
public fun SuperizerShell(superizer: Superizer, modifier: Modifier = Modifier) {
    val langTag by superizer.settings.langTag.collectAsState()
    val unlocked by superizer.unlocked.collectAsState()
    val registered by superizer.registry.apps.collectAsState()
    val scope = rememberCoroutineScope()

    var destination by remember { mutableStateOf<ShellDestination>(ShellDestination.Home) }
    var navDirection by remember { mutableStateOf(NavDirection.Forward) }
    var vetoed by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
    // The app whose tile was long-pressed on Home, waiting for the answer to "hide it?" (D48).
    var hiding by remember { mutableStateOf<AppId?>(null) }

    fun go(dest: ShellDestination) {
        navDirection = NavDirection.Forward
        destination = dest
    }

    fun goBack(dest: ShellDestination) {
        navDirection = NavDirection.Back
        destination = dest
    }

    /**
     * Closes the open app, turning its veto (03) into the host's own dialog rather than into
     * something the app has to draw. [onClosed] runs only if the app actually went away.
     */
    suspend fun closeCurrent(onClosed: suspend () -> Unit) {
        if (superizer.handler.close()) onClosed() else vetoed = onClosed
    }

    suspend fun open(id: AppId) {
        closeCurrent {
            superizer.handler.launch(id)
            go(ShellDestination.App(id))
        }
    }

    // What a link means, wherever it came from — a notification tap, an intent, `?link=` in the
    // query (13 §5). Always through the activation service, so it is the same code path a QR code
    // takes, and the app on the other end cannot tell the difference (§12–13).
    LaunchedEffect(superizer) {
        superizer.route.route.collectLatest { pending ->
            if (pending == null) return@collectLatest
            val link = superizer.route.consume() ?: return@collectLatest
            val result = superizer.activation.fromDeepLink(link)
            if (result !is ActivationResult.Success) return@collectLatest
            closeCurrent {
                superizer.activation.apply(result.activation, source = "deeplink")
                    .onSuccess { go(ShellDestination.App(result.activation.appId)) }
            }
        }
    }

    // What an app asked for (04). The app has already been launched or closed by the time the
    // command arrives; all that is left is for the shell to be looking at the right thing.
    LaunchedEffect(superizer) {
        superizer.commands.collect { command ->
            when (command) {
                is cx.m42.superizer.ShellCommand.OpenApp -> go(ShellDestination.App(command.id))
                cx.m42.superizer.ShellCommand.OpenSettings -> go(ShellDestination.Settings(destination))
                cx.m42.superizer.ShellCommand.Close -> goBack(ShellDestination.Home)
            }
        }
    }

    // Priority at startup (13 §5): a link already waiting, then the session snapshot, then Home.
    // The other order would open yesterday's screen over the notification someone just tapped.
    LaunchedEffect(superizer) {
        if (superizer.route.route.value != null) return@LaunchedEffect
        superizer.handler.restoreLastSession()?.onSuccess { go(ShellDestination.App(it.app.id)) }
    }

    val strings = hostStringsFor(langTag)
    val here = destination
    val visible = registered.filter { !it.metadata.hidden || it.id in unlocked }
    // Only what an activation revealed can be put away again (D48): hiding an app that was never
    // hidden would be a way to lose the calculator with a slip of the thumb and no way back.
    val hideable = visible.filter { it.metadata.hidden }.map { it.id }.toSet()

    SuperizerTheme {
        CompositionLocalProvider(LocalHostStrings provides strings) {

            // Back means what the top bar's arrow means. Registered above the screens, so an open
            // drawer — whose handler is composed deeper — answers first (D12). Disabled on Home,
            // where back should leave the host, which is what the system does when nobody claims it.
            SystemBackHandler(enabled = here !is ShellDestination.Home) {
                when (here) {
                    is ShellDestination.Settings -> goBack(here.from)
                    is ShellDestination.Activate -> goBack(here.from)
                    ShellDestination.ServiceMenu -> goBack(ShellDestination.Home)
                    is ShellDestination.App -> scope.launch { closeCurrent { goBack(ShellDestination.Home) } }
                    ShellDestination.Home -> Unit
                }
            }

            val menu = buildList {
                add(
                    MenuEntry(
                        label = strings.allApps,
                        onClick = {
                            scope.launch { closeCurrent { goBack(ShellDestination.Home) } }
                        },
                        enabled = here !is ShellDestination.Home,
                    ),
                )
                visible.forEach { app ->
                    add(
                        MenuEntry(
                            label = app.metadata.title.resolve(langTag),
                            onClick = { scope.launch { open(app.id) } },
                            // The entry for where you already are is disabled, not dropped, so the
                            // menu reads the same from every screen.
                            enabled = (here as? ShellDestination.App)?.id != app.id,
                        ),
                    )
                }
            }

            val haptic = superizer.hapticTick()
            val footer: @Composable () -> Unit = {
                ShellFooter(
                    superizer = superizer,
                    onActivate = { go(ShellDestination.Activate(destination)) },
                    onSettings = { go(ShellDestination.Settings(destination)) },
                )
            }

            Box(modifier = modifier.fillMaxSize().background(AppTheme.menuBackground)) {
                // GitHub-app style screen changes: forward pushes the new screen in from the right
                // while the old one recedes a third of the way left; back is the mirror image. The
                // z-order flips with the direction, or a return would look like another push.
                AnimatedContent(
                    targetState = destination,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        val spec = tween<IntOffset>(durationMillis = 300, easing = FastOutSlowInEasing)
                        if (navDirection == NavDirection.Forward) {
                            (slideInHorizontally(spec) { it } togetherWith slideOutHorizontally(spec) { -it / 3 })
                                .apply { targetContentZIndex = 1f }
                        } else {
                            (slideInHorizontally(spec) { -it / 3 } togetherWith slideOutHorizontally(spec) { it })
                                .apply { targetContentZIndex = -1f }
                        }
                    },
                ) { where ->
                    when (where) {
                        ShellDestination.Home -> AppScaffold(
                            title = superizer.hostInfo.name,
                            menu = menu,
                            onHaptic = haptic,
                            footer = footer,
                        ) {
                            Capped {
                                AllAppsScreen(
                                    apps = visible,
                                    langTag = langTag,
                                    onOpen = { id -> scope.launch { open(id) } },
                                    hideable = hideable,
                                    onHide = { id -> hiding = id },
                                )
                            }
                        }

                        is ShellDestination.App -> AppContainer(
                            superizer = superizer,
                            id = where.id,
                            menu = menu,
                            langTag = langTag,
                            haptic = haptic,
                            footer = footer,
                            onBack = { scope.launch { closeCurrent { goBack(ShellDestination.Home) } } },
                            onRetry = { scope.launch { superizer.handler.launch(where.id, force = true) } },
                        )

                        is ShellDestination.Settings -> AppScaffold(
                            title = strings.settings,
                            menu = menu,
                            onBack = { goBack(where.from) },
                            onHaptic = haptic,
                            footer = footer,
                        ) {
                            Capped {
                                SettingsScreen(
                                    superizer = superizer,
                                    onOpenServiceMenu = { go(ShellDestination.ServiceMenu) },
                                )
                            }
                        }

                        is ShellDestination.Activate -> AppScaffold(
                            title = strings.activate,
                            menu = menu,
                            onBack = { goBack(where.from) },
                            onHaptic = haptic,
                            footer = footer,
                        ) {
                            Capped {
                                ActivateScreen(
                                    superizer = superizer,
                                    onActivated = { id -> go(ShellDestination.App(id)) },
                                    onHostCommand = { command ->
                                        when (command) {
                                            ActivationResult.Command.OpenServiceMenu ->
                                                go(ShellDestination.ServiceMenu)
                                        }
                                    },
                                )
                            }
                        }

                        ShellDestination.ServiceMenu -> AppScaffold(
                            title = strings.serviceMenu,
                            menu = menu,
                            onBack = { goBack(ShellDestination.Home) },
                            onHaptic = haptic,
                            footer = footer,
                        ) {
                            Capped {
                                ServiceMenuScreen(
                                    superizer = superizer,
                                    onLaunched = { id -> go(ShellDestination.App(id)) },
                                )
                            }
                        }
                    }
                }

                // The app said no (03). The host asks, because a modal is global UI and an app that
                // drew its own would be drawing over the host's frame.
                val ask = vetoed
                if (ask != null) {
                    HostDialog(
                        title = strings.closeDialogTitle,
                        body = strings.closeDialogBody,
                        confirmText = strings.closeDialogConfirm,
                        dismissText = strings.closeDialogStay,
                        tag = "shell:close-dialog",
                        onConfirm = {
                            vetoed = null
                            scope.launch {
                                superizer.handler.close(force = true)
                                ask()
                            }
                        },
                        onDismiss = {
                            vetoed = null
                            // A route the user refused is a route that is gone (13 §5). Discarding
                            // it emits `RouteDiscarded`, so the Service Menu shows what happened.
                            superizer.route.discard()
                        },
                    )
                }

                // D48. The host asks, for the same reason it asks about a veto: the tile the
                // question is about is behind this dialog, and the answer is not undoable by
                // tapping again — only an activation brings the app back.
                val hide = hiding
                if (hide != null) {
                    val name = registered.firstOrNull { it.id == hide }
                        ?.metadata?.title?.resolve(langTag) ?: hide.value
                    HostDialog(
                        title = strings.hideDialogTitle(name),
                        body = strings.hideDialogBody,
                        confirmText = strings.hideDialogConfirm,
                        dismissText = strings.hideDialogCancel,
                        tag = "home:confirm-hide",
                        onConfirm = {
                            hiding = null
                            scope.launch { superizer.hide(hide) }
                        },
                        onDismiss = { hiding = null },
                    )
                }
            }
        }
    }
}

/** D36: every destination's content sits in a column of at most [MaxContentWidth], centred. */
@Composable
private fun Capped(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxSize()) { content() }
    }
}

/**
 * The app container: the host's frame, with the app's own screen inside it.
 *
 * The test tag goes on *this* node rather than inside the app (D39), so every app — including one
 * the host has never seen — has a root that a test and the web bridge can find.
 */
@Composable
private fun AppContainer(
    superizer: Superizer,
    id: AppId,
    menu: List<MenuEntry>,
    langTag: String,
    haptic: () -> Unit,
    footer: @Composable () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val session by superizer.handler.current.collectAsState()
    val states by superizer.handler.states.collectAsState()
    val open: AppSession? = session?.takeIf { it.app.id == id }
    val chrome: AppChrome? = open?.let { it.instance.chrome.collectAsState().value }

    val fallbackTitle = superizer.registry.get(id)?.metadata?.title?.resolve(langTag) ?: id.value

    AppScaffold(
        title = chrome?.title ?: fallbackTitle,
        subtitle = chrome?.subtitle,
        menu = menu,
        onBack = onBack,
        onHaptic = haptic,
        footer = footer,
    ) {
        Capped {
            when {
                // D31: a failed launch is the host's screen to draw. The app is not involved —
                // it may not even have been constructed.
                states[id] == AppState.Failed -> HostErrorScreen(onRetry = onRetry, onBack = onBack)

                // Launching. A blank frame rather than a spinner: `onLaunch` is budgeted at 200 ms
                // (12 §4.7), and a spinner that flashes for one frame is worse than none.
                open == null -> Box(Modifier.fillMaxSize())

                else -> CompositionLocalProvider(LocalAppRuntime provides open.runtime) {
                    Box(modifier = Modifier.fillMaxSize().testTag("${id.value}:root")) {
                        open.instance.Content()
                    }
                }
            }
        }
    }
}

@Composable
private fun ShellFooter(superizer: Superizer, onActivate: () -> Unit, onSettings: () -> Unit) {
    val tokens = AppTheme
    val dismiss = LocalDrawerDismiss.current
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { dismiss(); onSettings() }, label = hostStrings.settings) { tint ->
                SettingsIcon(tint)
            }
            IconButton(onClick = { dismiss(); onActivate() }, label = hostStrings.activate) { tint ->
                QrIcon(tint)
            }
        }
        // Which build this is — the first thing to ask for when a bug report comes in.
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(
                text = hostStrings.version(superizer.hostInfo.version),
                style = tokens.label,
                color = tokens.muted,
            )
            Text(
                text = superizer.hostInfo.build,
                style = tokens.label,
                color = tokens.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The host's one modal: a question over a scrim, with the destructive answer on the right.
 *
 * Shared by the close veto and by hiding an app (D48) rather than written twice — two dialogs that
 * drifted apart would be two answers to "what does the host look like when it asks something".
 */
@Composable
private fun HostDialog(
    title: String,
    body: String,
    confirmText: String,
    dismissText: String,
    tag: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = AppTheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.foreground.copy(alpha = 0.35f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            // On the scrim rather than on the panel: the scrim is clickable, so it merges its
            // children's semantics into one node, and a tag below it would only exist in the
            // unmerged tree — findable by a test that knew the trick and by nothing else.
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .padding(24.dp)
                .clip(RoundedCornerShape(tokens.radius))
                .background(tokens.background)
                .padding(20.dp),
        ) {
            Text(text = title, style = tokens.header, color = tokens.foreground)
            Spacer(Modifier.height(8.dp))
            Text(text = body, style = tokens.body, color = tokens.muted)
            Spacer(Modifier.height(16.dp))
            Row {
                AppButton(
                    text = dismissText,
                    onClick = onDismiss,
                    variant = ButtonVariant.Outline,
                    size = ButtonSize.Default,
                )
                Spacer(Modifier.width(12.dp))
                AppButton(
                    text = confirmText,
                    onClick = onConfirm,
                    variant = ButtonVariant.Destructive,
                    size = ButtonSize.Default,
                )
            }
        }
    }
}

/**
 * The host's own haptic, handed to the frame so the drawer's settle feels like every key press.
 *
 * It goes through the same [cx.m42.superizer.runtime.HapticsService] an app gets, which is what
 * makes the Settings switch one decision made in one place (D38).
 */
@Composable
private fun Superizer.hapticTick(): () -> Unit {
    val haptics = this.haptics
    return remember(haptics) { { haptics.tick() } }
}
