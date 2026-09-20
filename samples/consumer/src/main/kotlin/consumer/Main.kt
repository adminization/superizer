package consumer

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import cx.m42.apps.testapp.TestApp
import cx.m42.superizer.Superizer
import cx.m42.superizer.SuperizerContract
import cx.m42.superizer.host.build
import cx.m42.superizer.host.platform.currentPlatform
import cx.m42.superizer.runtime.HostInfo
import cx.m42.superizer.ui.shell.SuperizerShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The whole of a Super App, written against the published package.
 *
 * Six lines of host and one `register`. That is the claim the library makes, and this file is where
 * it is either true or not.
 */
fun buildConsumerHost(scope: CoroutineScope): Superizer = Superizer.build(scope) {
    host(
        HostInfo(
            name = "Consumer",
            version = "1.0",
            build = "sample",
            platform = currentPlatform(),
            contractVersion = SuperizerContract.VERSION,
            debug = true,
        ),
    )
    register(TestApp())
}

fun main() = application {
    val superizer = buildConsumerHost(CoroutineScope(SupervisorJob() + Dispatchers.Main))
    Window(onCloseRequest = ::exitApplication, title = "Superizer consumer") {
        SuperizerShell(superizer)
    }
}
