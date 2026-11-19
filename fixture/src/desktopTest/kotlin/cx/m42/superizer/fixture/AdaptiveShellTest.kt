package cx.m42.superizer.fixture

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ClipboardList
import com.composables.icons.lucide.LayoutDashboard
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Receipt
import com.composables.icons.lucide.Wallet
import cx.m42.superizer.ContextPort
import cx.m42.superizer.HostContext
import cx.m42.superizer.Superizer
import cx.m42.superizer.SuperizerContract
import cx.m42.superizer.app.AppConfigSpec
import cx.m42.superizer.app.AppIcon
import cx.m42.superizer.app.AppId
import cx.m42.superizer.app.AppInstance
import cx.m42.superizer.app.AppLayout
import cx.m42.superizer.app.AppManifest
import cx.m42.superizer.app.AppMetadata
import cx.m42.superizer.app.AppSetupContext
import cx.m42.superizer.app.AppWidth
import cx.m42.superizer.app.SuperizerApp
import cx.m42.superizer.app.localized
import cx.m42.superizer.host.build
import cx.m42.superizer.host.storage.PrefsStorage
import cx.m42.superizer.kit.AppSections
import cx.m42.superizer.kit.Button
import cx.m42.superizer.kit.Grid
import cx.m42.superizer.kit.Page
import cx.m42.superizer.kit.PageHeader
import cx.m42.superizer.kit.Panel
import cx.m42.superizer.kit.SectionItem
import cx.m42.superizer.kit.Stat
import cx.m42.superizer.kit.StatusBadge
import cx.m42.superizer.kit.Tone
import cx.m42.superizer.kit.byWindow
import cx.m42.superizer.kit.kitTypography
import cx.m42.superizer.runtime.HostInfo
import cx.m42.superizer.runtime.InstanceRuntime
import cx.m42.superizer.runtime.Platform
import cx.m42.superizer.ui.shell.ShellLayout
import cx.m42.superizer.ui.shell.SuperizerShell
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * The adaptive shell (0.11): the drawer on a phone, a rail on a tablet, a sidebar on a desktop — the
 * same menu each time — and an app that says it can be wide gets the width and heads its own page.
 * A shell left at [ShellLayout.Drawer] is the shell it always was, at any width.
 */
@OptIn(ExperimentalTestApi::class)
class AdaptiveShellTest {

    @BeforeTest
    fun isolateStorage() {
        PrefsStorage.useDirectory(Files.createTempDirectory("superizer-adaptive-test").toFile())
    }

    private class Places : ContextPort {
        private val all = listOf(HostContext("north", "Pizza North", "pizza-north.example"), HostContext("cloud", "Cloud", "anna@example"))
        override val contexts: StateFlow<List<HostContext>> = MutableStateFlow(all)
        override val current: StateFlow<HostContext?> = MutableStateFlow(all[0])
        override fun switchTo(id: String) {}
        override fun title(langTag: String) = "Servers"
    }

    private fun host(): Superizer = Superizer.build(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)) {
        host(HostInfo("Adaptive", "0.11.0", "dev", Platform.Desktop, SuperizerContract.VERSION, debug = true))
        register(OrdersApp())
        register(ProbeApp())
        home("orders", "probe")
        contexts(Places())
    }.also {
        it.settings.chooseLanguage("en")
        it.settings.chooseAccent("violet")
    }

    private fun ComposeUiTest.show(superizer: Superizer, width: Dp, height: Dp, layout: ShellLayout = ShellLayout.Adaptive) {
        setContent {
            Box(Modifier.size(width, height).testTag("window")) {
                SuperizerShell(superizer, Modifier, swipeBack = false, layout = layout, typography = kitTypography())
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.shoot(name: String) {
        val directory = java.io.File("build/screenshots/adaptive").apply { mkdirs() }
        javax.imageio.ImageIO.write(onNodeWithTag("window").captureToImage().toAwtImage(), "png", java.io.File(directory, "$name.png"))
    }

    @Test
    fun aDrawerShellStaysADrawerInAWideWindow() = runComposeUiTest {
        show(host(), 1440.dp, 900.dp, ShellLayout.Drawer)
        onNodeWithTag("shell:sidebar").assertDoesNotExist()
        onNodeWithTag("shell:rail").assertDoesNotExist()
        shoot("drawer-1440")
    }

    @Test
    fun onAPhoneTheAdaptiveShellIsTheDrawer() = runComposeUiTest {
        val superizer = host()
        show(superizer, 390.dp, 844.dp)
        onNodeWithTag("shell:sidebar").assertDoesNotExist()
        onNodeWithTag("shell:rail").assertDoesNotExist()
        shoot("compact-home")

        // The drawer lists the same items as the sidebar — with the app's mark — and goes where they go.
        onNodeWithContentDescription("Open menu").performClick()
        waitForIdle()
        onNodeWithTag("shell:nav-orders").assertExists()
        onNodeWithText("8").assertExists()
        shoot("compact-drawer")
        onNodeWithTag("shell:nav-orders").performClick()
        waitForIdle()
        onNodeWithTag("orders:page").assertExists()
    }

    @Test
    fun aTabletGetsTheRailAndADesktopTheSidebarWithTheAppsMark() = runComposeUiTest {
        val superizer = host()
        var width by mutableStateOf(820.dp)
        setContent {
            Box(Modifier.size(width, 1000.dp).testTag("window")) {
                SuperizerShell(superizer, Modifier, swipeBack = false, layout = ShellLayout.Adaptive, typography = kitTypography())
            }
        }
        waitForIdle()
        onNodeWithTag("shell:rail").assertExists()
        shoot("medium-home")

        width = 1440.dp
        waitForIdle()
        onNodeWithTag("shell:sidebar").assertExists()
        onNodeWithText("8").assertExists()
        shoot("expanded-home")
    }

    @Test
    fun aWideAppHeadsItsOwnPageOnADesktopAndACappedOneKeepsTheBar() = runComposeUiTest {
        val superizer = host()
        show(superizer, 1440.dp, 900.dp)
        onNodeWithTag("shell:nav-orders").performClick()
        waitForIdle()
        onNodeWithTag("orders:page").assertExists()
        onNodeWithTag("shell:back").assertDoesNotExist()
        shoot("expanded-orders")

        onNodeWithTag("shell:nav-probe").performClick()
        waitForIdle()
        onNodeWithTag("probe:label").assertExists()
        shoot("expanded-probe")
    }

    @Test
    fun theAppsSectionsAreABottomBarOnAPhoneAndTabsOnATablet() = runComposeUiTest {
        val superizer = host()
        var width by mutableStateOf(390.dp)
        setContent {
            Box(Modifier.size(width, 844.dp).testTag("window")) {
                SuperizerShell(superizer, Modifier, swipeBack = false, layout = ShellLayout.Adaptive, typography = kitTypography())
            }
        }
        waitForIdle()
        width = 820.dp
        waitForIdle()
        onNodeWithTag("shell:nav-orders").performClick()
        waitForIdle()
        onNodeWithTag("sections:tabs").assertExists()
        shoot("medium-orders")

        width = 390.dp
        waitForIdle()
        onNodeWithTag("sections:bar").assertExists()
        shoot("compact-orders")
    }
}

@Serializable
class OrdersConfig

/** A wide app with sections and a mark in the menu — the shape RestoApp's apps take. */
class OrdersApp : SuperizerApp<OrdersConfig>() {
    override val manifest = AppManifest(
        id = AppId("orders"),
        version = "1.0.0",
        metadata = AppMetadata(title = localized("en" to "Orders"), icon = AppIcon.Letter('O'), category = "Restaurant"),
    )
    override val configSpec: AppConfigSpec<OrdersConfig> = AppConfigSpec(OrdersConfig.serializer(), OrdersConfig())

    override fun setup(ctx: AppSetupContext) {
        ctx.badge(MutableStateFlow("8"))
    }

    override fun launch(runtime: InstanceRuntime, config: OrdersConfig): AppInstance = OrdersInstance()
}

private class OrdersInstance : AppInstance() {
    override val layout: StateFlow<AppLayout> = MutableStateFlow(AppLayout(width = AppWidth.Adaptive))

    @Composable
    override fun Content() {
        var section by remember { mutableStateOf("board") }
        AppSections(
            items = listOf(
                SectionItem("summary", "Summary", Lucide.LayoutDashboard),
                SectionItem("board", "Board", Lucide.ClipboardList, badge = "8"),
                SectionItem("receipts", "Receipts", Lucide.Receipt),
                SectionItem("money", "Money", Lucide.Wallet),
            ),
            selected = section,
            onSelect = { section = it },
        ) {
            Page(modifier = Modifier.testTag("orders:page")) {
                PageHeader(
                    "Orders",
                    "Lenina 5 · 14 today, 9 in progress",
                    badge = { StatusBadge("Open", Tone.Success) },
                    actions = { Button("New order", {}, leadingIcon = Lucide.Plus) },
                )
                Grid(byWindow(2, 4, 4)) {
                    Stat("Revenue today", "48 320 ₽", hint = "+12% on last week")
                    Stat("Orders", "37", hint = "9 in progress")
                    Stat("Average check", "1 306 ₽")
                    Stat("Stop list", "3 dishes", tone = Tone.Warn, hint = "4 running low")
                }
                Panel(title = "Kitchen now") { StatusBadge("Cooking · 3", Tone.Warn) }
            }
        }
    }
}
