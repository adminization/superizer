package cx.m42.superizer.fixture

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.m42.apps.testapp.KitGalleryApp
import cx.m42.superizer.Superizer
import cx.m42.superizer.SuperizerContract
import cx.m42.superizer.ThemeMode
import cx.m42.superizer.host.build
import cx.m42.superizer.host.storage.PrefsStorage
import cx.m42.superizer.kit.kitTypography
import cx.m42.superizer.runtime.HostInfo
import cx.m42.superizer.runtime.Platform
import cx.m42.superizer.ui.shell.ShellLayout
import cx.m42.superizer.ui.shell.SuperizerShell
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The ui-kit gallery (`apps/test-app`'s Kit) in the adaptive shell, shot whole — the window taller
 * than the page, the empty bottom cropped — at a phone's, a tablet's and a desktop's width, light and
 * dark, in the violet accent: `build/screenshots/kit/`. These pictures are the review of the theme
 * (13 §8.2); the asserts only say the gallery opened and its overlays open.
 */
@OptIn(ExperimentalTestApi::class)
class KitGalleryTest {

    @BeforeTest
    fun isolateStorage() {
        PrefsStorage.useDirectory(Files.createTempDirectory("superizer-kit-test").toFile())
    }

    private fun host(dark: Boolean): Superizer = Superizer.build(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)) {
        host(HostInfo("Kit", "0.11.0", "dev", Platform.Desktop, SuperizerContract.VERSION, debug = true))
        register(KitGalleryApp())
        register(ProbeApp())
        home("kit", "probe")
    }.also {
        it.settings.chooseLanguage("en")
        it.settings.chooseAccent("violet")
        it.settings.chooseTheme(if (dark) ThemeMode.Dark else ThemeMode.Light)
    }

    private fun ComposeUiTest.open(dark: Boolean, width: Dp, height: Dp) {
        val superizer = host(dark)
        setContent {
            Box(Modifier.size(width, height).testTag("window")) {
                SuperizerShell(superizer, Modifier, swipeBack = false, layout = ShellLayout.Adaptive, typography = kitTypography())
            }
        }
        waitForIdle()
        onNodeWithTag("home:tile-kit").performClick()
        waitForIdle()
        onNodeWithTag("kit:page").assertExists()
    }

    private val directory = File("build/screenshots/kit").apply { mkdirs() }

    /** The window, without the rows at the bottom that are all one colour — the window taller than the page. */
    private fun ComposeUiTest.shootWhole(name: String) {
        val image = onNodeWithTag("window").captureToImage().toAwtImage()
        ImageIO.write(cropBottom(image), "png", File(directory, "$name.png"))
    }

    /** Whatever is on screen, overlays included: the last root is the popup's. */
    private fun ComposeUiTest.shootTop(name: String) {
        val roots = onAllNodes(isRoot()).fetchSemanticsNodes().size
        val image = onAllNodes(isRoot())[roots - 1].captureToImage().toAwtImage()
        ImageIO.write(image, "png", File(directory, "$name.png"))
    }

    /**
     * Down to the page's last block and a margin. Only the right half is looked at: a sidebar's footer
     * sits at the bottom of the window whatever the page's length.
     */
    private fun cropBottom(image: BufferedImage): BufferedImage {
        val blank = image.getRGB(image.width - 2, image.height - 1)
        fun rowBlank(y: Int): Boolean = (image.width / 2 until image.width step 2).all { image.getRGB(it, y) == blank }
        var bottom = image.height - 1
        while (bottom > 0 && rowBlank(bottom)) bottom--
        return image.getSubimage(0, 0, image.width, (bottom + 40).coerceAtMost(image.height))
    }

    private fun whole(width: Dp, height: Dp, label: String) {
        for (dark in listOf(false, true)) {
            // The scene as big as the window, or the capture stops at the default 1024 × 768.
            runDesktopComposeUiTest(width.value.toInt(), height.value.toInt()) {
                open(dark, width, height)
                shootWhole("$label-${if (dark) "dark" else "light"}")
            }
        }
    }

    @Test
    fun theGalleryOnAPhone() = whole(420.dp, 14000.dp, "420")

    @Test
    fun theGalleryOnATablet() = whole(800.dp, 10000.dp, "800")

    @Test
    fun theGalleryOnADesktop() = whole(1280.dp, 8000.dp, "1280")

    @Test
    fun theOverlaysOpenOnAPhoneAndOnADesktop() {
        for ((width, height) in listOf(420.dp to 844.dp, 1280.dp to 800.dp)) {
            for (dark in listOf(false, true)) {
                for (overlay in listOf("dialog", "delete", "sheet", "menu")) {
                    runDesktopComposeUiTest(width.value.toInt(), height.value.toInt()) {
                        open(dark, width, height)
                        onNodeWithTag("kit:open-$overlay").performScrollTo().performClick()
                        waitForIdle()
                        shootTop("${width.value.toInt()}-${if (dark) "dark" else "light"}-$overlay")
                    }
                }
            }
        }
    }
}
