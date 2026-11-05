package cx.m42.superizer.fixture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.geometry.Offset
import com.composeunstyled.Text
import cx.m42.superizer.Superizer
import cx.m42.superizer.SuperizerContract
import cx.m42.superizer.app.AppConfigSpec
import cx.m42.superizer.app.AppIcon
import cx.m42.superizer.app.AppId
import cx.m42.superizer.app.AppInstance
import cx.m42.superizer.app.AppManifest
import cx.m42.superizer.app.AppMetadata
import cx.m42.superizer.app.SuperizerApp
import cx.m42.superizer.app.localized
import cx.m42.superizer.host.build
import cx.m42.superizer.host.storage.PrefsStorage
import cx.m42.superizer.runtime.HostInfo
import cx.m42.superizer.runtime.InstanceRuntime
import cx.m42.superizer.runtime.Platform
import cx.m42.superizer.ui.platform.ScreenSteps
import cx.m42.superizer.ui.platform.SystemBackHandler
import cx.m42.superizer.ui.shell.SuperizerShell
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Back unwinds inside an app before it leaves it (09, "An app that navigates inside itself"): the
 * arrow of the top bar and the swipe from the right edge go through the same claims the platform's
 * own back goes through, on a platform that has none.
 */
@OptIn(ExperimentalTestApi::class)
class BackChainTest {

    @BeforeTest
    fun isolateStorage() {
        PrefsStorage.useDirectory(Files.createTempDirectory("superizer-back-test").toFile())
    }

    /** Depth 2 to begin with, and a sheet over it once something opens one. */
    private class Pages : AppInstance() {
        var depth by mutableIntStateOf(2)
        var sheet by mutableStateOf(false)

        @Composable
        override fun Content() {
            SystemBackHandler(enabled = depth > 0) { depth-- }
            ScreenSteps(depth)
            Text("depth $depth", modifier = Modifier.testTag("pages:depth"))
            if (sheet) {
                SystemBackHandler(enabled = true) { sheet = false }
                Text("sheet", modifier = Modifier.testTag("pages:sheet"))
            }
        }
    }

    /** Holds an edit until it is saved, and saving is refused while [refuses]. */
    private class Edits : AppInstance() {
        var refuses = false
        var saves = 0

        @Composable
        override fun Content() {
            Text("edits", modifier = Modifier.testTag("edits:root"))
        }

        override suspend fun onCloseRequested() = saves > 0
        override val canSaveBeforeClose get() = true
        override suspend fun saveBeforeClose(): Boolean {
            if (refuses) return false
            saves++
            return true
        }
    }

    private class PagesApp(val pages: AppInstance) : SuperizerApp<ProbeConfig>() {
        override val manifest = AppManifest(
            id = AppId("pages"),
            version = "1.0.0",
            metadata = AppMetadata(
                title = localized("en" to "Pages"),
                icon = AppIcon.Letter('P'),
                category = "Tools",
            ),
        )
        override val configSpec = AppConfigSpec(ProbeConfig.serializer(), ProbeConfig())
        override fun launch(runtime: InstanceRuntime, config: ProbeConfig): AppInstance = pages
    }

    private fun host(pages: AppInstance): Superizer =
        Superizer.build(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)) {
            host(HostInfo("Back", "0.1.0", "dev", Platform.Desktop, SuperizerContract.VERSION, debug = true))
            register(PagesApp(pages))
            home("pages")
        }.also { it.settings.chooseLanguage("en") }

    private fun ComposeUiTest.open() {
        onNodeWithTag("home:tile-pages").performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.arrow() {
        onNodeWithContentDescription("Back").performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.swipeFromTheRightEdge() {
        onRoot().performTouchInput { swipe(Offset(right - 2f, centerY), Offset(left + 2f, centerY), durationMillis = 300) }
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
    }

    private fun ComposeUiTest.depthIs(depth: Int) =
        onNodeWithTag("pages:depth").assertTextEquals("depth $depth")

    @Test
    fun theArrowWalksTheAppsScreensAndLeavesFromTheFirst() = runComposeUiTest {
        setContent { SuperizerShell(host(Pages())) }
        waitForIdle()
        open()
        depthIs(2)
        arrow()
        depthIs(1)
        arrow()
        depthIs(0)
        arrow()
        onNodeWithTag("pages:depth").assertDoesNotExist()
        onNodeWithTag("home:tile-pages").assertExists()
    }

    private fun ComposeUiTest.pressBackOnAVeto() {
        arrow()
        onNodeWithTag("shell:close-dialog").assertExists()
    }

    @Test
    fun theDialogAfterAVetoSavesThenLeaves() = runComposeUiTest {
        val edits = Edits()
        setContent { SuperizerShell(host(edits)) }
        waitForIdle()
        open()
        pressBackOnAVeto()
        onNodeWithTag("shell:close-dialog:save").performClick()
        waitForIdle()
        assertEquals(1, edits.saves)
        onNodeWithTag("home:tile-pages").assertExists()
    }

    @Test
    fun aSaveThatFailsLeavesThePersonOnTheScreen() = runComposeUiTest {
        val edits = Edits().apply { refuses = true }
        setContent { SuperizerShell(host(edits)) }
        waitForIdle()
        open()
        pressBackOnAVeto()
        onNodeWithTag("shell:close-dialog:save").performClick()
        waitForIdle()
        onNodeWithTag("edits:root").assertExists()
        onNodeWithTag("shell:close-dialog").assertDoesNotExist()
    }

    @Test
    fun theDeepestClaimAnswersFirst() = runComposeUiTest {
        val pages = Pages()
        pages.sheet = true
        setContent { SuperizerShell(host(pages)) }
        waitForIdle()
        open()
        arrow()
        onNodeWithTag("pages:sheet").assertDoesNotExist()
        depthIs(2)
    }

    @Test
    fun theSwipeStepsBackAndDoesNotLeaveTheApp() = runComposeUiTest {
        setContent { SuperizerShell(host(Pages()), swipeBack = true) }
        waitForIdle()
        open()
        swipeFromTheRightEdge()
        depthIs(1)
        swipeFromTheRightEdge()
        depthIs(0)
        swipeFromTheRightEdge()
        depthIs(0)
    }

    @Test
    fun theSwipeUncoversAPictureOfTheScreenBelowOnlyOnceThereIsOne() = runComposeUiTest {
        val pages = Pages().apply { depth = 0 }
        setContent { SuperizerShell(host(pages), swipeBack = true) }
        waitForIdle()
        open()
        // Opened at the first screen, then stepped forward: that step is what is pictured.
        pages.depth = 1
        waitForIdle()
        onRoot().performTouchInput {
            down(Offset(right - 2f, centerY))
            moveBy(Offset(-200f, 0f))
        }
        waitForIdle()
        onNodeWithTag("shell:back-preview").assertExists()
        onRoot().performTouchInput { up() }
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        onNodeWithTag("shell:back-preview").assertDoesNotExist()
    }

    @Test
    fun aHostThatDidNotAskHasNoSwipe() = runComposeUiTest {
        setContent { SuperizerShell(host(Pages())) }
        waitForIdle()
        open()
        swipeFromTheRightEdge()
        depthIs(2)
    }

    @Test
    fun aSwipeFromTheMiddleOfTheScreenIsNotOne() = runComposeUiTest {
        setContent { SuperizerShell(host(Pages()), swipeBack = true) }
        waitForIdle()
        open()
        onRoot().performTouchInput { swipe(center, Offset(left + 2f, centerY), durationMillis = 300) }
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        depthIs(2)
    }
}
