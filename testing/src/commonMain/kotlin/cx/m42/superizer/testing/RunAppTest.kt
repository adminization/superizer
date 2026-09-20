@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package cx.m42.superizer.testing

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import cx.m42.superizer.app.AppConfig
import cx.m42.superizer.app.AppConfigSpec
import cx.m42.superizer.app.AppInstance
import cx.m42.superizer.app.SuperizerApp
import cx.m42.superizer.runtime.LocalAppRuntime
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * What a test of a live app holds: the instance, the fakes behind it, and the composition.
 *
 * The verbs below are deliberately the only ones: an app's tests find nodes by `testTag` (D39),
 * because text changes with the language and a suite that asserts on Russian strings is a suite
 * that fails the day somebody adds a third table. [ui] is there for the cases these do not cover —
 * it is the experimental Compose testing API, which is why using it needs an opt-in and these do
 * not.
 */
public class AppTestScope<C : Any>(
    public val app: SuperizerApp<C>,
    public val instance: AppInstance,
    public val runtime: FakeAppRuntime,
    public val ui: ComposeUiTest,
) {
    public fun node(tag: String): SemanticsNodeInteraction = ui.onNodeWithTag(tag)

    public fun click(tag: String) {
        ui.onNodeWithTag(tag).performClick()
        ui.waitForIdle()
    }

    public fun type(tag: String, text: String) {
        ui.onNodeWithTag(tag).performTextInput(text)
        ui.waitForIdle()
    }

    public fun assertShown(tag: String) {
        ui.onNodeWithTag(tag).assertIsDisplayed()
    }

    public fun assertAbsent(tag: String) {
        ui.onNodeWithTag(tag).assertDoesNotExist()
    }

    public fun idle() {
        ui.waitForIdle()
    }
}

/**
 * Runs one app on fakes, composed inside a container that behaves like the host's.
 *
 * This is what an app's own tests import instead of a host (04): it launches, calls `onLaunch`,
 * provides `LocalAppRuntime` and puts the `<id>:root` tag on, so a test asserts about the app and
 * never about the shell. No `Superizer`, no registry, no handler — an app that needed those to be
 * testable would be an app that cannot ship on its own.
 *
 * @param restore a snapshot to hand the instance, which is what the handler does after a process
 *   death. Passing one here is how "the half-typed sum came back" is tested without a host.
 */
public fun <C : Any> runAppTest(
    app: SuperizerApp<C>,
    config: AppConfig = AppConfig.Empty,
    restore: kotlinx.serialization.json.JsonObject? = null,
    prepare: (FakeAppRuntime) -> Unit = {},
    block: AppTestScope<C>.() -> Unit,
) {
    runComposeUiTest {
        // Unconfined, so work an app starts in `runtime.scope` — writing a result to storage,
        // kicking off a refresh — has already happened by the time the test looks. A queued
        // dispatcher would make every assertion a guess about when to advance it.
        val scope = TestScope(UnconfinedTestDispatcher())
        val runtime = FakeAppRuntime(app.id, scope, declaredServices = app.manifest.requires)
        prepare(runtime)

        @Suppress("UNCHECKED_CAST")
        val typed = (app.configSpec as AppConfigSpec<C>).decode(config).getOrElse { app.configSpec.default }
        val instance = app.launch(runtime, typed)

        // `runTest` for `onLaunch` alone, so a suspending start is driven on virtual time while the
        // composition below stays on the clock the UI assertions need.
        runTest { instance.onLaunch() }
        restore?.let { instance.restore(it) }

        setContent {
            CompositionLocalProvider(LocalAppRuntime provides runtime) {
                androidx.compose.foundation.layout.Box(Modifier.testTag("${app.id.value}:root")) {
                    instance.Content()
                }
            }
        }
        waitForIdle()

        AppTestScope(app, instance, runtime, this).block()
    }
}
