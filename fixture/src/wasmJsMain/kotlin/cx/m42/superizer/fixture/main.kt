package cx.m42.superizer.fixture

import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(viewportContainerId = "composeTarget") {
        val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
        val superizer = remember { buildFixture(scope) }

        remember {
            // `?activate=` and `?link=` are the browser's version of a QR code and a notification
            // tap (06). One parser serves all three, which is what the smoke test checks.
            val query = window.location.search
            queryParam(query, "link")?.let { superizer.route.deliver(it) }
            queryParam(query, "activate")?.let { superizer.route.deliver(it) }
            if (queryParam(query, "test") != null) installTestBridge(superizer)
        }

        FixtureApp(superizer)
    }
}

/** Percent-decoded, because a deep link in a query string arrives escaped. */
internal fun queryParam(search: String, name: String): String? = search
    .removePrefix("?")
    .split('&')
    .firstOrNull { it.startsWith("$name=") }
    ?.substringAfter('=')
    ?.let { runCatching { decodeUriComponent(it) }.getOrDefault(it) }

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun decodeUriComponent(value: String): String = js("decodeURIComponent(value)")
