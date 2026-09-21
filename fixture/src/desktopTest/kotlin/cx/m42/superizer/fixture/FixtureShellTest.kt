package cx.m42.superizer.fixture

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import cx.m42.apps.testapp.TestApp
import cx.m42.superizer.Superizer
import cx.m42.superizer.host.storage.PrefsStorage
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The library, proving itself without Unitool.
 *
 * That is the whole reason this module exists (12 §1): a framework whose only evidence is the one
 * product built on it is a framework nobody can tell apart from that product. These are the same
 * journeys Unitool's own tests take — home, an app, an activation — against a host that knows
 * nothing about calculators.
 */
@OptIn(ExperimentalTestApi::class)
class FixtureShellTest {

    /**
     * A new directory on every test *and* on every run: the host stores unlocked apps and the
     * language, and a reused name would let one run's activation decide the next one's result.
     */
    @BeforeTest
    fun isolateStorage() {
        PrefsStorage.useDirectory(Files.createTempDirectory("superizer-fixture-test").toFile())
    }

    private fun fixture(): Superizer =
        buildFixture(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)).also {
            it.settings.chooseLanguage("en")
        }

    @Test
    fun homeShowsTheVisibleAppAndHidesTheBenchApp() = runComposeUiTest {
        setContent { FixtureApp(fixture()) }
        waitForIdle()

        onNodeWithTag("home:tile-probe").assertIsDisplayed()
        onNodeWithTag("home:tile-test-app").assertDoesNotExist()
    }

    @Test
    fun anAppShippingItsOwnIconOpensLikeAnyOther() = runComposeUiTest {
        // The probe's icon is an SVG path it carries itself (D29) — nothing in the host's glyph
        // pack knows about it, and it still draws and still opens.
        setContent { FixtureApp(fixture()) }
        waitForIdle()

        onNodeWithTag("home:tile-probe").performClick()
        waitForIdle()

        onNodeWithTag("probe:root").assertIsDisplayed()
        onNodeWithTag("probe:label").assertIsDisplayed()
    }

    @Test
    fun thePromoCodeUnlocksTheBenchAppAndItShowsEveryService() = runComposeUiTest {
        setContent { FixtureApp(fixture()) }
        waitForIdle()

        onNodeWithContentDescription("Open menu").performClick()
        waitForIdle()
        onNodeWithContentDescription("Activate").performClick()
        waitForIdle()
        onNodeWithTag("activate:promo").performTextInput(TestApp.PROMO_CODE)
        onNodeWithTag("activate:apply").performClick()
        waitForIdle()

        onNodeWithTag("test-app:root").assertIsDisplayed()
        // §22: every runtime service has a card. `assertExists` rather than `assertIsDisplayed`,
        // because the page is taller than a phone — most of these are below the fold, and a test
        // that scrolled to each one would be testing the scroll.
        listOf(
            "identity", "config", "lifecycle", "state", "storage", "network",
            "analytics", "navigation", "services", "locale", "haptics", "push", "auth", "veto",
        ).forEach { card -> onNodeWithTag("test-app:section-$card").assertExists() }
        onNodeWithTag("test-app:section-identity").assertIsDisplayed()
    }

    @Test
    fun theServiceMenuListsEveryAppTheRegistryWasOffered() = runComposeUiTest {
        setContent { FixtureApp(fixture()) }
        waitForIdle()

        onNodeWithContentDescription("Open menu").performClick()
        waitForIdle()
        onNodeWithContentDescription("Activate").performClick()
        waitForIdle()
        onNodeWithTag("activate:promo").performTextInput("SERVICE")
        onNodeWithTag("activate:apply").performClick()
        waitForIdle()

        onNodeWithTag("service:root").assertIsDisplayed()
        onNodeWithTag("service:app-probe").assertIsDisplayed()
        // Including the hidden one: the Service Menu shows everything, which is what it is for.
        onNodeWithTag("service:app-test-app").assertIsDisplayed()
    }

    /**
     * D48, the way back out of D8: what an activation revealed, a long press puts away again.
     *
     * The assertion that matters is the second half — a long press on the *ordinary* app does
     * nothing. Hiding is the inverse of an activation, not a way to lose the app you use.
     */
    @Test
    fun aLongPressPutsAnUnlockedHiddenAppAwayAgain() = runComposeUiTest {
        setContent { FixtureApp(fixture()) }
        waitForIdle()

        unlockBenchApp()
        goHome()
        onNodeWithTag("home:tile-test-app").assertIsDisplayed()

        onNodeWithTag("home:tile-test-app").performTouchInput { longClick() }
        waitForIdle()
        onNodeWithTag("home:confirm-hide").assertIsDisplayed()
        onNodeWithText("Hide").performClick()
        waitForIdle()

        onNodeWithTag("home:tile-test-app").assertDoesNotExist()

        // The ordinary app has no long press to answer: the gesture falls through to the tap it
        // always was and the app opens, which is the only sane thing a tile can do with it.
        onNodeWithTag("home:tile-probe").performTouchInput { longClick() }
        waitForIdle()
        onNodeWithTag("home:confirm-hide").assertDoesNotExist()
        onNodeWithTag("probe:root").assertIsDisplayed()
    }

    private fun ComposeUiTest.unlockBenchApp() {
        onNodeWithContentDescription("Open menu").performClick()
        waitForIdle()
        onNodeWithContentDescription("Activate").performClick()
        waitForIdle()
        onNodeWithTag("activate:promo").performTextInput(TestApp.PROMO_CODE)
        onNodeWithTag("activate:apply").performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.goHome() {
        onNodeWithContentDescription("Open menu").performClick()
        waitForIdle()
        onNodeWithText("All apps").performClick()
        waitForIdle()
    }

    @Test
    fun aLinkDeliveredBeforeTheFirstFrameOpensTheAppItNames() = runComposeUiTest {
        val superizer = fixture()
        superizer.route.deliver("superizer://app/probe")
        setContent { FixtureApp(superizer) }
        waitForIdle()

        onNodeWithTag("probe:root").assertIsDisplayed()
    }
}
