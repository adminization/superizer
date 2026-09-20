package cx.m42.superizer.fixture

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import cx.m42.superizer.host.storage.PrefsStorage
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The fixture, as a desktop window.
 *
 * `--link=` is the desktop stand-in for a notification tap (06): it is how the routing path is
 * driven by hand on a machine with no push and no intents.
 */
fun main(args: Array<String>) {
    // Its own directory, so running the fixture never touches whatever settings a real host wrote.
    PrefsStorage.useDirectory(File(System.getProperty("java.io.tmpdir"), "superizer-fixture"))

    application {
        val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
        val superizer = remember { buildFixture(scope) }

        remember {
            args.firstOrNull { it.startsWith("--link=") }
                ?.removePrefix("--link=")
                ?.let { superizer.route.deliver(it) }
        }

        Window(
            onCloseRequest = ::exitApplication,
            // Phone-shaped on purpose: the layout has no desktop breakpoint, and this is the
            // aspect ratio it is designed against.
            state = rememberWindowState(width = 420.dp, height = 820.dp),
            title = "Superizer fixture",
        ) {
            FixtureApp(superizer)
        }
    }
}
