package cx.m42.superizer.fixture

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import cx.m42.superizer.Superizer
import cx.m42.superizer.SuperizerContract
import cx.m42.superizer.app.AppChrome
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
import cx.m42.superizer.ui.shell.SuperizerShell
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Past the shell's 300 ms slide, at 16 ms a frame. */
private const val SlideFrames = 30

/** `AppChrome.fullscreen`: the app gets the whole window, and an app that does not ask keeps the frame. */
@OptIn(ExperimentalTestApi::class)
class FullscreenTest {

    @BeforeTest
    fun isolateStorage() {
        PrefsStorage.useDirectory(Files.createTempDirectory("superizer-fullscreen-test").toFile())
    }

    private class Probe(private val fullscreen: Boolean) : SuperizerApp<Unit>() {
        override val manifest = AppManifest(
            id = AppId("probe"),
            version = "1.0.0",
            metadata = AppMetadata(title = localized("en" to "Probe"), icon = AppIcon.Letter('P')),
        )
        override val configSpec: AppConfigSpec<Unit> = AppConfigSpec.None

        override fun launch(runtime: InstanceRuntime, config: Unit): AppInstance = object : AppInstance() {
            override val chrome: StateFlow<AppChrome> = MutableStateFlow(AppChrome(fullscreen = fullscreen))

            @Composable
            override fun Content() {
                Box(Modifier.fillMaxSize().testTag("probe:body")) {
                    Box(
                        Modifier.size(48.dp).testTag("probe:close").clickable {
                            runtime.scope.launch { runtime.navigation.close() }
                        },
                    )
                }
            }
        }
    }

    private fun host(fullscreen: Boolean): Superizer = Superizer.build(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)) {
        host(HostInfo("Fixture", "1", "test", Platform.Desktop, SuperizerContract.VERSION, debug = true))
        languages(fallback = "en")
        register(Probe(fullscreen))
        home("probe")
    }.also { it.settings.chooseLanguage("en") }

    @Test
    fun aFullscreenAppHasTheWholeWindowAndNoFrame() = runComposeUiTest {
        val superizer = host(fullscreen = true)
        setContent { SuperizerShell(superizer) }
        waitForIdle()
        onNodeWithTag("home:tile-probe").performClick()
        waitUntil(timeoutMillis = 5_000L) { runCatching { onNodeWithTag("probe:body").assertExists() }.isSuccess }
        waitForIdle()

        onNodeWithContentDescription("Open menu").assertDoesNotExist()
        val window = onRoot().getUnclippedBoundsInRoot()
        val body = onNodeWithTag("probe:body").getUnclippedBoundsInRoot()
        assertEquals(window.width, body.width, "no width cap")
        assertEquals(window.height, body.height, "no bar above it")
    }

    /**
     * Frame by frame, because the bar is wrong even for the length of a slide. "Back" is on an open app's
     * bar and not on Home's, so Home sliding along under the app does not count against it.
     */
    @Test
    fun noBarShowsWhileAFullscreenAppSlidesInOrOut() = runComposeUiTest {
        val superizer = host(fullscreen = true)
        setContent { SuperizerShell(superizer) }
        waitForIdle()
        mainClock.autoAdvance = false

        fun noBarForTheWholeSlide() = repeat(SlideFrames) {
            mainClock.advanceTimeByFrame()
            onNodeWithContentDescription("Back").assertDoesNotExist()
        }

        onNodeWithTag("home:tile-probe").performClick()
        noBarForTheWholeSlide()
        onNodeWithTag("probe:body").assertExists()

        onNodeWithTag("probe:close").performClick()
        noBarForTheWholeSlide()
        onNodeWithTag("home:tile-probe").assertExists()
        onNodeWithTag("probe:body").assertDoesNotExist()
    }

    @Test
    fun anOrdinaryAppKeepsTheFrame() = runComposeUiTest {
        val superizer = host(fullscreen = false)
        setContent { SuperizerShell(superizer) }
        waitForIdle()
        onNodeWithTag("home:tile-probe").performClick()
        waitUntil(timeoutMillis = 5_000L) { runCatching { onNodeWithTag("probe:body").assertExists() }.isSuccess }
        waitForIdle()

        onNodeWithContentDescription("Open menu").assertExists()
        val window = onRoot().getUnclippedBoundsInRoot()
        val body = onNodeWithTag("probe:body").getUnclippedBoundsInRoot()
        assertTrue(body.height < window.height, "the bar takes its height")
    }
}
