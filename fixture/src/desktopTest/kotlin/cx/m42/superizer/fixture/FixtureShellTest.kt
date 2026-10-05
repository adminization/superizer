package cx.m42.superizer.fixture

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableStateOf

import androidx.compose.ui.test.assertTextEquals

import cx.m42.apps.testapp.SecretTestApp

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
     * D48: Home is chosen. An activation puts the bench app there, a long press takes it off, and
     * the catalog still lists it — being unlocked is what "available" means, and removing a tile
     * is not a lock.
     */
    @Test
    fun anActivationPutsTheBenchAppOnHomeAndALongPressTakesItOff() = runComposeUiTest {
        setContent { FixtureApp(fixture()) }
        waitForIdle()

        unlockBenchApp()
        goHome()
        onNodeWithTag("home:tile-test-app").assertIsDisplayed()

        onNodeWithTag("home:tile-test-app").performTouchInput { longClick() }
        waitForIdle()
        onNodeWithTag("home:tile-test-app").assertDoesNotExist()

        onNodeWithTag("home:add").performClick()
        waitForIdle()
        onNodeWithTag("catalog:row-test-app").assertIsDisplayed()
    }

    @Test
    fun theCatalogAddsAnAppToHomeAndTheChoiceSurvivesARestart() = runComposeUiTest {
        setContent { FixtureApp(fixture()) }
        waitForIdle()

        // The probe is on Home by the host's default; take it off, then put it back from the list.
        onNodeWithTag("home:tile-probe").performTouchInput { longClick() }
        waitForIdle()
        onNodeWithTag("home:tile-probe").assertDoesNotExist()
        onNodeWithText("Nothing on Home yet. Add an app from the list.").assertIsDisplayed()

        onNodeWithTag("home:add").performClick()
        waitForIdle()
        onNodeWithTag("catalog:row-probe").performClick()
        waitForIdle()
        onNodeWithContentDescription("Back").performClick()
        waitForIdle()
        onNodeWithTag("home:tile-probe").assertIsDisplayed()

        // A second host on the same preferences directory — which is what the next process is.
        setContent { FixtureApp(fixture()) }
        waitForIdle()
        onNodeWithTag("home:tile-probe").assertIsDisplayed()
    }

    @Test
    fun aLockedAppIsNotInTheCatalogAndCannotBeAddedFromIt() = runComposeUiTest {
        setContent { FixtureApp(fixture()) }
        waitForIdle()

        onNodeWithTag("home:add").performClick()
        waitForIdle()
        onNodeWithTag("catalog:row-probe").assertIsDisplayed()
        // D8 holds in the catalog too: a row for a locked app would be the reveal itself.
        onNodeWithTag("catalog:row-test-app").assertDoesNotExist()
    }

    /**
     * idea/09, the owner's journey: the code opens the secret app; back, and it is nowhere — not
     * on Home, in the drawer or the catalog; the code again, and it is back with what it kept.
     */
    @Test
    fun theSecretAppOpensByItsCodeForOneVisitAndLeavesNoTrace() = runComposeUiTest {
        setContent { FixtureApp(fixture()) }
        waitForIdle()
        onNodeWithTag("home:tile-secret-test").assertDoesNotExist()

        enterCode(SecretTestApp.PROMO_CODE)
        onNodeWithTag("secret-test:root").assertIsDisplayed()
        onNodeWithTag("secret-test:visits").assertTextEquals("Visit 1")

        goHome()
        onNodeWithTag("home:tile-secret-test").assertDoesNotExist()
        onNodeWithContentDescription("Open menu").performClick()
        waitForIdle()
        onNodeWithText("Secret app").assertDoesNotExist()
        onNodeWithText("All apps").performClick()
        waitForIdle()
        onNodeWithTag("catalog:row-test-app").assertDoesNotExist()
        onNodeWithTag("catalog:row-secret-test").assertDoesNotExist()
        onNodeWithTag("catalog:row-probe").assertIsDisplayed()

        enterCode("secret 2026")
        onNodeWithTag("secret-test:visits").assertTextEquals("Visit 2")
    }

    @Test
    fun aReleaseServiceMenuDoesNotListTheSecretAppAndADebugOneDoes() = runComposeUiTest {
        var superizer by mutableStateOf(
            buildFixture(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), debug = false)
                .also { it.settings.chooseLanguage("en") },
        )
        setContent { FixtureApp(superizer) }
        waitForIdle()
        enterCode("SERVICE")
        onNodeWithTag("service:app-test-app").assertExists()
        onNodeWithTag("service:app-secret-test").assertDoesNotExist()

        superizer = fixture()
        waitForIdle()
        enterCode("SERVICE")
        onNodeWithTag("service:app-secret-test").assertExists()
    }

    @Test
    fun theCameraReadsAQrCodeAndActivatesStraightAway() = runComposeUiTest {
        val payload = """{"type":"app_activation","appId":"test-app"}"""
        val superizer = buildFixture(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), scanner = { payload })
            .also { it.settings.chooseLanguage("en") }
        setContent { FixtureApp(superizer) }
        waitForIdle()

        onNodeWithContentDescription("Open menu").performClick()
        waitForIdle()
        onNodeWithContentDescription("Activate").performClick()
        waitForIdle()
        onNodeWithTag("activate:scan").performClick()
        waitForIdle()

        onNodeWithTag("test-app:root").assertIsDisplayed()
    }

    @Test
    fun withoutACameraThereIsNoScanButtonAndAScannedSecretIdIsUnknown() = runComposeUiTest {
        val superizer = buildFixture(
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            scanner = { """{"type":"app_activation","appId":"secret-test"}""" },
        ).also { it.settings.chooseLanguage("en") }
        var host by mutableStateOf(fixture())
        setContent { FixtureApp(host) }
        waitForIdle()
        onNodeWithContentDescription("Open menu").performClick()
        waitForIdle()
        onNodeWithContentDescription("Activate").performClick()
        waitForIdle()
        onNodeWithTag("activate:scan").assertDoesNotExist()

        host = superizer
        waitForIdle()
        onNodeWithContentDescription("Open menu").performClick()
        waitForIdle()
        onNodeWithContentDescription("Activate").performClick()
        waitForIdle()
        onNodeWithTag("activate:scan").performClick()
        waitForIdle()
        onNodeWithTag("activate:error").assertIsDisplayed()
        onNodeWithTag("secret-test:root").assertDoesNotExist()
    }

    private fun ComposeUiTest.enterCode(code: String) {
        onNodeWithContentDescription("Open menu").performClick()
        waitForIdle()
        onNodeWithContentDescription("Activate").performClick()
        waitForIdle()
        onNodeWithTag("activate:promo").performTextInput(code)
        onNodeWithTag("activate:apply").performClick()
        waitForIdle()
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
        onNodeWithText("Home").performClick()
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
