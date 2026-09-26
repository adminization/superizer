package cx.m42.superizer.fixture

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.composeunstyled.Text
import cx.m42.superizer.Superizer
import cx.m42.superizer.SuperizerContract
import cx.m42.superizer.activation.Activation
import cx.m42.superizer.activation.ActivationResult
import cx.m42.superizer.app.AppConfigSpec
import cx.m42.superizer.app.AppId
import cx.m42.superizer.app.AppInstance
import cx.m42.superizer.app.AppManifest
import cx.m42.superizer.app.AppMetadata
import cx.m42.superizer.app.AppProtection
import cx.m42.superizer.app.AppSetupContext
import cx.m42.superizer.app.LockPolicy
import cx.m42.superizer.app.SuperizerApp
import cx.m42.superizer.app.localized
import cx.m42.superizer.host.build
import cx.m42.superizer.host.storage.HostKeys
import cx.m42.superizer.host.storage.PrefsStorage
import cx.m42.superizer.host.storage.SafePrefs
import cx.m42.superizer.lock.AuthAvailability
import cx.m42.superizer.lock.AuthOutcome
import cx.m42.superizer.runtime.HostInfo
import cx.m42.superizer.runtime.HostLifecycle
import cx.m42.superizer.runtime.InstanceRuntime
import cx.m42.superizer.runtime.LocalAppRuntime
import cx.m42.superizer.runtime.Platform
import cx.m42.superizer.testing.FakeDeviceAuthenticator
import cx.m42.superizer.ui.components.AppButton
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * 06 §11, the main test: however an app reaches the screen, a protected one does not show a node
 * of its own until the lock is open. Eight roads, one curtain — which is the whole case for putting
 * the curtain in the container rather than in front of each road.
 */
@OptIn(ExperimentalTestApi::class)
class LockShellTest {

    @BeforeTest
    fun isolateStorage() {
        PrefsStorage.useDirectory(Files.createTempDirectory("superizer-lock-test").toFile())
    }

    private val device = FakeDeviceAuthenticator(answer = AuthOutcome.Cancelled)
    private val lifecycle = MutableStateFlow(HostLifecycle.Foreground)

    private fun host(): Superizer = Superizer.build(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)) {
        host(HostInfo("Fixture", "0.1.0", "dev", Platform.Desktop, SuperizerContract.VERSION, debug = true))
        scheme("superizer")
        register(SafeApp())
        register(OpenerApp())
        home("safe", "opener")
        promoCodes(mapOf("SAFE" to ActivationResult.Success(Activation(AppId("safe"), SafeApp.config("promo")))))
        serviceCode("SERVICE")
        deviceAuthenticator(device)
        lifecycle(lifecycle)
    }.also { it.settings.chooseLanguage("en") }

    /** The curtain is up and not one node of the app is in the tree. */
    private fun ComposeUiTest.assertCovered() {
        waitForIdle()
        onNodeWithTag("shell:lock").assertIsDisplayed()
        onNodeWithTag("safe:root").assertDoesNotExist()
        onNodeWithTag("safe:secret").assertDoesNotExist()
    }

    private fun ComposeUiTest.openMenu() {
        onNodeWithContentDescription("Open menu").performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.activate(code: String) {
        openMenu()
        onNodeWithContentDescription("Activate").performClick()
        waitForIdle()
        onNodeWithTag("activate:promo").performTextInput(code)
        onNodeWithTag("activate:apply").performClick()
        waitForIdle()
    }

    @Test
    fun theTile() = runComposeUiTest {
        setContent { FixtureApp(host()) }
        waitForIdle()
        onNodeWithTag("home:tile-safe").performClick()
        assertCovered()
        // The curtain asked once by itself, and the person said no.
        assertTrue(device.prompts.size == 1)
    }

    @Test
    fun theMenu() = runComposeUiTest {
        setContent { FixtureApp(host()) }
        waitForIdle()
        // From a screen that does not itself say "Safe", so the menu's entry is the only one.
        openMenu()
        onNodeWithContentDescription("Activate").performClick()
        waitForIdle()
        openMenu()
        onNodeWithText("Safe").performClick()
        assertCovered()
    }

    @Test
    fun aDeepLink() = runComposeUiTest {
        val superizer = host()
        setContent { FixtureApp(superizer) }
        waitForIdle()
        superizer.route.deliver("superizer://app/safe/add?secret=JBSWY3DPEHPK3PXP")
        assertCovered()
    }

    @Test
    fun anActivation() = runComposeUiTest {
        setContent { FixtureApp(host()) }
        waitForIdle()
        activate("SAFE")
        assertCovered()
    }

    @Test
    fun aRestoredSnapshot() = runComposeUiTest {
        val first = host()
        setContent { FixtureApp(first) }
        waitForIdle()
        onNodeWithTag("home:tile-safe").performClick()
        waitForIdle()
        // Background is where the snapshot is written; the next process restores from it.
        lifecycle.value = HostLifecycle.Background
        waitForIdle()
        lifecycle.value = HostLifecycle.Foreground

        setContent { FixtureApp(host()) }
        assertCovered()
    }

    @Test
    fun theServiceMenu() = runComposeUiTest {
        setContent { FixtureApp(host()) }
        waitForIdle()
        activate("SERVICE")
        onNodeWithTag("service:app-safe").performClick()
        waitForIdle()
        onNodeWithText("Run").performScrollTo().performClick()
        assertCovered()
    }

    @Test
    fun anotherAppOpeningIt() = runComposeUiTest {
        setContent { FixtureApp(host()) }
        waitForIdle()
        onNodeWithTag("home:tile-opener").performClick()
        waitForIdle()
        onNodeWithTag("opener:open-safe").performClick()
        assertCovered()
    }

    @Test
    fun aNotificationTap() = runComposeUiTest {
        val superizer = host()
        setContent { FixtureApp(superizer) }
        waitForIdle()
        CoroutineScope(Dispatchers.Unconfined).launch {
            superizer.diagnostics.simulatePush(mapOf("schemaVersion" to "1", "appId" to "safe", "tapped" to "true"))
        }
        assertCovered()
    }

    @Test
    fun unlockingShowsTheAppAndLockNowBringsTheCurtainBack() = runComposeUiTest {
        val superizer = host()
        setContent { FixtureApp(superizer) }
        waitForIdle()
        onNodeWithTag("home:tile-safe").performClick()
        assertCovered()

        device.answer = AuthOutcome.Success
        onNodeWithTag("shell:unlock").performClick()
        waitForIdle()
        onNodeWithTag("safe:secret").assertIsDisplayed()
        onNodeWithTag("shell:lock").assertDoesNotExist()

        // Settings' own "Lock now" brings the curtain back over the live session.
        device.answer = AuthOutcome.Cancelled
        superizer.lock.lockNow()
        assertCovered()
        // The app is still open underneath; only its screen is withheld.
        assertTrue(superizer.handler.current.value?.app?.id == AppId("safe"))
    }

    @Test
    fun withNoScreenLockTheAppOpensUnderTheBanner() = runComposeUiTest {
        device.availability = AuthAvailability.NoScreenLock
        setContent { FixtureApp(host()) }
        waitForIdle()
        onNodeWithTag("home:tile-safe").performClick()
        waitForIdle()
        onNodeWithTag("shell:unprotected").assertIsDisplayed()
        onNodeWithTag("safe:secret").assertIsDisplayed()
        assertTrue(device.prompts.isEmpty())
    }

    @Test
    fun theSecretInALinkIsInNoEventAndNoSnapshot() = runComposeUiTest {
        // §6 / D134, end to end: the event log the Service Menu prints and the snapshot in prefs.
        val superizer = host()
        setContent { FixtureApp(superizer) }
        waitForIdle()
        superizer.route.deliver("superizer://app/safe/add?secret=JBSWY3DPEHPK3PXP")
        waitForIdle()
        lifecycle.value = HostLifecycle.Background
        waitForIdle()

        assertFalse(superizer.diagnostics.recentEvents.value.any { "JBSWY3DPEHPK3PXP" in it.toString() })
        val stored = SafePrefs.get(HostKeys.SESSION).orEmpty()
        assertTrue("safe" in stored)
        assertFalse("JBSWY3DPEHPK3PXP" in stored)
    }

    /**
     * §11: the Protection section with every kind of policy, as a picture to look at — a switch for
     * each optional app in its app-given position, a plain "Always" for the required one, and no
     * row at all for an app with no lock.
     */
    @Test
    fun theProtectionSectionWithEveryPolicy() = androidx.compose.ui.test.runDesktopComposeUiTest(width = 824, height = 1830) {
        val superizer = Superizer.build(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)) {
            host(HostInfo("Fixture", "0.1.0", "dev", Platform.Desktop, SuperizerContract.VERSION, debug = true))
            register(SafeApp())
            register(PolicyApp("notes", "Notes", LockPolicy.OptionalOff))
            register(PolicyApp("wallet", "Wallet", LockPolicy.OptionalOn))
            register(PolicyApp("clock", "Clock", LockPolicy.Off))
            deviceAuthenticator(device)
        }.also { it.settings.chooseLanguage("en") }
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(2f),
            ) { FixtureApp(superizer) }
        }
        waitForIdle()
        openMenu()
        onNodeWithContentDescription("Settings").performClick()
        waitForIdle()
        onNodeWithTag("settings:lock-now").performScrollTo()
        waitForIdle()

        onNodeWithTag("settings:lock-safe").assertExists()
        onNodeWithTag("settings:lock-notes").assertExists()
        onNodeWithTag("settings:lock-wallet").assertExists()
        onNodeWithTag("settings:lock-clock").assertDoesNotExist()

        val directory = java.io.File("build/screenshots").apply { mkdirs() }
        javax.imageio.ImageIO.write(
            onRoot().captureToImage().toAwtImage(),
            "png",
            java.io.File(directory, "protection-settings.png"),
        )
    }

    @Test
    fun settingsHidesTheSectionOfACoveredAppAndListsItsLock() = runComposeUiTest {
        setContent { FixtureApp(host()) }
        waitForIdle()
        openMenu()
        onNodeWithContentDescription("Settings").performClick()
        waitForIdle()
        onNodeWithTag("settings:protection").assertExists()
        onNodeWithTag("settings:lock-safe").assertExists()
        onNodeWithTag("settings:section-safe").assertDoesNotExist()
    }
}

@Serializable
private data class SafeConfig(val secret: String? = null)

/** A protected app with a secret on its screen and a deep link that carries one. */
private class SafeApp : SuperizerApp<SafeConfig>() {
    override val manifest = AppManifest(
        id = AppId("safe"),
        version = "1.0.0",
        minHostContract = 2,
        metadata = AppMetadata(title = localized("en" to "Safe")),
        deepLinks = setOf("add"),
        protection = AppProtection(lock = LockPolicy.Required, secureWindow = true),
    )

    override val configSpec = AppConfigSpec(SafeConfig.serializer(), SafeConfig())

    override fun setup(ctx: AppSetupContext) {
        ctx.deepLink("add") { params -> configSpec.encode(SafeConfig(params["secret"])) }
        ctx.settingsSection { Text("safe settings", modifier = Modifier.testTag("safe:settings")) }
    }

    override fun launch(runtime: InstanceRuntime, config: SafeConfig): AppInstance = object : AppInstance() {
        @Composable
        override fun Content() {
            Text("secret: ${config.secret}", modifier = Modifier.testTag("safe:secret"))
        }
    }

    companion object {
        fun config(secret: String) = AppConfigSpec(SafeConfig.serializer(), SafeConfig()).encode(SafeConfig(secret))
    }
}

/** An app that exists only to carry one lock policy into Settings. */
private class PolicyApp(id: String, title: String, lock: LockPolicy) : SuperizerApp<Unit>() {
    override val manifest = AppManifest(
        id = AppId(id),
        version = "1.0.0",
        minHostContract = if (lock == LockPolicy.Off) 1 else 2,
        metadata = AppMetadata(title = localized("en" to title)),
        protection = AppProtection(lock = lock),
    )

    override val configSpec = AppConfigSpec.None

    override fun launch(runtime: InstanceRuntime, config: Unit): AppInstance = object : AppInstance() {
        @Composable
        override fun Content() = Unit
    }
}

/** An ordinary app whose one button opens the protected one — road seven. */
private class OpenerApp : SuperizerApp<Unit>() {
    override val manifest = AppManifest(
        id = AppId("opener"),
        version = "1.0.0",
        metadata = AppMetadata(title = localized("en" to "Opener")),
    )

    override val configSpec = AppConfigSpec.None

    override fun launch(runtime: InstanceRuntime, config: Unit): AppInstance = object : AppInstance() {
        @Composable
        override fun Content() {
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            val app = LocalAppRuntime.current
            Column {
                AppButton(
                    text = "Open the safe",
                    onClick = { scope.launch { app.navigation.openApp(AppId("safe")) } },
                    modifier = Modifier.testTag("opener:open-safe"),
                )
            }
        }
    }
}
