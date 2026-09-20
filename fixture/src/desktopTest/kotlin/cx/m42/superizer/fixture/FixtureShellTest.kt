package cx.m42.superizer.fixture

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import cx.m42.apps.testapp.TestApp
import cx.m42.superizer.Superizer
import cx.m42.superizer.host.storage.PrefsStorage
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
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

    @BeforeTest
    fun isolateStorage() {
        PrefsStorage.useDirectory(
            File(System.getProperty("java.io.tmpdir"), "superizer-fixture-test-${counter.incrementAndGet()}"),
        )
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
        // §22: every runtime service has a card, and the config card has no "opened by" line.
        listOf("identity", "config", "lifecycle", "state", "storage", "network", "navigation", "push", "veto")
            .forEach { card -> onNodeWithTag("test-app:section-$card").assertIsDisplayed() }
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

    @Test
    fun aLinkDeliveredBeforeTheFirstFrameOpensTheAppItNames() = runComposeUiTest {
        val superizer = fixture()
        superizer.route.deliver("superizer://app/probe")
        setContent { FixtureApp(superizer) }
        waitForIdle()

        onNodeWithTag("probe:root").assertIsDisplayed()
    }

    private companion object {
        val counter = AtomicInteger(0)
    }
}
