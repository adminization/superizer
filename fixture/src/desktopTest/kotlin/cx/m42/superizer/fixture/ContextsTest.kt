package cx.m42.superizer.fixture

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.composeunstyled.Text
import cx.m42.superizer.ContextPort
import cx.m42.superizer.HostContext
import cx.m42.superizer.HostScreen
import cx.m42.superizer.Superizer
import cx.m42.superizer.SuperizerContract
import cx.m42.superizer.host.build
import cx.m42.superizer.host.storage.PrefsStorage
import cx.m42.superizer.runtime.HostInfo
import cx.m42.superizer.runtime.Platform
import cx.m42.superizer.ui.shell.SuperizerShell
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The context switcher (RestoApp D6): a host without contexts keeps the drawer it always had, and
 * a host with them gets the row, the sheet, and the context's name on Home.
 */
@OptIn(ExperimentalTestApi::class)
class ContextsTest {

    @BeforeTest
    fun isolateStorage() {
        PrefsStorage.useDirectory(Files.createTempDirectory("superizer-contexts-test").toFile())
    }

    private val addScreen = object : HostScreen {
        override val id = "add-place"
        override fun title(langTag: String) = "Add a place"

        @Composable
        override fun Content(superizer: Superizer, back: () -> Unit, open: (HostScreen) -> Unit) {
            Text("the host's own form", modifier = Modifier.testTag("place:form"))
        }
    }

    /** Two places; switching is recorded rather than acted on — what it means is the host's. */
    private inner class Places : ContextPort {
        val switched = mutableListOf<String>()
        private val all = listOf(HostContext("a", "Alpha", "alpha.example"), HostContext("b", "Beta", "beta.example"))
        override val contexts: StateFlow<List<HostContext>> = MutableStateFlow(all)
        override val current: StateFlow<HostContext?> = MutableStateFlow(all[0])
        override fun switchTo(id: String) { switched += id }
        override fun previous(): HostContext = all[1]
        override fun title(langTag: String) = "Places"
        override val addScreen: HostScreen = this@ContextsTest.addScreen
    }

    private fun host(port: ContextPort? = null): Superizer =
        Superizer.build(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)) {
            host(HostInfo("Contexts", "0.1.0", "dev", Platform.Desktop, SuperizerContract.VERSION, debug = true))
            register(ProbeApp())
            home("probe")
            if (port != null) contexts(port)
        }.also { it.settings.chooseLanguage("en") }

    @Test
    fun aHostWithoutContextsKeepsTheDrawerItAlwaysHad() = runComposeUiTest {
        setContent { SuperizerShell(host()) }
        waitForIdle()
        onNodeWithText("Contexts").assertIsDisplayed()

        onNodeWithContentDescription("Open menu").performClick()
        waitForIdle()

        onNodeWithText("Menu").assertIsDisplayed()
        onNodeWithTag("context:row").assertDoesNotExist()
    }

    @Test
    fun homeIsTitledWithTheCurrentContext() = runComposeUiTest {
        setContent { SuperizerShell(host(Places())) }
        waitForIdle()

        onNodeWithText("Alpha").assertIsDisplayed()
        onNodeWithText("alpha.example").assertIsDisplayed()
    }

    @Test
    fun theRowOpensTheSheetAndAPickIsHandedToTheHost() = runComposeUiTest {
        val places = Places()
        setContent { SuperizerShell(host(places)) }
        waitForIdle()
        onNodeWithContentDescription("Open menu").performClick()
        waitForIdle()

        onNodeWithText("Menu").assertDoesNotExist()
        onNodeWithTag("context:row").assertIsDisplayed().performClick()
        waitForIdle()

        onNodeWithTag("context:sheet").assertIsDisplayed()
        onNodeWithText("Places").assertIsDisplayed()
        onNodeWithTag("context:item-a").assertIsSelected()
        onNodeWithTag("context:item-b").performClick()
        waitForIdle()

        assertEquals(listOf("b"), places.switched)
        onNodeWithTag("context:sheet").assertDoesNotExist()
    }

    @Test
    fun pickingTheCurrentOneSwitchesNothing() = runComposeUiTest {
        val places = Places()
        setContent { SuperizerShell(host(places)) }
        waitForIdle()
        onNodeWithContentDescription("Open menu").performClick()
        waitForIdle()
        onNodeWithTag("context:row").performClick()
        waitForIdle()

        onNodeWithTag("context:item-a").performClick()
        waitForIdle()

        assertEquals(emptyList(), places.switched)
    }

    @Test
    fun theAddRowOpensTheHostsScreenInTheShellsFrame() = runComposeUiTest {
        setContent { SuperizerShell(host(Places())) }
        waitForIdle()
        onNodeWithContentDescription("Open menu").performClick()
        waitForIdle()
        onNodeWithTag("context:row").performClick()
        waitForIdle()

        onNodeWithTag("context:add").performClick()
        waitForIdle()

        onNodeWithTag("host:add-place").assertIsDisplayed()
        onNodeWithTag("place:form").assertIsDisplayed()
        onNodeWithTag("context:sheet").assertDoesNotExist()
    }

    @Test
    fun aLongPressGoesBackToThePreviousContext() = runComposeUiTest {
        val places = Places()
        setContent { SuperizerShell(host(places)) }
        waitForIdle()
        onNodeWithContentDescription("Open menu").performClick()
        waitForIdle()

        onNodeWithTag("context:row").performTouchInput { longClick() }
        waitForIdle()

        assertEquals(listOf("b"), places.switched)
        onNodeWithTag("context:sheet").assertDoesNotExist()
    }
}
