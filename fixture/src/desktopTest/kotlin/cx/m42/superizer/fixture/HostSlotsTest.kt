package cx.m42.superizer.fixture

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.composeunstyled.Text
import cx.m42.superizer.HomeBanner
import cx.m42.superizer.HostScreen
import cx.m42.superizer.HostSection
import cx.m42.superizer.Superizer
import cx.m42.superizer.SuperizerContract
import cx.m42.superizer.diagnostics.DiagnosticsCode
import cx.m42.superizer.host.build
import cx.m42.superizer.host.storage.PrefsStorage
import cx.m42.superizer.runtime.HostInfo
import cx.m42.superizer.runtime.Platform
import cx.m42.superizer.ui.components.AppButton
import cx.m42.superizer.ui.i18n.LocalDiagnosticsStrings
import cx.m42.superizer.ui.shell.SuperizerShell
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The slots of 07 §2.4: a host block in Settings, a host screen behind it with the shell's frame
 * and back button, and a line on Home — all written by the host, none known to the library.
 */
@OptIn(ExperimentalTestApi::class)
class HostSlotsTest {

    @BeforeTest
    fun isolateStorage() {
        PrefsStorage.useDirectory(Files.createTempDirectory("superizer-slots-test").toFile())
    }

    private val detail = object : HostScreen {
        override val id = "keys"
        override fun title(langTag: String) = if (langTag.startsWith("ru")) "Ключи" else "Keys"

        @Composable
        override fun Content(superizer: Superizer, back: () -> Unit, open: (HostScreen) -> Unit) {
            Text("the host's own screen", modifier = Modifier.testTag("keys:body"))
            AppButton(text = "Done", onClick = back, modifier = Modifier.testTag("keys:done"))
        }
    }

    private val section = object : HostSection {
        override val id = "keys"
        override fun title(langTag: String) = "SSH keys"

        @Composable
        override fun Content(superizer: Superizer, open: (HostScreen) -> Unit) {
            AppButton(text = "Manage keys", onClick = { open(detail) }, modifier = Modifier.testTag("keys:manage"))
        }
    }

    private val banner = HomeBanner { _, open ->
        // A banner in the host's words — here, whatever words the host provided for a code.
        AppButton(
            text = LocalDiagnosticsStrings.current.title(DiagnosticsCode.VaultMissing.id),
            onClick = { open(detail) },
            modifier = Modifier.testTag("banner:storage"),
        )
        true
    }

    private fun host(): Superizer = Superizer.build(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)) {
        host(HostInfo("Slots", "0.1.0", "dev", Platform.Desktop, SuperizerContract.VERSION, debug = true))
        register(ProbeApp())
        home("probe")
        hostSection(section)
        homeBanner(banner)
    }.also { it.settings.chooseLanguage("en") }

    @Test
    fun aHostSectionOpensAHostScreenInTheShellsFrameAndBackReturns() = runComposeUiTest {
        setContent { SuperizerShell(host()) }
        waitForIdle()
        onNodeWithContentDescription("Open menu").performClick()
        waitForIdle()
        onNodeWithContentDescription("Settings").performClick()
        waitForIdle()

        onNodeWithTag("settings:host:keys").performScrollTo().assertIsDisplayed()
        onNodeWithText("SSH keys").assertExists()
        onNodeWithTag("keys:manage").performClick()
        waitForIdle()

        onNodeWithTag("host:keys").assertIsDisplayed()
        onNodeWithTag("keys:body").assertIsDisplayed()
        onNodeWithText("Keys").assertIsDisplayed()

        onNodeWithTag("keys:done").performClick()
        waitForIdle()
        onNodeWithTag("settings:host:keys").assertExists()
        onNodeWithTag("host:keys").assertDoesNotExist()
    }

    @Test
    fun aHomeBannerSitsAboveTheTilesAndShowsRawCodesWithoutAWordsTable() = runComposeUiTest {
        setContent { SuperizerShell(host()) }
        waitForIdle()
        onNodeWithTag("banner:storage").assertIsDisplayed()
        // No table provided: the code itself, never nothing (06 §6).
        onNodeWithText("vault.missing").assertIsDisplayed()
        onNodeWithTag("home:tile-probe").assertIsDisplayed()

        onNodeWithTag("banner:storage").performClick()
        waitForIdle()
        onNodeWithTag("host:keys").assertIsDisplayed()
    }
}
